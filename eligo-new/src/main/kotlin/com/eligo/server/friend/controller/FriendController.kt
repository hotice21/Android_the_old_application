package com.eligo.server.friend.controller

import com.eligo.server.common.api.Result
import com.eligo.server.follow.entity.UserFollowEntity
import com.eligo.server.friend.service.FriendService
import com.eligo.server.security.UserPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class FriendController(
    private val friendService: FriendService
) {

    data class RemarkRequest(val remarkName: String? = null)

    @PostMapping("/friends/{targetUserId}/request")
    fun sendFriendRequest(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable targetUserId: Long
    ): Result<UserFollowEntity> {
        return Result.success(
            friendService.sendFriendRequest(principal.userId, targetUserId)
        )
    }

    @PutMapping("/friend-requests/{requestId}/accept")
    fun acceptFriendRequest(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable requestId: Long
    ): Result<UserFollowEntity> {
        return Result.success(
            friendService.acceptFriendRequest(principal.userId, requestId)
        )
    }

    @PutMapping("/friend-requests/{requestId}/reject")
    fun rejectFriendRequest(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable requestId: Long
    ): Result<UserFollowEntity> {
        return Result.success(
            friendService.rejectFriendRequest(principal.userId, requestId)
        )
    }

    @DeleteMapping("/friends/{friendUserId}")
    fun unfriend(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable friendUserId: Long
    ): Result<Void> {
        friendService.unfriend(principal.userId, friendUserId)
        return Result.success()
    }

    @GetMapping("/users/me/friends")
    fun getMyFriends(
        @AuthenticationPrincipal principal: UserPrincipal
    ): Result<List<UserFollowEntity>> {
        return Result.success(friendService.getMyFriends(principal.userId))
    }

    @GetMapping("/users/me/friend-requests")
    fun getPendingRequests(
        @AuthenticationPrincipal principal: UserPrincipal
    ): Result<List<UserFollowEntity>> {
        return Result.success(friendService.getPendingRequests(principal.userId))
    }

    @PutMapping("/friends/{friendUserId}/remark")
    fun updateRemark(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable friendUserId: Long,
        @RequestBody request: RemarkRequest
    ): Result<Void> {
        friendService.updateRemark(principal.userId, friendUserId, request.remarkName)
        return Result.success()
    }
}
