package com.eligo.server.follow.service

import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.follow.entity.OrganizationFollowEntity
import com.eligo.server.follow.entity.UserFollowEntity
import com.eligo.server.follow.error.FollowErrorCode
import com.eligo.server.follow.mapper.OrganizationFollowMapper
import com.eligo.server.follow.mapper.UserFollowMapper
import com.eligo.server.follow.vo.FollowStateView
import com.eligo.server.organization.mapper.OrganizationMapper
import com.eligo.server.profile.mapper.UserProfileMapper
import com.eligo.server.profile.service.ProfileCompletionReader
import com.eligo.server.security.UserPrincipal
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import org.springframework.context.annotation.Profile
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test")
class DefaultFollowCommandService(
    private val userFollows: UserFollowMapper,
    private val organizationFollows: OrganizationFollowMapper,
    private val profiles: UserProfileMapper,
    private val organizations: OrganizationMapper,
    private val completion: ProfileCompletionReader,
    private val clock: Clock = Clock.systemUTC()
) : FollowCommandService {

    @Transactional
    override fun followUser(principal: UserPrincipal, userId: Long): FollowStateView {
        requirePrincipalAndId(principal, userId)
        requirePublicUser(userId)
        if (principal.userId == userId) {
            throw BusinessException(FollowErrorCode.CANNOT_FOLLOW_SELF)
        }
        val current = userFollows.find(principal.userId, userId)
        if (current.isPresent) {
            return view(current.get())
        }
        requireCompletedProfile(principal.userId)

        val created = UserFollowEntity()
        created.followerUserId = principal.userId
        created.followedUserId = userId
        created.followedAt = now()
        return try {
            if (userFollows.insert(created) != 1) {
                throw conflict()
            }
            view(created)
        } catch (exception: DuplicateKeyException) {
            userFollows.lockRelation(principal.userId, userId)
                .map { view(it) }
                .orElseThrow { exception }
        }
    }

    @Transactional
    override fun unfollowUser(principal: UserPrincipal, userId: Long): FollowStateView {
        requirePrincipalAndId(principal, userId)
        userFollows.deleteRelation(principal.userId, userId)
        return notFollowing()
    }

    @Transactional
    override fun followOrganization(principal: UserPrincipal, organizationId: Long): FollowStateView {
        requirePrincipalAndId(principal, organizationId)
        requirePublicOrganization(organizationId)
        val current = organizationFollows.find(principal.userId, organizationId)
        if (current.isPresent) {
            return view(current.get())
        }
        requireCompletedProfile(principal.userId)

        val created = OrganizationFollowEntity()
        created.followerUserId = principal.userId
        created.organizationId = organizationId
        created.followedAt = now()
        return try {
            if (organizationFollows.insert(created) != 1) {
                throw conflict()
            }
            view(created)
        } catch (exception: DuplicateKeyException) {
            organizationFollows.lockRelation(principal.userId, organizationId)
                .map { view(it) }
                .orElseThrow { exception }
        }
    }

    @Transactional
    override fun unfollowOrganization(principal: UserPrincipal, organizationId: Long): FollowStateView {
        requirePrincipalAndId(principal, organizationId)
        organizationFollows.deleteRelation(principal.userId, organizationId)
        return notFollowing()
    }

    @Transactional(readOnly = true)
    override fun getUserState(principal: UserPrincipal, userId: Long): FollowStateView {
        requirePrincipalAndId(principal, userId)
        requirePublicUser(userId)
        return userFollows.find(principal.userId, userId)
            .map { view(it) }
            .orElseGet { notFollowing() }
    }

    @Transactional(readOnly = true)
    override fun getOrganizationState(principal: UserPrincipal, organizationId: Long): FollowStateView {
        requirePrincipalAndId(principal, organizationId)
        requirePublicOrganization(organizationId)
        return organizationFollows.find(principal.userId, organizationId)
            .map { view(it) }
            .orElseGet { notFollowing() }
    }

    private fun requirePublicUser(userId: Long) {
        if (!profiles.existsActiveCompletedUser(userId)) {
            throw notFound()
        }
    }

    private fun requirePublicOrganization(organizationId: Long) {
        organizations.findActivePublicById(organizationId)
            .orElseThrow(::notFound)
    }

    private fun requireCompletedProfile(userId: Long) {
        if (!completion.isCompleted(userId)) {
            throw BusinessException(AccountUserFileErrorCode.PROFILE_INCOMPLETE)
        }
    }

    private fun now(): LocalDateTime =
        LocalDateTime.ofInstant(clock.instant().truncatedTo(ChronoUnit.MILLIS), ZoneOffset.UTC)

    private fun view(entity: UserFollowEntity): FollowStateView =
        FollowStateView(true, entity.followedAt!!.toInstant(ZoneOffset.UTC))

    private fun view(entity: OrganizationFollowEntity): FollowStateView =
        FollowStateView(true, entity.followedAt!!.toInstant(ZoneOffset.UTC))

    private fun notFollowing(): FollowStateView = FollowStateView(false, null)

    private fun notFound(): BusinessException = BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND)

    private fun conflict(): BusinessException = BusinessException(CommonErrorCode.CONFLICT)

    companion object {
        private fun requirePrincipalAndId(principal: UserPrincipal?, targetId: Long) {
            if (principal == null || principal.userId <= 0 || targetId <= 0) {
                throw BusinessException(CommonErrorCode.VALIDATION_FAILED)
            }
        }
    }
}
