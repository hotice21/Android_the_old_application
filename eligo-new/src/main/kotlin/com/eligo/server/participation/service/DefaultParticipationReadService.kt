package com.eligo.server.participation.service

import com.eligo.server.activity.service.ActivityParticipationAccessService
import com.eligo.server.activity.service.ActivityParticipationSnapshot
import com.eligo.server.activity.vo.ActivityOwnerSummaryView
import com.eligo.server.activity.vo.PublicActivitySummaryView
import com.eligo.server.activity.vo.PublicImageView
import com.eligo.server.common.api.CursorPage
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.participation.mapper.ActivityParticipantRow
import com.eligo.server.participation.mapper.ActivityParticipationMapper
import com.eligo.server.participation.mapper.MyParticipationRow
import com.eligo.server.participation.vo.ActivityParticipantSummaryView
import com.eligo.server.participation.vo.MyParticipationView
import com.eligo.server.security.UserPrincipal
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.Locale
import java.util.Optional
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test")
class DefaultParticipationReadService(
    private val activities: ActivityParticipationAccessService,
    private val participations: ActivityParticipationMapper,
    private val clock: Clock = Clock.systemUTC()
) : ParticipationReadService {

    override
    @Transactional(readOnly = true)
    fun listParticipants(
        principal: UserPrincipal?,
        activityId: Long,
        cursor: String?,
        limit: Int
    ): CursorPage<ActivityParticipantSummaryView> {
        requirePrincipal(principal)
        if (activityId <= 0 || limit < 1 || limit > 50) {
            throw validation()
        }
        val activity = activities.findById(activityId).orElseThrow(::notFound)
        if (activity.status != PUBLISHED
            && activity.status != CANCELLED_ACTIVITY
            && activity.status != ENDED_ACTIVITY
        ) {
            throw notFound()
        }
        val decoded = decode(cursor, "p1")
        val rows = participations.findActiveParticipantPage(
            activityId,
            decoded.time,
            decoded.id,
            limit + 1
        )
        val hasMore = rows.size > limit
        val pageRows = rows.subList(0, minOf(rows.size, limit))
        val items = pageRows.map(::participantView)
        val nextCursor = if (hasMore && pageRows.isNotEmpty()) {
            encode("p1", pageRows.last().joinedAt!!, pageRows.last().userId!!)
        } else {
            null
        }
        return CursorPage(items, nextCursor, hasMore)
    }

    override
    @Transactional(readOnly = true)
    fun findStatus(activityId: Long, userId: Long): Optional<String> {
        if (activityId <= 0 || userId <= 0) {
            throw validation()
        }
        return participations.findByActivityAndUser(activityId, userId)
            .map { participationStatusName(it.status) }
    }

    override
    @Transactional(readOnly = true)
    fun listMyParticipations(
        principal: UserPrincipal?,
        cursor: String?,
        limit: Int,
        status: String?,
        keyword: String?
    ): CursorPage<MyParticipationView> {
        requirePrincipal(principal)
        if (limit < 1 || limit > 50) {
            throw validation()
        }
        val statusCode = participationStatus(status)
        val normalizedKeyword = keyword(keyword)
        val decoded = decode(cursor, "m1")
        val rows = participations.findMyParticipationPage(
            principal!!.userId,
            statusCode,
            normalizedKeyword,
            decoded.time,
            decoded.id,
            limit + 1
        )
        val hasMore = rows.size > limit
        val pageRows = rows.subList(0, minOf(rows.size, limit))
        val now = now()
        val items = pageRows.map { myView(it, now) }
        val nextCursor = if (hasMore && pageRows.isNotEmpty()) {
            encode("m1", pageRows.last().joinedAt!!, pageRows.last().participationId!!)
        } else {
            null
        }
        return CursorPage(items, nextCursor, hasMore)
    }

    private fun participantView(row: ActivityParticipantRow): ActivityParticipantSummaryView =
        ActivityParticipantSummaryView(
            row.userId.toString(),
            row.nickname,
            image(row.avatarFileId),
            instant(row.joinedAt)
        )

    private fun myView(row: MyParticipationRow, now: LocalDateTime): MyParticipationView =
        MyParticipationView(
            row.participationId.toString(),
            row.userId.toString(),
            participationStatusName(row.participationStatus),
            instant(row.joinedAt),
            instant(row.cancelledAt),
            instant(row.terminatedAt),
            reasonName(row.terminationReason),
            activitySummary(row, now)
        )

    private fun activitySummary(
        row: MyParticipationRow,
        now: LocalDateTime
    ): PublicActivitySummaryView {
        val effectiveStatus = if (row.activityStatus == PUBLISHED
            && row.endsAt != null
            && !now.isBefore(row.endsAt)
        ) {
            ENDED_ACTIVITY
        } else {
            row.activityStatus
        }
        return PublicActivitySummaryView(
            row.activityId.toString(),
            activityStatusName(effectiveStatus),
            row.title ?: "",
            row.categoryCode,
            image(row.coverFileId),
            ActivityOwnerSummaryView(
                row.ownerType ?: "",
                row.ownerId.toString(),
                row.ownerDisplayName ?: "",
                image(row.ownerAvatarFileId)
            ),
            instant(row.startsAt),
            instant(row.endsAt),
            row.regionCode,
            row.addressDetail,
            row.latitude,
            row.longitude,
            row.capacity,
            row.participantCount,
            registrationStatus(row, effectiveStatus!!, now),
            "FREE"
        )
    }

    private fun registrationStatus(
        row: MyParticipationRow,
        activityStatus: Int,
        now: LocalDateTime
    ): String {
        when (activityStatus) {
            CANCELLED_ACTIVITY -> return "CANCELLED"
            ENDED_ACTIVITY -> return "ENDED"
            5 -> return "CLOSED"
        }
        if (row.registrationStartsAt != null
            && now.isBefore(row.registrationStartsAt)
        ) {
            return "NOT_STARTED"
        }
        if (row.registrationEndsAt != null
            && !now.isBefore(row.registrationEndsAt)
        ) {
            return "CLOSED"
        }
        if (row.capacity != null
            && row.participantCount != null
            && row.participantCount >= row.capacity
        ) {
            return "FULL"
        }
        return "OPEN"
    }

    private fun participationStatus(value: String?): Int? {
        if (value == null) {
            return null
        }
        return when (value.trim().uppercase(Locale.ROOT)) {
            "ACTIVE" -> 1
            "CANCELLED" -> 2
            "TERMINATED" -> 3
            else -> throw validation()
        }
    }

    private fun keyword(value: String?): String? {
        if (value == null) {
            return null
        }
        val normalized = value.trim()
        if (normalized.isEmpty()
            || normalized.codePointCount(0, normalized.length) > 20
        ) {
            throw validation()
        }
        return normalized
    }

    private fun participationStatusName(value: Int?): String = when (value) {
        1 -> "ACTIVE"
        2 -> "CANCELLED"
        3 -> "TERMINATED"
        else -> throw notFound()
    }

    private fun reasonName(value: Int?): String? {
        if (value == null) {
            return null
        }
        return when (value) {
            1 -> "ACTIVITY_CANCELLED"
            2 -> "REMOVED_BY_OWNER"
            else -> throw notFound()
        }
    }

    private fun activityStatusName(value: Int?): String = when (value) {
        PUBLISHED -> "PUBLISHED"
        CANCELLED_ACTIVITY -> "CANCELLED"
        ENDED_ACTIVITY -> "ENDED"
        5 -> "HIDDEN"
        else -> throw notFound()
    }

    private fun image(fileId: Long?): PublicImageView? =
        if (fileId == null) null else PublicImageView(
            fileId.toString(),
            "/api/v1/files/$fileId/content"
        )

    private fun decode(cursor: String?, prefix: String): PageCursor {
        if (cursor == null) {
            return PageCursor(null, null)
        }
        if (cursor.length > MAX_CURSOR_LENGTH) {
            throw validation()
        }
        try {
            val raw = String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
            val parts = raw.split(Regex(":"), -1)
            if (parts.size != 3 || prefix != parts[0]) {
                throw validation()
            }
            val epochMillis = parts[1].toLong()
            val id = parts[2].toLong()
            if (id <= 0) {
                throw validation()
            }
            return PageCursor(
                LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC
                ),
                id
            )
        } catch (exception: IllegalArgumentException) {
            throw validation()
        } catch (exception: java.time.DateTimeException) {
            throw validation()
        }
    }

    private fun encode(prefix: String, time: LocalDateTime, id: Long): String {
        val raw = prefix + ":" +
            time.toInstant(ZoneOffset.UTC).toEpochMilli() +
            ":" + id
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
    }

    private fun requirePrincipal(principal: UserPrincipal?) {
        if (principal == null) {
            throw BusinessException(CommonErrorCode.AUTHENTICATION_REQUIRED)
        }
    }

    private fun now(): LocalDateTime =
        LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)

    private fun instant(value: LocalDateTime?): Instant? =
        value?.toInstant(ZoneOffset.UTC)

    private fun validation(): BusinessException =
        BusinessException(CommonErrorCode.VALIDATION_FAILED)

    private fun notFound(): BusinessException =
        BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND)

    private data class PageCursor(val time: LocalDateTime?, val id: Long?)

    companion object {
        private const val PUBLISHED = 2
        private const val CANCELLED_ACTIVITY = 3
        private const val ENDED_ACTIVITY = 4
        private const val MAX_CURSOR_LENGTH = 512
    }
}
