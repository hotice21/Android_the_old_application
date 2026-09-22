package com.eligo.server.post.service

import com.eligo.server.activity.vo.PublicImageView
import com.eligo.server.common.api.CursorPage
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.follow.mapper.OrganizationFollowMapper
import com.eligo.server.follow.mapper.UserFollowMapper
import com.eligo.server.organization.mapper.OrganizationMapper
import com.eligo.server.post.entity.PostEntity
import com.eligo.server.post.mapper.PostActivityRow
import com.eligo.server.post.mapper.PostDetailRow
import com.eligo.server.post.mapper.PostQueryMapper
import com.eligo.server.post.vo.ManagedPostDetailView
import com.eligo.server.post.vo.ManagedPostSummaryView
import com.eligo.server.post.vo.PostActivityCardView
import com.eligo.server.post.vo.PostAuthorSummaryView
import com.eligo.server.post.vo.PublicPostView
import com.eligo.server.profile.mapper.UserProfileMapper
import com.eligo.server.security.UserPrincipal
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.HexFormat
import java.util.Locale
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test")
class DefaultPostReadService(
    private val queries: PostQueryMapper,
    private val organizations: OrganizationMapper,
    private val profiles: UserProfileMapper,
    private val userFollows: UserFollowMapper,
    private val organizationFollows: OrganizationFollowMapper
) : PostReadService {

    override
    @Transactional(readOnly = true)
    fun listManagedPosts(
        principal: UserPrincipal?,
        cursor: String?,
        limit: Int,
        status: String?,
        authorType: String?
    ): CursorPage<ManagedPostSummaryView> {
        requirePrincipal(principal)
        requireLimit(limit)
        val normalizedStatus = managedStatus(status)
        val normalizedAuthorType = authorType(authorType)
        val filterHash = filterHash(
            normalizedStatus?.toString() ?: "ALL",
            normalizedAuthorType ?: "ALL"
        )
        val pageCursor = decode(cursor, "managed", filterHash)
        val rows = queries.findManagedPage(
            principal!!.userId,
            normalizedStatus,
            normalizedAuthorType,
            pageCursor.time,
            pageCursor.postId,
            limit + 1
        )
        return managedPage(rows, limit, "managed", filterHash)
    }

    override
    @Transactional(readOnly = true)
    fun getManagedPost(principal: UserPrincipal?, postId: Long): ManagedPostDetailView {
        requirePrincipalAndId(principal, postId)
        val row = queries.findById(postId).orElseThrow(::notFound)
        if (row.status == PostEntity.STATUS_DELETED || !canManage(principal!!, row)) {
            throw notFound()
        }
        return managedDetail(row)
    }

    override
    @Transactional
    fun getManagedPostForReplay(
        principal: UserPrincipal?,
        postId: Long
    ): ManagedPostDetailView {
        requirePrincipalAndId(principal, postId)
        val row = queries.findByIdForUpdate(postId).orElseThrow(::notFound)
        if (row.status == PostEntity.STATUS_DELETED || !canManage(principal!!, row)) {
            throw notFound()
        }
        return managedDetail(row, true)
    }

    override
    @Transactional(readOnly = true)
    fun getPost(principal: UserPrincipal?, postId: Long): PublicPostView {
        if (postId <= 0) {
            throw validation()
        }
        val row = queries.findPublicCandidateById(postId).orElseThrow(::notFound)
        if (!canRead(principal, row)) {
            throw notFound()
        }
        return publicView(row)
    }

    override
    @Transactional(readOnly = true)
    fun listPublicPosts(cursor: String?, limit: Int): CursorPage<PublicPostView> {
        requireLimit(limit)
        val filterHash = filterHash("PUBLIC")
        val pageCursor = decode(cursor, "public", filterHash)
        val rows = queries.findPublicPage(
            pageCursor.time,
            pageCursor.postId,
            limit + 1
        )
        return publicPage(rows, limit, "public", filterHash)
    }

    override
    @Transactional(readOnly = true)
    fun listUserPosts(
        principal: UserPrincipal?,
        userId: Long,
        cursor: String?,
        limit: Int
    ): CursorPage<PublicPostView> {
        if (userId <= 0) {
            throw validation()
        }
        requireLimit(limit)
        if (!profiles.existsActiveCompletedUser(userId)) {
            throw notFound()
        }
        val maxVisibility = userVisibility(principal, userId)
        val listKind = "user-$userId"
        val filterHash = filterHash("USER", userId.toString(), maxVisibility.toString())
        val pageCursor = decode(cursor, listKind, filterHash)
        val rows = queries.findAuthorPage(
            "USER",
            userId,
            maxVisibility,
            pageCursor.time,
            pageCursor.postId,
            limit + 1
        )
        return publicPage(rows, limit, listKind, filterHash)
    }

    override
    @Transactional(readOnly = true)
    fun listOrganizationPosts(
        principal: UserPrincipal?,
        organizationId: Long,
        cursor: String?,
        limit: Int
    ): CursorPage<PublicPostView> {
        if (organizationId <= 0) {
            throw validation()
        }
        requireLimit(limit)
        organizations.findActivePublicById(organizationId).orElseThrow(::notFound)
        val maxVisibility = organizationVisibility(principal, organizationId)
        val listKind = "organization-$organizationId"
        val filterHash = filterHash(
            "ORGANIZATION",
            organizationId.toString(),
            maxVisibility.toString()
        )
        val pageCursor = decode(cursor, listKind, filterHash)
        val rows = queries.findAuthorPage(
            "ORGANIZATION",
            organizationId,
            maxVisibility,
            pageCursor.time,
            pageCursor.postId,
            limit + 1
        )
        return publicPage(rows, limit, listKind, filterHash)
    }

    override
    @Transactional(readOnly = true)
    fun listFollowingFeed(
        principal: UserPrincipal?,
        cursor: String?,
        limit: Int
    ): CursorPage<PublicPostView> {
        requirePrincipal(principal)
        requireLimit(limit)
        val filterHash = filterHash("FOLLOWING")
        val pageCursor = decode(cursor, "following-feed", filterHash)
        val rows = queries.findFollowingFeedPage(
            principal!!.userId,
            pageCursor.time,
            pageCursor.postId,
            limit + 1
        )
        return publicPage(rows, limit, "following-feed", filterHash)
    }

    override
    @Transactional(readOnly = true)
    fun canReadMedia(principal: UserPrincipal?, fileId: Long): Boolean {
        if (fileId <= 0) {
            return false
        }
        val row = queries.findByMediaFileId(fileId).orElse(null)
        if (row == null || row.status == PostEntity.STATUS_DELETED) {
            return false
        }
        if (principal != null && canManage(principal, row)) {
            return true
        }
        return row.status == PostEntity.STATUS_PUBLISHED
            && queries.isPostAuthorPubliclyAvailable(row.postId!!)
            && canRead(principal, row)
    }

    private fun managedPage(
        rows: List<PostDetailRow>,
        limit: Int,
        listKind: String,
        filterHash: String
    ): CursorPage<ManagedPostSummaryView> {
        val hasMore = rows.size > limit
        val pageRows = rows.subList(0, minOf(rows.size, limit))
        val items = pageRows.map(::managedSummary)
        val nextCursor = if (hasMore && pageRows.isNotEmpty()) {
            encode(
                listKind,
                filterHash,
                pageRows.last().updatedAt!!,
                pageRows.last().postId!!
            )
        } else {
            null
        }
        return CursorPage(items, nextCursor, hasMore)
    }

    private fun publicPage(
        rows: List<PostDetailRow>,
        limit: Int,
        listKind: String,
        filterHash: String
    ): CursorPage<PublicPostView> {
        val hasMore = rows.size > limit
        val pageRows = rows.subList(0, minOf(rows.size, limit))
        val items = pageRows.map(::publicView)
        val nextCursor = if (hasMore && pageRows.isNotEmpty()) {
            encode(
                listKind,
                filterHash,
                pageRows.last().publishedAt!!,
                pageRows.last().postId!!
            )
        } else {
            null
        }
        return CursorPage(items, nextCursor, hasMore)
    }

    private fun managedDetail(row: PostDetailRow): ManagedPostDetailView =
        managedDetail(row, false)

    private fun managedDetail(
        row: PostDetailRow,
        currentRead: Boolean
    ): ManagedPostDetailView = ManagedPostDetailView(
        row.postId.toString(),
        author(row),
        statusName(row.status!!),
        visibilityName(row.visibility!!),
        row.title,
        row.content,
        media(row.postId!!, currentRead),
        activity(row.activityId),
        row.version!!,
        instant(row.publishedAt),
        instant(row.updatedAt),
        instant(row.createdAt),
        instant(row.hiddenAt)
    )

    private fun managedSummary(row: PostDetailRow): ManagedPostSummaryView =
        ManagedPostSummaryView(
            row.postId.toString(),
            author(row),
            statusName(row.status!!),
            visibilityName(row.visibility!!),
            row.title,
            row.content,
            media(row.postId!!),
            activity(row.activityId),
            row.version!!,
            instant(row.publishedAt),
            instant(row.updatedAt)
        )

    private fun publicView(row: PostDetailRow): PublicPostView = PublicPostView(
        row.postId.toString(),
        author(row),
        visibilityName(row.visibility!!),
        row.title,
        row.content,
        media(row.postId!!),
        activity(row.activityId),
        instant(row.publishedAt)
    )

    private fun author(row: PostDetailRow): PostAuthorSummaryView {
        val personal = row.authorUserId != null
        val authorId = if (personal) row.authorUserId else row.authorOrganizationId
        return PostAuthorSummaryView(
            if (personal) "USER" else "ORGANIZATION",
            authorId.toString(),
            row.authorDisplayName,
            image(row.authorAvatarFileId)
        )
    }

    private fun media(postId: Long): List<PublicImageView> = media(postId, false)

    private fun media(postId: Long, currentRead: Boolean): List<PublicImageView> {
        val fileIds = if (currentRead) {
            queries.findMediaFileIdsForUpdate(postId)
        } else {
            queries.findMediaFileIds(postId)
        }
        return fileIds.mapNotNull(::image)
    }

    private fun activity(activityId: Long?): PostActivityCardView? {
        if (activityId == null) {
            return null
        }
        return queries.findPublicActivityCard(activityId)
            .map(::activity)
            .orElse(null)
    }

    private fun activity(row: PostActivityRow): PostActivityCardView = PostActivityCardView(
        row.activityId.toString(),
        activityStatus(row.status!!),
        row.title,
        image(row.coverFileId),
        instant(row.startsAt),
        instant(row.endsAt)
    )

    private fun canManage(principal: UserPrincipal, row: PostDetailRow): Boolean {
        if (row.authorUserId != null) {
            return row.authorUserId == principal.userId
        }
        return row.authorOrganizationId != null
            && organizations.isSoleOwner(principal.userId, row.authorOrganizationId)
    }

    private fun canRead(principal: UserPrincipal?, row: PostDetailRow): Boolean {
        if (row.visibility == PostEntity.VISIBILITY_PUBLIC) {
            return true
        }
        if (principal == null) {
            return false
        }
        if (row.authorUserId != null) {
            if (row.authorUserId == principal.userId) {
                return true
            }
            return row.visibility == PostEntity.VISIBILITY_FOLLOWERS_ONLY
                && userFollows.find(principal.userId, row.authorUserId).isPresent
        }
        if (organizations.isSoleOwner(principal.userId, row.authorOrganizationId!!)) {
            return true
        }
        return row.visibility == PostEntity.VISIBILITY_FOLLOWERS_ONLY
            && organizationFollows.find(principal.userId, row.authorOrganizationId!!).isPresent
    }

    private fun userVisibility(principal: UserPrincipal?, authorUserId: Long): Int {
        if (principal == null) {
            return PostEntity.VISIBILITY_PUBLIC
        }
        if (principal.userId == authorUserId) {
            return PostEntity.VISIBILITY_PRIVATE
        }
        return if (userFollows.find(principal.userId, authorUserId).isPresent) {
            PostEntity.VISIBILITY_FOLLOWERS_ONLY
        } else {
            PostEntity.VISIBILITY_PUBLIC
        }
    }

    private fun organizationVisibility(
        principal: UserPrincipal?,
        organizationId: Long
    ): Int {
        if (principal == null) {
            return PostEntity.VISIBILITY_PUBLIC
        }
        if (organizations.isSoleOwner(principal.userId, organizationId)) {
            return PostEntity.VISIBILITY_PRIVATE
        }
        return if (organizationFollows.find(principal.userId, organizationId).isPresent) {
            PostEntity.VISIBILITY_FOLLOWERS_ONLY
        } else {
            PostEntity.VISIBILITY_PUBLIC
        }
    }

    private fun managedStatus(value: String?): Int? {
        if (value == null) {
            return null
        }
        return when (value.trim().uppercase(Locale.ROOT)) {
            "DRAFT" -> PostEntity.STATUS_DRAFT
            "PUBLISHED" -> PostEntity.STATUS_PUBLISHED
            "HIDDEN" -> PostEntity.STATUS_HIDDEN
            else -> throw validation()
        }
    }

    private fun authorType(value: String?): String? {
        if (value == null) {
            return null
        }
        return when (value.trim().uppercase(Locale.ROOT)) {
            "USER" -> "USER"
            "ORGANIZATION" -> "ORGANIZATION"
            else -> throw validation()
        }
    }

    private fun statusName(value: Int): String = when (value) {
        PostEntity.STATUS_DRAFT -> "DRAFT"
        PostEntity.STATUS_PUBLISHED -> "PUBLISHED"
        PostEntity.STATUS_HIDDEN -> "HIDDEN"
        else -> throw notFound()
    }

    private fun visibilityName(value: Int): String = when (value) {
        PostEntity.VISIBILITY_PUBLIC -> "PUBLIC"
        PostEntity.VISIBILITY_FOLLOWERS_ONLY -> "FOLLOWERS_ONLY"
        PostEntity.VISIBILITY_PRIVATE -> "PRIVATE"
        else -> throw notFound()
    }

    private fun activityStatus(value: Int): String = when (value) {
        2 -> "PUBLISHED"
        3 -> "CANCELLED"
        4 -> "ENDED"
        else -> throw notFound()
    }

    private fun image(fileId: Long?): PublicImageView? =
        if (fileId == null) null else PublicImageView(
            fileId.toString(),
            "/api/v1/files/$fileId/content"
        )

    private fun decode(
        cursor: String?,
        listKind: String,
        expectedFilterHash: String
    ): PageCursor {
        if (cursor == null) {
            return PageCursor(null, null)
        }
        if (cursor.isBlank() || cursor.length > MAX_CURSOR_LENGTH) {
            throw validation()
        }
        try {
            val raw = String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
            val parts = raw.split(Regex(":"), -1)
            if (parts.size != 5
                || "p1" != parts[0]
                || listKind != parts[1]
                || expectedFilterHash != parts[2]
            ) {
                throw validation()
            }
            val epochMillis = parts[3].toLong()
            val postId = parts[4].toLong()
            if (epochMillis < 0 || postId <= 0) {
                throw validation()
            }
            return PageCursor(
                LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC),
                postId
            )
        } catch (exception: IllegalArgumentException) {
            throw validation()
        } catch (exception: DateTimeException) {
            throw validation()
        }
    }

    private fun encode(
        listKind: String,
        filterHash: String,
        time: LocalDateTime,
        postId: Long
    ): String {
        val raw = "p1:$listKind:$filterHash:" +
            time.toInstant(ZoneOffset.UTC).toEpochMilli() +
            ":" + postId
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
    }

    private fun filterHash(vararg values: String): String {
        val source = values.joinToString("|")
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(source.toByteArray(StandardCharsets.UTF_8))
            )
        } catch (exception: NoSuchAlgorithmException) {
            throw IllegalStateException("运行环境缺少 SHA-256", exception)
        }
    }

    private fun instant(value: LocalDateTime?): Instant? =
        value?.toInstant(ZoneOffset.UTC)

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

    private fun requireLimit(limit: Int) {
        if (limit < 1 || limit > 50) {
            throw validation()
        }
    }

    private fun validation(): BusinessException =
        BusinessException(CommonErrorCode.VALIDATION_FAILED)

    private fun notFound(): BusinessException =
        BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND)

    private data class PageCursor(val time: LocalDateTime?, val postId: Long?)

    companion object {
        private const val MAX_CURSOR_LENGTH = 512
    }
}
