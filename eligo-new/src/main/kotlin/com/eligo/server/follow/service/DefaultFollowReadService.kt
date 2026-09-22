package com.eligo.server.follow.service

import com.eligo.server.activity.vo.PublicImageView
import com.eligo.server.common.api.CursorPage
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.follow.mapper.FollowReadMapper
import com.eligo.server.follow.mapper.FollowTargetRow
import com.eligo.server.follow.mapper.FollowerRow
import com.eligo.server.follow.mapper.PublicUserProfileRow
import com.eligo.server.follow.vo.FollowTargetSummaryView
import com.eligo.server.follow.vo.FollowerSummaryView
import com.eligo.server.follow.vo.PublicUserProfileView
import com.eligo.server.profile.entity.InterestTagEntity
import com.eligo.server.profile.mapper.InterestTagMapper
import com.eligo.server.profile.vo.InterestTagView
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
class DefaultFollowReadService(
    private val reads: FollowReadMapper,
    private val tags: InterestTagMapper
) : FollowReadService {

    @Transactional(readOnly = true)
    override fun listFollowing(
        principal: UserPrincipal?,
        cursor: String?,
        limit: Int,
        type: String?,
        keyword: String?,
        sort: String?
    ): CursorPage<FollowTargetSummaryView> {
        requirePrincipal(principal)
        requireLimit(limit)
        val normalizedType = targetType(type)
        val normalizedKeyword = keyword(keyword)
        val recent = recent(sort)
        val filterHash = filterHash(
            normalizedType ?: "ALL",
            normalizedKeyword,
            if (recent) "RECENT" else "EARLIEST"
        )
        val pageCursor = decode(cursor, "following", filterHash)
        val rows = reads.findFollowingPage(
            principal!!.userId,
            normalizedType,
            normalizedKeyword,
            pageCursor.followedAt,
            pageCursor.followId,
            recent,
            limit + 1
        )
        val hasMore = rows.size > limit
        val pageRows = rows.subList(0, minOf(rows.size, limit))
        val items = pageRows.map { targetView(it) }
        val nextCursor = if (hasMore && pageRows.isNotEmpty())
            encode(
                "following",
                filterHash,
                pageRows[pageRows.size - 1].followedAt!!,
                pageRows[pageRows.size - 1].followId
            )
        else null
        return CursorPage(items, nextCursor, hasMore)
    }

    @Transactional(readOnly = true)
    override fun listFollowers(
        principal: UserPrincipal?,
        cursor: String?,
        limit: Int,
        keyword: String?,
        sort: String?
    ): CursorPage<FollowerSummaryView> {
        requirePrincipal(principal)
        requireLimit(limit)
        val normalizedKeyword = keyword(keyword)
        val recent = recent(sort)
        val filterHash = filterHash(
            "USER",
            normalizedKeyword,
            if (recent) "RECENT" else "EARLIEST"
        )
        val pageCursor = decode(cursor, "followers", filterHash)
        val rows = reads.findFollowerPage(
            principal!!.userId,
            normalizedKeyword,
            pageCursor.followedAt,
            pageCursor.followId,
            recent,
            limit + 1
        )
        val hasMore = rows.size > limit
        val pageRows = rows.subList(0, minOf(rows.size, limit))
        val items = pageRows.map { followerView(it) }
        val nextCursor = if (hasMore && pageRows.isNotEmpty())
            encode(
                "followers",
                filterHash,
                pageRows[pageRows.size - 1].followedAt!!,
                pageRows[pageRows.size - 1].followId
            )
        else null
        return CursorPage(items, nextCursor, hasMore)
    }

    @Transactional(readOnly = true)
    override fun getPublicUserProfile(userId: Long): PublicUserProfileView {
        if (userId <= 0) {
            throw validation()
        }
        val profile = reads.findPublicUserProfile(userId).orElseThrow(::notFound)
        val interestTags = tags.findSelectedByUserId(userId).map { tagView(it) }
        return PublicUserProfileView(
            profile.userId.toString(),
            profile.nickname!!,
            image(profile.avatarFileId),
            profile.bio,
            interestTags,
            reads.countPublicFollowing(userId),
            reads.countPublicFollowers(userId)
        )
    }

    private fun targetView(row: FollowTargetRow): FollowTargetSummaryView = FollowTargetSummaryView(
        row.followId.toString(),
        row.targetType!!,
        row.targetId.toString(),
        row.displayName!!,
        image(row.avatarFileId),
        instant(row.followedAt!!)
    )

    private fun followerView(row: FollowerRow): FollowerSummaryView = FollowerSummaryView(
        row.followId.toString(),
        row.userId.toString(),
        row.nickname!!,
        image(row.avatarFileId),
        instant(row.followedAt!!)
    )

    private fun tagView(tag: InterestTagEntity): InterestTagView = InterestTagView(
        tag.id.toString(),
        tag.tagCode,
        tag.tagName,
        tag.sortOrder ?: 0
    )

    private fun image(fileId: Long?): PublicImageView? =
        if (fileId == null) null
        else PublicImageView(fileId.toString(), "/api/v1/files/$fileId/content")

    private fun instant(value: LocalDateTime): Instant = value.toInstant(ZoneOffset.UTC)

    private fun targetType(value: String?): String? {
        if (value == null) return null
        return when (value.trim().uppercase(Locale.ROOT)) {
            "USER" -> "USER"
            "ORGANIZATION" -> "ORGANIZATION"
            else -> throw validation()
        }
    }

    private fun keyword(value: String?): String? {
        if (value == null) return null
        val normalized = value.trim()
        if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length) > 64) {
            throw validation()
        }
        return normalized
    }

    private fun recent(value: String?): Boolean {
        if (value == null) return true
        return when (value.trim().uppercase(Locale.ROOT)) {
            "RECENT" -> true
            "EARLIEST" -> false
            else -> throw validation()
        }
    }

    private fun decode(cursor: String?, listKind: String, expectedFilterHash: String): PageCursor {
        if (cursor == null) return PageCursor(null, null)
        if (cursor.isBlank() || cursor.length > MAX_CURSOR_LENGTH) {
            throw validation()
        }
        return try {
            val raw = String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
            val parts = raw.split(":").toTypedArray()
            if (parts.size != 5 || parts[0] != "f1" || parts[1] != listKind || parts[2] != expectedFilterHash) {
                throw validation()
            }
            val epochMillis = parts[3].toLong()
            val followId = parts[4].toLong()
            if (epochMillis < 0 || followId <= 0) {
                throw validation()
            }
            PageCursor(
                LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC),
                followId
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
        followedAt: LocalDateTime,
        followId: Long?
    ): String {
        val raw = "f1:$listKind:$filterHash:" +
            followedAt.toInstant(ZoneOffset.UTC).toEpochMilli() +
            ":" + followId
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
    }

    private fun filterHash(type: String, keyword: String?, sort: String): String {
        val source = type + "|" + (keyword ?: "") + "|" + sort
        return try {
            HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(source.toByteArray(StandardCharsets.UTF_8))
            )
        } catch (exception: NoSuchAlgorithmException) {
            throw IllegalStateException("运行环境缺少 SHA-256", exception)
        }
    }

    private fun requirePrincipal(principal: UserPrincipal?) {
        if (principal == null || principal.userId <= 0) {
            throw BusinessException(CommonErrorCode.AUTHENTICATION_REQUIRED)
        }
    }

    private fun requireLimit(limit: Int) {
        if (limit < 1 || limit > 50) {
            throw validation()
        }
    }

    private fun validation(): BusinessException = BusinessException(CommonErrorCode.VALIDATION_FAILED)

    private fun notFound(): BusinessException = BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND)

    private data class PageCursor(val followedAt: LocalDateTime?, val followId: Long?)

    companion object {
        private const val MAX_CURSOR_LENGTH = 512
    }
}
