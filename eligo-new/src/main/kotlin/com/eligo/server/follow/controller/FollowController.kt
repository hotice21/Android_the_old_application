package com.eligo.server.follow.controller

import com.eligo.server.common.api.CursorPage
import com.eligo.server.common.api.Result
import com.eligo.server.follow.service.FollowCommandService
import com.eligo.server.follow.service.FollowReadService
import com.eligo.server.follow.vo.FollowStateView
import com.eligo.server.follow.vo.FollowTargetSummaryView
import com.eligo.server.follow.vo.FollowerSummaryView
import com.eligo.server.follow.vo.PublicUserProfileView
import com.eligo.server.security.UserPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class FollowController(
    private val commands: FollowCommandService,
    private val reads: FollowReadService
) {

    @GetMapping("/api/v1/users/{userId}/profile")
    fun publicUserProfile(@PathVariable userId: Long): Result<PublicUserProfileView?> =
        Result.success(reads.getPublicUserProfile(userId))

    @PutMapping("/api/v1/follows/users/{userId}")
    fun followUser(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable userId: Long
    ): Result<FollowStateView?> = Result.success(commands.followUser(principal, userId))

    @DeleteMapping("/api/v1/follows/users/{userId}")
    fun unfollowUser(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable userId: Long
    ): Result<FollowStateView?> = Result.success(commands.unfollowUser(principal, userId))

    @PutMapping("/api/v1/follows/organizations/{organizationId}")
    fun followOrganization(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable organizationId: Long
    ): Result<FollowStateView?> = Result.success(commands.followOrganization(principal, organizationId))

    @DeleteMapping("/api/v1/follows/organizations/{organizationId}")
    fun unfollowOrganization(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable organizationId: Long
    ): Result<FollowStateView?> = Result.success(commands.unfollowOrganization(principal, organizationId))

    @GetMapping("/api/v1/users/{userId}/follow-state")
    fun userState(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable userId: Long
    ): Result<FollowStateView?> = Result.success(commands.getUserState(principal, userId))

    @GetMapping("/api/v1/organizations/{organizationId}/follow-state")
    fun organizationState(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable organizationId: Long
    ): Result<FollowStateView?> = Result.success(commands.getOrganizationState(principal, organizationId))

    @GetMapping("/api/v1/users/me/following")
    fun following(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(required = false) type: String?,
        @RequestParam(name = "q", required = false) keyword: String?,
        @RequestParam(required = false) sort: String?
    ): Result<CursorPage<FollowTargetSummaryView>?> =
        Result.success(reads.listFollowing(principal, cursor, limit, type, keyword, sort))

    @GetMapping("/api/v1/users/me/followers")
    fun followers(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(name = "q", required = false) keyword: String?,
        @RequestParam(required = false) sort: String?
    ): Result<CursorPage<FollowerSummaryView>?> =
        Result.success(reads.listFollowers(principal, cursor, limit, keyword, sort))
}
