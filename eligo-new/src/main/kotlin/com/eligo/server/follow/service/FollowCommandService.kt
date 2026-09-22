package com.eligo.server.follow.service

import com.eligo.server.follow.vo.FollowStateView
import com.eligo.server.security.UserPrincipal

interface FollowCommandService {

    fun followUser(principal: UserPrincipal, userId: Long): FollowStateView

    fun unfollowUser(principal: UserPrincipal, userId: Long): FollowStateView

    fun followOrganization(principal: UserPrincipal, organizationId: Long): FollowStateView

    fun unfollowOrganization(principal: UserPrincipal, organizationId: Long): FollowStateView

    fun getUserState(principal: UserPrincipal, userId: Long): FollowStateView

    fun getOrganizationState(principal: UserPrincipal, organizationId: Long): FollowStateView
}
