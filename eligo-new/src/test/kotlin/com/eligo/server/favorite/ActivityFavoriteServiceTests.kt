package com.eligo.server.favorite

import java.util.function.Function

import com.eligo.server.account.service.AccountStateLockService
import com.eligo.server.activity.service.ActivityEngagementAccessService
import com.eligo.server.activity.service.ActivityEngagementSnapshot
import com.eligo.server.activity.vo.ActivityOwnerSummaryView
import com.eligo.server.activity.vo.PublicActivitySummaryView
import com.eligo.server.common.api.CursorPage
import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.favorite.entity.ActivityFavoriteEntity
import com.eligo.server.favorite.mapper.ActivityFavoriteMapper
import com.eligo.server.favorite.mapper.ActivityFavoriteRow
import com.eligo.server.favorite.service.DefaultActivityFavoriteService
import com.eligo.server.favorite.vo.FavoriteActivityView
import com.eligo.server.favorite.vo.FavoriteStateView
import com.eligo.server.profile.service.ProfileCompletionReader
import com.eligo.server.security.UserPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DuplicateKeyException
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Optional

class ActivityFavoriteServiceTests {

    private val userId = 202L
    private val activityId = 301L
    private val now = Instant.parse("2026-08-22T08:00:00Z")
    private val localNow = LocalDateTime.ofInstant(now, ZoneOffset.UTC)
    private val principal = UserPrincipal(userId, "favorite-session")

    private val favorites = mock(ActivityFavoriteMapper::class.java)
    private val activities = mock(ActivityEngagementAccessService::class.java)
    private val completion = mock(ProfileCompletionReader::class.java)
    private val accountStates = mock(AccountStateLockService::class.java)
    private lateinit var service: DefaultActivityFavoriteService

    @BeforeEach
    fun setUp() {
        whenever(completion.isCompleted(userId)).thenReturn(true)
        whenever(activities.findPublicById(activityId))
            .thenReturn(Optional.of(snapshot(activityId)))
        service = DefaultActivityFavoriteService(
            favorites,
            activities,
            completion,
            accountStates,
            Clock.fixed(now, ZoneOffset.UTC)
        )
    }

    @Test
    fun favoriteRequiresPublicActivityAndPersistsRelationship() {
        whenever(favorites.find(userId, activityId)).thenReturn(Optional.empty())
        whenever(favorites.insert(any<ActivityFavoriteEntity>())).thenAnswer { invocation ->
            val entity = invocation.getArgument<ActivityFavoriteEntity>(0)
            entity.id = 501L
            1
        }

        val result = service.favorite(principal, activityId)

        assertThat(result).isEqualTo(FavoriteStateView(true, now))
        val created = ArgumentCaptor.forClass(ActivityFavoriteEntity::class.java)
        verify(favorites).insert(created.capture())
        verify(accountStates).lockActive(userId)
        assertThat(created.value.userId).isEqualTo(userId)
        assertThat(created.value.activityId).isEqualTo(activityId)
        assertThat(created.value.favoritedAt).isEqualTo(localNow)

        whenever(activities.findPublicById(activityId)).thenReturn(Optional.empty())
        assertError({ service.favorite(principal, activityId) }, CommonErrorCode.RESOURCE_NOT_FOUND)
    }

    @Test
    fun repeatedAndConcurrentFavoriteReturnExistingRelationship() {
        val existing = favorite(501L, activityId, localNow)
        whenever(favorites.find(userId, activityId)).thenReturn(Optional.of(existing))

        assertThat(service.favorite(principal, activityId))
            .isEqualTo(FavoriteStateView(true, now))
        verify(favorites, never()).insert(any<ActivityFavoriteEntity>())

        whenever(favorites.find(userId, activityId)).thenReturn(Optional.empty())
        whenever(favorites.insert(any<ActivityFavoriteEntity>()))
            .thenThrow(DuplicateKeyException("duplicate"))
        whenever(favorites.lockRelation(userId, activityId))
            .thenReturn(Optional.of(existing))

        assertThat(service.favorite(principal, activityId))
            .isEqualTo(FavoriteStateView(true, now))
    }

