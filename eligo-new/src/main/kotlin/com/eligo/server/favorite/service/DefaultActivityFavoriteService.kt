package com.eligo.server.favorite.service

import com.eligo.server.account.service.AccountStateLockService
import com.eligo.server.activity.service.ActivityEngagementAccessService
import com.eligo.server.activity.vo.PublicActivitySummaryView
import com.eligo.server.common.api.CursorPage
import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.favorite.entity.ActivityFavoriteEntity
import com.eligo.server.favorite.mapper.ActivityFavoriteMapper
import com.eligo.server.favorite.mapper.ActivityFavoriteRow
import com.eligo.server.favorite.vo.FavoriteActivityView
import com.eligo.server.favorite.vo.FavoriteStateView
import com.eligo.server.profile.service.ProfileCompletionReader
import com.eligo.server.security.UserPrincipal
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Base64
import org.springframework.context.annotation.Profile
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test")
class DefaultActivityFavoriteService(
    private val favorites: ActivityFavoriteMapper,
    private val activities: ActivityEngagementAccessService,
    private val completion: ProfileCompletionReader,
    private val accountStates: AccountStateLockService,
    private val clock: Clock = Clock.systemUTC()
) : ActivityFavoriteService {

    @Transactional
    override fun favorite(principal: UserPrincipal, activityId: Long): FavoriteStateView {
        requirePrincipalAndId(principal, activityId)
        accountStates.lockActive(principal.userId)
        requireCompletedProfile(principal.userId)
        requirePublicActivity(activityId)
        val current = favorites.find(principal.userId, activityId).orElse(null)
        if (current != null) {
            return view(current)
        }
        val created = ActivityFavoriteEntity()
        created.userId = principal.userId
        created.activityId = activityId
        created.favoritedAt = now()
        return try {
            if (favorites.insert(created) != 1) {
                throw BusinessException(CommonErrorCode.CONFLICT)
            }
            view(created)
        } catch (exception: DuplicateKeyException) {
            favorites.lockRelation(principal.userId, activityId)
                .map { view(it) }
                .orElseThrow { exception }
        }
    }

    @Transactional(readOnly = true)
    override fun getState(principal: UserPrincipal, activityId: Long): FavoriteStateView {
        requirePrincipalAndId(principal, activityId)
        requireCompletedProfile(principal.userId)
        requirePublicActivity(activityId)
        return favorites.find(principal.userId, activityId)
            .map { view(it) }
            .orElseGet { notFavorited() }
    }

    @Transactional
    override fun unfavorite(principal: UserPrincipal, activityId: Long): FavoriteStateView {
        requirePrincipalAndId(principal, activityId)
        accountStates.lockExisting(principal.userId)
        favorites.deleteRelation(principal.userId, activityId)
        return notFavorited()
    }

    @Transactional(readOnly = true)
    override fun listMine(
        principal: UserPrincipal,
        cursor: String?,
        limit: Int
    ): CursorPage<FavoriteActivityView> {
        requirePrincipal(principal)
        requireCompletedProfile(principal.userId)
        if (limit < 1 || limit > 50) {
            throw validation()
        }
        val decoded = decode(cursor)
        val rows = favorites.findPage(principal.userId, decoded.favoritedAt, decoded.favoriteId, limit + 1)
        val hasMore = rows.size > limit
        val pageRows = rows.subList(0, minOf(rows.size, limit))
        val summaries = activities.findPublicSummaries(pageRows.map { it.activityId!! })
        val items = pageRows.mapNotNull { row ->
            val activity = summaries[row.activityId]
            if (activity != null) FavoriteActivityView(instant(row.favoritedAt)!!, activity) else null
        }
        val nextCursor = if (hasMore && pageRows.isNotEmpty()) encode(pageRows[pageRows.size - 1]) else null
        return CursorPage(items, nextCursor, hasMore)
    }

    private fun requirePublicActivity(activityId: Long) {
        activities.findPublicById(activityId).orElseThrow { BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND) }
    }

    private fun requireCompletedProfile(userId: Long) {
        if (!completion.isCompleted(userId)) {
            throw BusinessException(AccountUserFileErrorCode.PROFILE_INCOMPLETE)
        }
    }

    private fun view(entity: ActivityFavoriteEntity): FavoriteStateView =
        FavoriteStateView(true, instant(entity.favoritedAt))

    private fun notFavorited(): FavoriteStateView = FavoriteStateView(false, null)

    private fun now(): LocalDateTime =
        LocalDateTime.ofInstant(clock.instant().truncatedTo(ChronoUnit.MILLIS), ZoneOffset.UTC)

    private fun instant(value: LocalDateTime?): Instant? = value?.toInstant(ZoneOffset.UTC)

    private fun decode(cursor: String?): FavoriteCursor {
        if (cursor == null) {
            return FavoriteCursor(null, null)
        }
        if (cursor.isBlank() || cursor.length > MAX_CURSOR_LENGTH) {
            throw validation()
        }
        try {
            val decoded = String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
            val parts = decoded.split("|")
            if (parts.size != 3 || parts[0] != "f1") {
                throw validation()
            }
            val favoritedAt = LocalDateTime.parse(parts[1])
            val favoriteId = parts[2].toLong()
            if (favoriteId <= 0) {
                throw validation()
            }
            return FavoriteCursor(favoritedAt, favoriteId)
        } catch (exception: IllegalArgumentException) {
            throw validation()
        }
    }

    private fun encode(row: ActivityFavoriteRow): String {
        val raw = "f1|${row.favoritedAt}|${row.favoriteId}"
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
    }

    private data class FavoriteCursor(val favoritedAt: LocalDateTime?, val favoriteId: Long?)

    companion object {
        private const val MAX_CURSOR_LENGTH = 256

        private fun validation(): BusinessException = BusinessException(CommonErrorCode.VALIDATION_FAILED)

        private fun requirePrincipalAndId(principal: UserPrincipal?, activityId: Long) {
            requirePrincipal(principal)
            if (activityId <= 0) {
                throw validation()
            }
        }

        private fun requirePrincipal(principal: UserPrincipal?) {
            if (principal == null || principal.userId <= 0) {
                throw validation()
            }
        }
    }
}
