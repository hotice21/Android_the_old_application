package com.eligo.server.block.controller

import com.eligo.server.block.entity.UserBlockEntity
import com.eligo.server.block.service.UserBlockService
import com.eligo.server.common.api.Result
import com.eligo.server.security.UserPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users/me/blocks")
class BlockController(
    private val blockService: UserBlockService
) {

    @PostMapping("/{targetUserId}")
    fun block(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable targetUserId: Long,
        @RequestParam(required = false) reason: String?
    ): Result<UserBlockEntity> {
        return Result.success(
            blockService.blockUser(principal.userId, targetUserId, reason)
        )
    }

    @DeleteMapping("/{targetUserId}")
    fun unblock(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable targetUserId: Long
    ): Result<Void> {
        blockService.unblockUser(principal.userId, targetUserId)
        return Result.success()
    }

    @GetMapping
    fun listBlocked(
        @AuthenticationPrincipal principal: UserPrincipal
    ): Result<List<UserBlockEntity>> {
        return Result.success(blockService.getMyBlockedList(principal.userId))
    }
}