    @Test
    fun favoriteAndPersonalListRequireCompletedProfile() {
        whenever(completion.isCompleted(userId)).thenReturn(false)

        assertError({ service.favorite(principal, activityId) }, AccountUserFileErrorCode.PROFILE_INCOMPLETE)
        assertError({ service.listMine(principal, null, 20) }, AccountUserFileErrorCode.PROFILE_INCOMPLETE)
    }

    @Test
    fun stateQueryValidatesPublicVisibilityBeforeReturningRelation() {
        whenever(favorites.find(userId, activityId))
            .thenReturn(Optional.of(favorite(501L, activityId, localNow)))

        assertThat(service.getState(principal, activityId))
            .isEqualTo(FavoriteStateView(true, now))

        whenever(activities.findPublicById(activityId)).thenReturn(Optional.empty())
        assertError({ service.getState(principal, activityId) }, CommonErrorCode.RESOURCE_NOT_FOUND)
    }

    @Test
    fun unfavoriteIsIdempotentAndDoesNotRequireCurrentActivityVisibility() {
        whenever(favorites.deleteRelation(userId, activityId)).thenReturn(1, 0)

        assertThat(service.unfavorite(principal, activityId))
            .isEqualTo(FavoriteStateView(false, null))
        assertThat(service.unfavorite(principal, activityId))
            .isEqualTo(FavoriteStateView(false, null))
        verify(activities, never()).findPublicById(activityId)
        verify(accountStates, times(2)).lockExisting(userId)
    }

    @Test
    fun personalListUsesDescendingFavoriteCursorAndM2PublicSummaries() {
        val older = localNow.minusHours(1)
        val first = ActivityFavoriteRow(502L, 302L, localNow)
        val second = ActivityFavoriteRow(501L, 301L, older)
        whenever(favorites.findPage(userId, null, null, 3))
            .thenReturn(listOf(first, second))
        val firstSummary = summary("302", "第一场")
        val secondSummary = summary("301", "第二场")
        whenever(activities.findPublicSummaries(listOf(302L, 301L)))
            .thenReturn(mapOf(302L to firstSummary, 301L to secondSummary))

        val page = service.listMine(principal, null, 2)

        assertThat(page.items).extracting(Function {  item -> item.activity.activityId  })
            .containsExactly("302", "301")
        assertThat(page.items).extracting(Function { it.favoritedAt })
            .containsExactly(now, now.minusSeconds(3600))
        assertThat(page.hasMore).isFalse()
        assertThat(page.nextCursor).isNull()
        verify(favorites).findPage(userId, null, null, 3)
    }

    private fun snapshot(activityId: Long): ActivityEngagementSnapshot {
        return ActivityEngagementSnapshot(
            activityId, 2, localNow.plusDays(1), userId, null
        )
    }

    private fun favorite(id: Long, activityId: Long, time: LocalDateTime): ActivityFavoriteEntity {
        val entity = ActivityFavoriteEntity()
        entity.id = id
        entity.userId = userId
        entity.activityId = activityId
        entity.favoritedAt = time
        return entity
    }

    private fun summary(id: String, title: String): PublicActivitySummaryView {
        return PublicActivitySummaryView(
            id, "PUBLISHED", title, "OUTDOOR", null,
            ActivityOwnerSummaryView("USER", "202", "组织者", null),
            now, now.plusSeconds(3600), "440100", "地址",
            null, null, null, 0, "OPEN", "FREE", "集合点",
            null, null, listOf()
        )
    }

    private fun assertError(action: () -> Unit, errorCode: Any) {
        assertThatThrownBy(action)
            .isInstanceOfSatisfying(BusinessException::class.java) { exception ->
                assertThat(exception.errorCode)
                    .isEqualTo(errorCode)
            }
    }
}
