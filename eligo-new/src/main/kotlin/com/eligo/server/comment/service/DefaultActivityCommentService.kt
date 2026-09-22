package com.eligo.server.comment.service

import com.eligo.server.account.service.AccountStateLockService
import com.eligo.server.activity.service.ActivityEngagementAccessService
import com.eligo.server.activity.service.ActivityEngagementSnapshot
import com.eligo.server.activity.vo.PublicImageView
import com.eligo.server.comment.dto.ActivityCommentCreateRequest
import com.eligo.server.comment.entity.ActivityCommentEntity
import com.eligo.server.comment.error.ActivityCommentErrorCode
import com.eligo.server.comment.mapper.ActivityCommentMapper
import com.eligo.server.comment.mapper.ActivityCommentRow
import com.eligo.server.comment.vo.ActivityCommentAuthorView
import com.eligo.server.comment.vo.ActivityCommentView
import com.eligo.server.common.api.CursorPage
import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.profile.service.ProfileCompletionReader
import com.eligo.server.security.UserPrincipal
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.HexFormat
import org.springframework.context.annotation.Profile
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test")
class DefaultActivityCommentService(
    private val comments: ActivityCommentMapper,
    private val activities: ActivityEngagementAccessService,
    private val completion: ProfileCompletionReader,
    private val accountStates: AccountStateLockService,
    private val clock: Clock = Clock.systemUTC()
) : ActivityCommentService {

    @Transactional
    override fun create(
        principal: UserPrincipal,
        activityId: Long,
        request: ActivityCommentCreateRequest,
        idempotencyKey: String
    ): ActivityCommentCreateOutcome {
        requirePrincipalAndId(principal, activityId)
        validateIdempotencyKey(idempotencyKey)
        val normalized = normalize(request)
        val requestFingerprint = fingerprint(activityId, normalized.parentCommentId, normalized.content)
        val existing = comments.findByIdempotency(principal.userId, idempotencyKey).orElse(null)
        if (existing != null) {
            return replay(existing, requestFingerprint)
        }

        accountStates.lockActive(principal.userId)
        requireCompletedProfile(principal.userId)
        val activity = requireCommentableActivity(activityId)
        validateParent(principal.userId, activity, normalized.parentCommentId)

        val created = ActivityCommentEntity()
        created.activityId = activityId
        created.authorUserId = principal.userId
        created.parentCommentId = normalized.parentCommentId
        created.status = ActivityCommentEntity.STATUS_ACTIVE
        created.content = normalized.content
        created.idempotencyKey = idempotencyKey
        created.requestFingerprint = requestFingerprint
        created.createdAt = now()
        return try {
            if (comments.insert(created) != 1) {
                throw BusinessException(CommonErrorCode.CONFLICT)
            }
            ActivityCommentCreateOutcome(view(requireRow(created.id!!)), false)
        } catch (exception: DuplicateKeyException) {
            val winner = comments.lockByIdempotency(principal.userId, idempotencyKey)
                .orElseThrow { exception }
            replay(winner, requestFingerprint, true)
        }
    }

    @Transactional(readOnly = true)
    override fun list(activityId: Long, cursor: String?, limit: Int): CursorPage<ActivityCommentView> {
        if (activityId <= 0 || limit < 1 || limit > 50) {
            throw validation()
        }
        activities.findPublicById(activityId).orElseThrow { notFound() }
        val decoded = decode(cursor)
        val rows = comments.findPage(activityId, decoded.createdAt, decoded.commentId, limit + 1)
        val hasMore = rows.size > limit
        val pageRows = rows.subList(0, minOf(rows.size, limit))
        val items = pageRows.map { view(it) }
        val nextCursor = if (hasMore && pageRows.isNotEmpty()) encode(pageRows[pageRows.size - 1]) else null
        return CursorPage(items, nextCursor, hasMore)
    }

    @Transactional
    override fun delete(principal: UserPrincipal, activityId: Long, commentId: Long): ActivityCommentView {
        requirePrincipalAndId(principal, activityId)
        if (commentId <= 0) {
            throw validation()
        }
        val comment = comments.findById(commentId)
            .filter { it.activityId == activityId }
            .orElseThrow { notFound() }
        val author = comment.authorUserId == principal.userId
        if (!author) {
            val activity = activities.findById(activityId).orElseThrow { notFound() }
            if (!activities.isOwner(principal.userId, activity)) {
                throw notFound()
            }
        }
        if (comment.status == ActivityCommentEntity.STATUS_ACTIVE) {
            comments.softDelete(commentId, now())
        }
        return view(requireRow(commentId))
    }

    fun fingerprint(activityId: Long, parentCommentId: Long?, content: String): String {
        try {
            val bytes = ByteArrayOutputStream()
            DataOutputStream(bytes).use { output ->
                output.writeLong(activityId)
                output.writeBoolean(parentCommentId != null)
                if (parentCommentId != null) {
                    output.writeLong(parentCommentId)
                }
                val contentBytes = content.toByteArray(StandardCharsets.UTF_8)
                output.writeInt(contentBytes.size)
                output.write(contentBytes)
            }
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())
            )
        } catch (exception: Exception) {
            throw IllegalStateException("评论请求指纹计算失败", exception)
        }
    }

    private fun replay(existing: ActivityCommentEntity, requestFingerprint: String): ActivityCommentCreateOutcome =
        replay(existing, requestFingerprint, false)

    private fun replay(
        existing: ActivityCommentEntity,
        requestFingerprint: String,
        currentRead: Boolean
    ): ActivityCommentCreateOutcome {
        if (existing.requestFingerprint != requestFingerprint) {
            throw BusinessException(ActivityCommentErrorCode.IDEMPOTENCY_KEY_CONFLICT)
        }
        val row = if (currentRead) requireCurrentRow(existing.id!!) else requireRow(existing.id!!)
        return ActivityCommentCreateOutcome(view(row), true)
    }

    private fun requireCommentableActivity(activityId: Long): ActivityEngagementSnapshot {
        val activity = activities.findPublicById(activityId).orElseThrow { notFound() }
        if (activity.status != PUBLISHED || activity.endsAt == null || !now().isBefore(activity.endsAt)) {
            throw BusinessException(CommonErrorCode.CONFLICT)
        }
        return activity
    }

    private fun validateParent(userId: Long, activity: ActivityEngagementSnapshot, parentCommentId: Long?) {
        if (parentCommentId == null) {
            return
        }
        val parent = comments.findById(parentCommentId)
            .filter { it.activityId == activity.activityId }
            .orElseThrow { notFound() }
        if (parent.status != ActivityCommentEntity.STATUS_ACTIVE || parent.parentCommentId != null) {
            throw validation()
        }
        if (!activities.isOwner(userId, activity)) {
            throw BusinessException(CommonErrorCode.ACCESS_DENIED)
        }
    }

    private fun normalize(request: ActivityCommentCreateRequest?): NormalizedRequest {
        if (request == null) {
            throw validation()
        }
        val content = request.content?.trim()
        if (content == null || content.isEmpty() ||
            content.codePointCount(0, content.length) > MAX_CONTENT_LENGTH ||
            (request.parentCommentId != null && request.parentCommentId <= 0)
        ) {
            throw validation()
        }
        return NormalizedRequest(content, request.parentCommentId)
    }

    private fun requireRow(commentId: Long): ActivityCommentRow =
        comments.findRowById(commentId) ?: throw IllegalStateException("评论写入结果不存在")

    private fun requireCurrentRow(commentId: Long): ActivityCommentRow =
        comments.findRowByIdForUpdate(commentId) ?: throw IllegalStateException("评论并发写入结果不存在")

    private fun view(row: ActivityCommentRow): ActivityCommentView {
        val deleted = row.status == ActivityCommentEntity.STATUS_DELETED
        return ActivityCommentView(
            row.commentId.toString(),
            row.activityId.toString(),
            row.parentCommentId?.toString(),
            deleted,
            if (deleted) null else row.content,
            if (deleted) null else ActivityCommentAuthorView(
                row.authorUserId.toString(),
                row.authorNickname!!,
                image(row.authorAvatarFileId)
            ),
            instant(row.createdAt)!!,
            instant(row.deletedAt)
        )
    }

    private fun image(fileId: Long?): PublicImageView? =
        if (fileId == null) null else PublicImageView(fileId.toString(), "/api/v1/files/$fileId/content")

    private fun validateIdempotencyKey(value: String?) {
        if (value == null || value.length < 8 || value.length > 128 ||
            !value.matches(Regex("[A-Za-z0-9._:-]+"))
        ) {
            throw validation()
        }
    }

    private fun requireCompletedProfile(userId: Long) {
        if (!completion.isCompleted(userId)) {
            throw BusinessException(AccountUserFileErrorCode.PROFILE_INCOMPLETE)
        }
    }

    private fun now(): LocalDateTime =
        LocalDateTime.ofInstant(clock.instant().truncatedTo(ChronoUnit.MILLIS), ZoneOffset.UTC)

    private fun instant(value: LocalDateTime?): Instant? = value?.toInstant(ZoneOffset.UTC)

    private fun decode(cursor: String?): CommentCursor {
        if (cursor == null) {
            return CommentCursor(null, null)
        }
        if (cursor.isBlank() || cursor.length > MAX_CURSOR_LENGTH) {
            throw validation()
        }
        try {
            val decoded = String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
            val parts = decoded.split("|")
            if (parts.size != 3 || parts[0] != "c1") {
                throw validation()
            }
            val createdAt = LocalDateTime.parse(parts[1])
            val commentId = parts[2].toLong()
            if (commentId <= 0) {
                throw validation()
            }
            return CommentCursor(createdAt, commentId)
        } catch (exception: IllegalArgumentException) {
            throw validation()
        }
    }

    private fun encode(row: ActivityCommentRow): String {
        val raw = "c1|${row.createdAt}|${row.commentId}"
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
    }

    private fun notFound(): BusinessException = BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND)

    private data class NormalizedRequest(val content: String, val parentCommentId: Long?)

    private data class CommentCursor(val createdAt: LocalDateTime?, val commentId: Long?)

    companion object {
        private const val PUBLISHED = 2
        private const val MAX_CONTENT_LENGTH = 500
        private const val MAX_CURSOR_LENGTH = 256

        private fun validation(): BusinessException = BusinessException(CommonErrorCode.VALIDATION_FAILED)

        private fun requirePrincipalAndId(principal: UserPrincipal?, activityId: Long) {
            if (principal == null || principal.userId <= 0 || activityId <= 0) {
                throw validation()
            }
        }
    }
}
