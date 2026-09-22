package com.eligo.server.post.service

import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.file.entity.FileObjectEntity
import com.eligo.server.file.mapper.FileObjectMapper
import com.eligo.server.organization.mapper.OrganizationMapper
import com.eligo.server.post.dto.PostDraftCreateRequest
import com.eligo.server.post.dto.PostDraftReplaceRequest
import com.eligo.server.post.entity.PostEntity
import com.eligo.server.post.entity.PostMediaEntity
import com.eligo.server.post.entity.PostStatusEventEntity
import com.eligo.server.post.error.PostErrorCode
import com.eligo.server.post.mapper.PostMapper
import com.eligo.server.post.mapper.PostMediaMapper
import com.eligo.server.post.mapper.PostStatusEventMapper
import com.eligo.server.post.vo.ManagedPostDetailView
import com.eligo.server.profile.service.ProfileCompletionReader
import com.eligo.server.recommendation.service.RecommendationIndexTaskWriter
import com.eligo.server.security.UserPrincipal
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.HexFormat
import java.util.LinkedHashSet
import java.util.Locale
import org.springframework.context.annotation.Profile
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test")
class DefaultPostCommandService(
    private val posts: PostMapper,
    private val media: PostMediaMapper,
    private val events: PostStatusEventMapper,
    private val files: FileObjectMapper,
    private val organizations: OrganizationMapper,
    private val completion: ProfileCompletionReader,
    private val reads: PostReadService,
    private val recommendationTasks: RecommendationIndexTaskWriter = RecommendationIndexTaskWriter.noop(),
    private val clock: Clock = Clock.systemUTC()
) : PostCommandService {

    override
    @Transactional
    fun createPersonal(
        principal: UserPrincipal?,
        request: PostDraftCreateRequest?,
        idempotencyKey: String
    ): PostCreateOutcome {
        requirePrincipal(principal)
        requireCompletedProfile(principal!!.userId)
        return create(principal, "USER", principal.userId, request, idempotencyKey)
    }

    override
    @Transactional
    fun createOrganization(
        principal: UserPrincipal?,
        organizationId: Long,
        request: PostDraftCreateRequest?,
        idempotencyKey: String
    ): PostCreateOutcome {
        requirePrincipal(principal)
        if (organizationId <= 0) {
            throw validation()
        }
        requireCompletedProfile(principal!!.userId)
        requireOrganizationWrite(principal.userId, organizationId)
        return create(principal, "ORGANIZATION", organizationId, request, idempotencyKey)
    }

    override
    @Transactional
    fun replacePersonal(
        principal: UserPrincipal?,
        postId: Long,
        request: PostDraftReplaceRequest?
    ): ManagedPostDetailView {
        requirePrincipalAndId(principal, postId)
        val post = posts.lockById(postId).orElseThrow(::notFound)
        if (post.authorUserId != principal!!.userId || post.authorOrganizationId != null) {
            throw notFound()
        }
        requireCompletedProfile(principal.userId)
        return replace(principal, post, request)
    }

    override
    @Transactional
    fun replaceOrganization(
        principal: UserPrincipal?,
        organizationId: Long,
        postId: Long,
        request: PostDraftReplaceRequest?
    ): ManagedPostDetailView {
        requirePrincipalAndId(principal, postId)
        if (organizationId <= 0) {
            throw validation()
        }
        val post = posts.lockById(postId).orElseThrow(::notFound)
        if (post.authorOrganizationId != organizationId
            || post.authorUserId != null
            || !organizations.isSoleOwner(principal!!.userId, organizationId)
        ) {
            throw notFound()
        }
        requireCompletedProfile(principal.userId)
        requireOrganizationWrite(principal.userId, organizationId)
        return replace(principal, post, request)
    }

    override
    @Transactional
    fun publish(principal: UserPrincipal?, postId: Long): ManagedPostDetailView {
        requirePrincipalAndId(principal, postId)
        val post = posts.lockById(postId).orElseThrow(::notFound)
        requireManagedRead(principal!!.userId, post)
        if (post.status == PostEntity.STATUS_PUBLISHED) {
            return reads.getManagedPost(principal, postId)
        }
        requireWrite(principal.userId, post)
        if (post.status != PostEntity.STATUS_DRAFT) {
            throw BusinessException(PostErrorCode.STATUS_CONFLICT)
        }

        val mediaFileIds = media.findByPostId(postId).map { it.fileId!! }
        validatePublishable(post, mediaFileIds)
        validateActivity(post.activityId)
        validateFiles(mediaFileIds, principal.userId, postId)
        val now = now()
        if (posts.publishById(postId, now) != 1) {
            throw BusinessException(PostErrorCode.STATUS_CONFLICT)
        }
        appendUserEvent(postId, PostEntity.STATUS_DRAFT, PostEntity.STATUS_PUBLISHED, principal.userId, now)
        recommendationTasks.enqueueUpsert(postId)
        return reads.getManagedPost(principal, postId)
    }

    override
    @Transactional
    fun delete(principal: UserPrincipal?, postId: Long) {
        requirePrincipalAndId(principal, postId)
        val post = posts.lockById(postId).orElseThrow(::notFound)
        requireManagedRead(principal!!.userId, post)
        if (post.status == PostEntity.STATUS_DELETED) {
            throw notFound()
        }
        if (post.authorOrganizationId != null) {
            requireOrganizationDeleteWrite(principal.userId, post.authorOrganizationId!!)
        }
        if (post.status != PostEntity.STATUS_DRAFT
            && post.status != PostEntity.STATUS_PUBLISHED
            && post.status != PostEntity.STATUS_HIDDEN
        ) {
            throw BusinessException(PostErrorCode.STATUS_CONFLICT)
        }
        val fromStatus = post.status!!
        val now = now()
        if (posts.softDeleteById(postId, fromStatus, now) != 1) {
            throw BusinessException(PostErrorCode.STATUS_CONFLICT)
        }
        appendUserEvent(postId, fromStatus, PostEntity.STATUS_DELETED, principal.userId, now)
        recommendationTasks.enqueueDelete(postId)
    }

    private fun replace(
        principal: UserPrincipal,
        post: PostEntity,
        request: PostDraftReplaceRequest?
    ): ManagedPostDetailView {
        if (request == null || request.version == null || request.version < 0) {
            throw validation()
        }
        if (post.version != request.version) {
            throw BusinessException(PostErrorCode.VERSION_CONFLICT)
        }
        if (post.status != PostEntity.STATUS_DRAFT) {
            throw BusinessException(PostErrorCode.STATUS_CONFLICT)
        }
        val normalized = normalizeReplace(request)
        validateActivity(normalized.activityId)
        val referencedFiles = validateFiles(normalized.mediaFileIds, principal.userId, post.id)
        post.visibility = normalized.visibility
        post.title = normalized.title
        post.content = normalized.content
        post.activityId = normalized.activityId
        post.operatorUserId = principal.userId
        val now = now()
        if (posts.replaceDraft(post, request.version, now) != 1) {
            throw BusinessException(PostErrorCode.VERSION_CONFLICT)
        }
        media.deleteByPostId(post.id!!)
        bindMedia(post.id!!, normalized.mediaFileIds, now)
        activateTemporaryFiles(referencedFiles, now)
        return reads.getManagedPost(principal, post.id!!)
    }

    private fun create(
        principal: UserPrincipal,
        authorType: String,
        authorId: Long,
        request: PostDraftCreateRequest?,
        idempotencyKey: String
    ): PostCreateOutcome {
        validateIdempotencyKey(idempotencyKey)
        val scope = "$authorType:$authorId:${principal.userId}"
        val now = now()
        var existing = posts.findByCreateIdempotency(scope, idempotencyKey)
        if (existing.isPresent) {
            if (!existing.get().createIdempotencyExpiresAt!!.isAfter(now)) {
                val cleared = posts.clearExpiredCreateIdempotency(
                    existing.get().id!!, scope, idempotencyKey, now
                )
                existing = if (cleared == 1) {
                    java.util.Optional.empty()
                } else {
                    posts.lockByCreateIdempotency(scope, idempotencyKey)
                }
            } else {
                existing = posts.lockByCreateIdempotency(scope, idempotencyKey)
            }
        }
        val normalized = try {
            normalizeCreate(request)
        } catch (exception: BusinessException) {
            if (existing.isPresent) {
                throw BusinessException(PostErrorCode.IDEMPOTENCY_KEY_CONFLICT)
            }
            throw exception
        }
        val requestFingerprint = fingerprint(
            authorType,
            authorId,
            normalized.title,
            normalized.content,
            normalized.visibility,
            normalized.activityId,
            normalized.mediaFileIds
        )
        if (existing.isPresent) {
            return replayCurrent(principal, existing.get(), requestFingerprint)
        }

        validateActivity(normalized.activityId)
        val referencedFiles = validateFiles(normalized.mediaFileIds, principal.userId, null)
        val post = PostEntity()
        post.authorUserId = if ("USER" == authorType) authorId else null
        post.authorOrganizationId = if ("ORGANIZATION" == authorType) authorId else null
        post.operatorUserId = principal.userId
        post.status = PostEntity.STATUS_DRAFT
        post.visibility = normalized.visibility
        post.title = normalized.title
        post.content = normalized.content
        post.activityId = normalized.activityId
        post.createIdempotencyScope = scope
        post.createIdempotencyKey = idempotencyKey
        post.createRequestFingerprint = requestFingerprint
        post.createIdempotencyExpiresAt = now.plus(IDEMPOTENCY_TTL)
        post.version = 0
        post.createdAt = now
        post.updatedAt = now
        try {
            posts.insert(post)
        } catch (exception: DuplicateKeyException) {
            val winner = posts.lockByCreateIdempotency(scope, idempotencyKey)
                .orElseThrow { exception }
            return replayCurrent(principal, winner, requestFingerprint)
        }
        bindMedia(post.id!!, normalized.mediaFileIds, now)
        activateTemporaryFiles(referencedFiles, now)
        return PostCreateOutcome(
            reads.getManagedPost(principal, post.id!!),
            false
        )
    }

    private fun replayCurrent(
        principal: UserPrincipal,
        post: PostEntity,
        requestFingerprint: String
    ): PostCreateOutcome {
        if (requestFingerprint != post.createRequestFingerprint) {
            throw BusinessException(PostErrorCode.IDEMPOTENCY_KEY_CONFLICT)
        }
        if (post.status == PostEntity.STATUS_DELETED) {
            throw BusinessException(PostErrorCode.IDEMPOTENCY_RESULT_DELETED)
        }
        return PostCreateOutcome(
            reads.getManagedPostForReplay(principal, post.id!!),
            true
        )
    }

    private fun normalizeCreate(request: PostDraftCreateRequest?): NormalizedPost {
        if (request == null) {
            throw validation()
        }
        return normalize(
            request.title,
            request.content,
            request.visibility,
            request.activityId,
            request.mediaFileIds,
            false
        )
    }

    private fun normalizeReplace(request: PostDraftReplaceRequest): NormalizedPost =
        normalize(
            request.title,
            request.content,
            request.visibility,
            request.activityId,
            request.mediaFileIds,
            true
        )

    private fun normalize(
        title: String?,
        content: String?,
        visibility: String?,
        activityId: Long?,
        mediaFileIds: List<Long?>?,
        visibilityRequired: Boolean
    ): NormalizedPost {
        val normalizedTitle = blankToNull(title?.trim())
        val normalizedContent = blankToNull(content)
        if (normalizedTitle != null
            && normalizedTitle.codePointCount(0, normalizedTitle.length) > 20
        ) {
            throw validation()
        }
        if (normalizedContent != null
            && normalizedContent.codePointCount(0, normalizedContent.length) > 5000
        ) {
            throw validation()
        }
        val normalizedVisibility = visibility(visibility, visibilityRequired)
        if (activityId != null && activityId <= 0) {
            throw validation()
        }
        val normalizedMedia = mediaFileIds?.toMutableList() ?: mutableListOf()
        if (normalizedMedia.size > 5
            || normalizedMedia.any { it == null || it <= 0 }
            || LinkedHashSet(normalizedMedia).size != normalizedMedia.size
        ) {
            throw validation()
        }
        if (normalizedTitle == null
            && normalizedContent == null
            && normalizedMedia.isEmpty()
            && activityId == null
        ) {
            throw validation()
        }
        return NormalizedPost(
            normalizedTitle,
            normalizedContent,
            normalizedVisibility,
            activityId,
            normalizedMedia.filterNotNull()
        )
    }

    private fun validatePublishable(post: PostEntity, mediaFileIds: List<Long>) {
        if (post.title == null
            || post.title!!.isBlank()
            || (blankToNull(post.content) == null && mediaFileIds.isEmpty())
        ) {
            throw validation()
        }
    }

    private fun validateActivity(activityId: Long?) {
        if (activityId != null && !posts.existsPublicActivityReference(activityId)) {
            throw BusinessException(PostErrorCode.ACTIVITY_REFERENCE_UNAVAILABLE)
        }
    }

    private fun validateFiles(
        fileIds: List<Long>,
        userId: Long,
        currentPostId: Long?
    ): List<FileObjectEntity> {
        if (fileIds.isEmpty()) {
            return emptyList()
        }
        val lockOrder = fileIds.sorted()
        val result = ArrayList<FileObjectEntity>(lockOrder.size)
        var totalSize = 0L
        for (fileId in lockOrder) {
            val file = files.lockById(fileId).orElseThrow(::fileConflict)
            val boundPostId = media.findPostIdByFileId(fileId)
            val boundToCurrent = currentPostId != null
                && boundPostId.filter { currentPostId == it }.isPresent
            if (file.scanStatus == FileObjectEntity.SCAN_FAILED) {
                throw BusinessException(PostErrorCode.CONTENT_REJECTED)
            }
            val temporaryOwned = file.lifecycleStatus == FileObjectEntity.LIFECYCLE_TEMPORARY
                && file.uploaderType == FileObjectEntity.UPLOADER_USER
                && file.uploaderId == userId
                && boundPostId.isEmpty
                && (file.expiresAt == null || file.expiresAt!!.isAfter(now()))
            val activeCurrent = file.lifecycleStatus == FileObjectEntity.LIFECYCLE_ACTIVE
            && boundToCurrent
        val sizeBytes = file.sizeBytes
        if (FileObjectEntity.PURPOSE_POST != file.purpose
            || file.accessLevel != FileObjectEntity.ACCESS_PUBLIC
            || file.scanStatus != FileObjectEntity.SCAN_PASSED
            || ("image/jpeg" != file.contentType && "image/png" != file.contentType)
            || (!temporaryOwned && !activeCurrent)
            || sizeBytes == null
            || sizeBytes <= 0
            || sizeBytes > MAX_FILE_SIZE
        ) {
            throw fileConflict()
        }
        try {
            totalSize = Math.addExact(totalSize, sizeBytes)
        } catch (exception: ArithmeticException) {
                throw fileConflict()
            }
            if (totalSize > MAX_TOTAL_FILE_SIZE) {
                throw fileConflict()
            }
            result.add(file)
        }
        return result
    }

    private fun bindMedia(postId: Long, fileIds: List<Long>, now: LocalDateTime) {
        var order = 0
        for (fileId in fileIds) {
            val item = PostMediaEntity()
            item.postId = postId
            item.fileId = fileId
            item.sortOrder = order++
            item.createdAt = now
            media.insert(item)
        }
    }

    private fun activateTemporaryFiles(
        referencedFiles: List<FileObjectEntity>,
        now: LocalDateTime
    ) {
        for (file in referencedFiles) {
            if (file.lifecycleStatus == FileObjectEntity.LIFECYCLE_TEMPORARY
                && files.activate(file.id!!, now, file.version ?: 0) != 1
            ) {
                throw fileConflict()
            }
        }
    }

    private fun appendUserEvent(
        postId: Long,
        fromStatus: Int,
        toStatus: Int,
        userId: Long,
        now: LocalDateTime
    ) {
        val event = PostStatusEventEntity()
        event.postId = postId
        event.fromStatus = fromStatus
        event.toStatus = toStatus
        event.actorType = PostStatusEventEntity.ACTOR_USER
        event.actorUserId = userId
        event.createdAt = now
        events.insert(event)
    }

    private fun requireWrite(userId: Long, post: PostEntity) {
        if (post.authorUserId != null) {
            requireCompletedProfile(userId)
            return
        }
        requireCompletedProfile(userId)
        requireOrganizationWrite(userId, post.authorOrganizationId!!)
    }

    private fun requireManagedRead(userId: Long, post: PostEntity) {
        if (post.authorUserId != null) {
            if (post.authorUserId != userId) {
                throw notFound()
            }
            return
        }
        if (post.authorOrganizationId == null
            || !organizations.isSoleOwner(userId, post.authorOrganizationId!!)
        ) {
            throw notFound()
        }
    }

    private fun requireOrganizationWrite(userId: Long, organizationId: Long) {
        if (!organizations.isActiveSoleOwner(userId, organizationId)) {
            throw notFound()
        }
    }

    private fun requireOrganizationDeleteWrite(userId: Long, organizationId: Long) {
        if (!completion.isCompleted(userId)
            || !organizations.isActiveSoleOwner(userId, organizationId)
        ) {
            throw notFound()
        }
    }

    private fun requireCompletedProfile(userId: Long) {
        if (!completion.isCompleted(userId)) {
            throw BusinessException(AccountUserFileErrorCode.PROFILE_INCOMPLETE)
        }
    }

    private fun requirePrincipal(principal: UserPrincipal?) {
        if (principal == null || principal.userId <= 0) {
            throw BusinessException(CommonErrorCode.AUTHENTICATION_REQUIRED)
        }
    }

    private fun requirePrincipalAndId(principal: UserPrincipal?, postId: Long) {
        requirePrincipal(principal)
        if (postId <= 0) {
            throw validation()
        }
    }

    private fun visibility(value: String?, required: Boolean): Int {
        if (value == null) {
            if (required) {
                throw validation()
            }
            return PostEntity.VISIBILITY_PUBLIC
        }
        return when (value.trim().uppercase(Locale.ROOT)) {
            "PUBLIC" -> PostEntity.VISIBILITY_PUBLIC
            "FOLLOWERS_ONLY" -> PostEntity.VISIBILITY_FOLLOWERS_ONLY
            "PRIVATE" -> PostEntity.VISIBILITY_PRIVATE
            else -> throw validation()
        }
    }

    private fun blankToNull(value: String?): String? =
        if (value == null || value.isBlank()) null else value

    private fun validateIdempotencyKey(value: String?) {
        if (value == null
            || value.length < 8
            || value.length > 128
            || !value.matches(Regex("[A-Za-z0-9._:-]+"))
        ) {
            throw validation()
        }
    }

    protected open fun fingerprint(
        authorType: String,
        authorId: Long,
        title: String?,
        content: String?,
        visibility: Int,
        activityId: Long?,
        mediaFileIds: List<Long>
    ): String {
        try {
            val output = ByteArrayOutputStream()
            DataOutputStream(output).use { data ->
                writeString(data, authorType)
                data.writeLong(authorId)
                writeNullableString(data, title)
                writeNullableString(data, content)
                data.writeInt(visibility)
                data.writeBoolean(activityId != null)
                if (activityId != null) {
                    data.writeLong(activityId)
                }
                data.writeInt(mediaFileIds.size)
                for (fileId in mediaFileIds) {
                    data.writeLong(fileId)
                }
            }
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(output.toByteArray())
            )
        } catch (exception: IOException) {
            throw IllegalStateException("无法计算动态创建请求摘要", exception)
        } catch (exception: NoSuchAlgorithmException) {
            throw IllegalStateException("无法计算动态创建请求摘要", exception)
        }
    }

    private fun writeNullableString(output: DataOutputStream, value: String?) {
        output.writeBoolean(value != null)
        if (value != null) {
            writeString(output, value)
        }
    }

    private fun writeString(output: DataOutputStream, value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        output.writeInt(bytes.size)
        output.write(bytes)
    }

    private fun now(): LocalDateTime =
        LocalDateTime.ofInstant(
            clock.instant().truncatedTo(ChronoUnit.MILLIS),
            ZoneOffset.UTC
        )

    private fun validation(): BusinessException =
        BusinessException(CommonErrorCode.VALIDATION_FAILED)

    private fun notFound(): BusinessException =
        BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND)

    private fun fileConflict(): BusinessException =
        BusinessException(AccountUserFileErrorCode.FILE_STATE_CONFLICT)

    private data class NormalizedPost(
        val title: String?,
        val content: String?,
        val visibility: Int,
        val activityId: Long?,
        val mediaFileIds: List<Long>
    )

    companion object {
        private val IDEMPOTENCY_TTL = Duration.ofHours(24)
        private const val MAX_FILE_SIZE = 10L * 1024 * 1024
        private const val MAX_TOTAL_FILE_SIZE = 50L * 1024 * 1024
    }
}
