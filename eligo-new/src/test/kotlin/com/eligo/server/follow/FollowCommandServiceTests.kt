package com.eligo.server.follow

import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.follow.entity.OrganizationFollowEntity
import com.eligo.server.follow.entity.UserFollowEntity
import com.eligo.server.follow.error.FollowErrorCode
import com.eligo.server.follow.mapper.OrganizationFollowMapper
import com.eligo.server.follow.mapper.UserFollowMapper
import com.eligo.server.follow.service.DefaultFollowCommandService
import com.eligo.server.follow.vo.FollowStateView
import com.eligo.server.organization.entity.OrganizationEntity
import com.eligo.server.organization.mapper.OrganizationMapper
import com.eligo.server.profile.mapper.UserProfileMapper
import com.eligo.server.profile.service.ProfileCompletionReader
import com.eligo.server.security.UserPrincipal
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DuplicateKeyException

class FollowCommandServiceTests {

    private val followerId = 202L
    private val targetUserId = 303L
    private val organizationId = 401L
    private val followId = 5001L
    private val now = Instant.parse("2026-08-16T00:00:00Z")
    private val localNow = LocalDateTime.ofInstant(now, ZoneOffset.UTC)
    private val principal = UserPrincipal(followerId, "session-m4")

    private val userFollows = mock<UserFollowMapper>()
    private val organizationFollows = mock<OrganizationFollowMapper>()
    private val profiles = mock<UserProfileMapper>()
    private val organizations = mock<OrganizationMapper>()
    private val completion = mock<ProfileCompletionReader>()
    private lateinit var service: DefaultFollowCommandService

    @BeforeEach
    fun setup() {
        whenever(completion.isCompleted(followerId)).thenReturn(true)
        service = DefaultFollowCommandService(
            userFollows,
            organizationFollows,
            profiles,
            organizations,
            completion,
            Clock.fixed(now, ZoneOffset.UTC)
        )
    }

    @Test
    fun userFollowValidatesTargetBeforeSelfAndExistingRelation() {
        whenever(profiles.existsActiveCompletedUser(followerId)).thenReturn(false)
        val existing = userFollow()
        whenever(userFollows.find(followerId, followerId))
            .thenReturn(Optional.of(existing))

        assertError({ service.followUser(principal, followerId) }, CommonErrorCode.RESOURCE_NOT_FOUND)

        whenever(profiles.existsActiveCompletedUser(followerId)).thenReturn(true)
        assertError({ service.followUser(principal, followerId) }, FollowErrorCode.CANNOT_FOLLOW_SELF)
        verify(completion, never()).isCompleted(followerId)
    }

    @Test
    fun existingUserFollowIsIdempotentBeforeCompletionCheck() {
        val existing = userFollow()
        whenever(profiles.existsActiveCompletedUser(targetUserId)).thenReturn(true)
        whenever(userFollows.find(followerId, targetUserId))
            .thenReturn(Optional.of(existing))
        whenever(completion.isCompleted(followerId)).thenReturn(false)

        val result = service.followUser(principal, targetUserId)

        assertThat(result.following).isTrue()
        assertThat(result.followedAt).isEqualTo(now)
        verify(userFollows, never()).insert(any<UserFollowEntity>())
        verify(completion, never()).isCompleted(followerId)
    }

    @Test
    fun newUserFollowRequiresCompletedProfileAndPersistsRelationship() {
        whenever(profiles.existsActiveCompletedUser(targetUserId)).thenReturn(true)
        whenever(userFollows.find(followerId, targetUserId))
            .thenReturn(Optional.empty())
        whenever(completion.isCompleted(followerId)).thenReturn(false)

        assertError(
            { service.followUser(principal, targetUserId) },
            AccountUserFileErrorCode.PROFILE_INCOMPLETE
        )

        whenever(completion.isCompleted(followerId)).thenReturn(true)
        whenever(userFollows.insert(any<UserFollowEntity>()))
            .thenAnswer { invocation ->
                val entity = invocation.getArgument<UserFollowEntity>(0)
                entity.id = followId
                1
            }

        val result = service.followUser(principal, targetUserId)

        assertThat(result).isEqualTo(FollowStateView(true, now))
        verify(userFollows).insert(any<UserFollowEntity>())
        val order = inOrder(profiles, userFollows, completion)
        order.verify(profiles).existsActiveCompletedUser(targetUserId)
        order.verify(userFollows).find(followerId, targetUserId)
        order.verify(completion).isCompleted(followerId)
        order.verify(userFollows).insert(any<UserFollowEntity>())
    }

    @Test
    fun duplicateUserInsertReturnsWinningRelationship() {
        val winner = userFollow()
        whenever(profiles.existsActiveCompletedUser(targetUserId)).thenReturn(true)
        whenever(userFollows.find(followerId, targetUserId))
            .thenReturn(Optional.empty())
        whenever(userFollows.lockRelation(followerId, targetUserId))
            .thenReturn(Optional.of(winner))
        whenever(userFollows.insert(any<UserFollowEntity>()))
            .thenThrow(DuplicateKeyException("duplicate"))

        val result = service.followUser(principal, targetUserId)

        assertThat(result).isEqualTo(FollowStateView(true, now))
        verify(userFollows).find(followerId, targetUserId)
        verify(userFollows).lockRelation(followerId, targetUserId)
    }

    @Test
    fun organizationFollowUsesSameIdempotencyAndCompletionRules() {
        val organization = OrganizationEntity()
        organization.id = organizationId
        val existing = organizationFollow()
        whenever(organizations.findActivePublicById(organizationId))
            .thenReturn(Optional.of(organization))
        whenever(organizationFollows.find(followerId, organizationId))
            .thenReturn(Optional.of(existing))
        whenever(completion.isCompleted(followerId)).thenReturn(false)

        assertThat(service.followOrganization(principal, organizationId))
            .isEqualTo(FollowStateView(true, now))
        verify(organizationFollows, never()).insert(any<OrganizationFollowEntity>())
        verify(completion, never()).isCompleted(followerId)

        whenever(organizations.findActivePublicById(organizationId))
            .thenReturn(Optional.empty())
        assertError(
            { service.followOrganization(principal, organizationId) },
            CommonErrorCode.RESOURCE_NOT_FOUND
        )
    }

    @Test
    fun newOrganizationFollowRecoversFromConcurrentDuplicate() {
        val organization = OrganizationEntity()
        organization.id = organizationId
        val winner = organizationFollow()
        whenever(organizations.findActivePublicById(organizationId))
            .thenReturn(Optional.of(organization))
        whenever(organizationFollows.find(followerId, organizationId))
            .thenReturn(Optional.empty())
        whenever(organizationFollows.lockRelation(followerId, organizationId))
            .thenReturn(Optional.of(winner))
        whenever(organizationFollows.insert(any<OrganizationFollowEntity>()))
            .thenThrow(DuplicateKeyException("duplicate"))

        assertThat(service.followOrganization(principal, organizationId))
            .isEqualTo(FollowStateView(true, now))
        verify(organizationFollows).lockRelation(followerId, organizationId)
    }

    @Test
    fun unfollowDoesNotRequireVisibleTargetOrCompletedProfile() {
        whenever(userFollows.deleteRelation(followerId, targetUserId)).thenReturn(1)
        whenever(organizationFollows.deleteRelation(followerId, organizationId))
            .thenReturn(0)

        assertThat(service.unfollowUser(principal, targetUserId))
            .isEqualTo(FollowStateView(false, null))
        assertThat(service.unfollowOrganization(principal, organizationId))
            .isEqualTo(FollowStateView(false, null))
        verify(profiles, never()).existsActiveCompletedUser(targetUserId)
        verify(organizations, never()).findActivePublicById(organizationId)
        verify(completion, never()).isCompleted(followerId)
    }

    @Test
    fun stateQueriesRequireVisibleTargetAndReturnRelationshipState() {
        val userFollow = userFollow()
        val organizationFollow = organizationFollow()
        val organization = OrganizationEntity()
        organization.id = organizationId
        whenever(profiles.existsActiveCompletedUser(targetUserId)).thenReturn(true)
        whenever(organizations.findActivePublicById(organizationId))
            .thenReturn(Optional.of(organization))
        whenever(userFollows.find(followerId, targetUserId))
            .thenReturn(Optional.of(userFollow))
        whenever(organizationFollows.find(followerId, organizationId))
            .thenReturn(Optional.of(organizationFollow))

        assertThat(service.getUserState(principal, targetUserId))
            .isEqualTo(FollowStateView(true, now))
        assertThat(service.getOrganizationState(principal, organizationId))
            .isEqualTo(FollowStateView(true, now))

        whenever(profiles.existsActiveCompletedUser(targetUserId)).thenReturn(false)
        assertError(
            { service.getUserState(principal, targetUserId) },
            CommonErrorCode.RESOURCE_NOT_FOUND
        )
    }

    @Test
    fun invalidIdsAreRejectedBeforeMapperCalls() {
        assertError({ service.followUser(principal, 0) }, CommonErrorCode.VALIDATION_FAILED)
        assertError(
            { service.unfollowOrganization(principal, -1) },
            CommonErrorCode.VALIDATION_FAILED
        )
        verify(userFollows, never()).find(followerId, 0)
        verify(organizationFollows, never()).deleteRelation(followerId, -1)
    }

    private fun userFollow(): UserFollowEntity {
        val entity = UserFollowEntity()
        entity.id = followId
        entity.followerUserId = followerId
        entity.followedUserId = targetUserId
        entity.followedAt = localNow
        return entity
    }

    private fun organizationFollow(): OrganizationFollowEntity {
        val entity = OrganizationFollowEntity()
        entity.id = followId
        entity.followerUserId = followerId
        entity.organizationId = organizationId
        entity.followedAt = localNow
        return entity
    }

    private fun assertError(action: () -> Unit, expected: Any) {
        assertThatThrownBy(action)
            .isInstanceOfSatisfying(BusinessException::class.java) { exception ->
                assertThat(exception.errorCode).isSameAs(expected)
            }
    }
}
