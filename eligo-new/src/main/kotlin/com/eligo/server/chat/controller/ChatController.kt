package com.eligo.server.chat.controller

import com.eligo.server.chat.dto.SendMessageRequest
import com.eligo.server.chat.entity.ChatConversationEntity
import com.eligo.server.chat.entity.ChatMessageEntity
import com.eligo.server.chat.service.ChatService
import com.eligo.server.common.api.CursorPage
import com.eligo.server.common.api.Result
import com.eligo.server.security.UserPrincipal
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/chat")
class ChatController(
    private val chatService: ChatService
) {

    @GetMapping("/conversations")
    fun listConversations(
        @AuthenticationPrincipal principal: UserPrincipal
    ): Result<List<ChatConversationEntity>> {
        return Result.success(chatService.getMyConversations(principal.userId))
    }

    @GetMapping("/conversations/{convId}/messages")
    fun getMessages(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable convId: Long,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "50") limit: Int
    ): Result<CursorPage<ChatMessageEntity>> {
        return Result.success(
            chatService.getMessages(principal.userId, convId, cursor, limit)
        )
    }

    @PostMapping("/conversations/{convId}/messages")
    fun sendMessage(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable convId: Long,
        @Valid @RequestBody request: SendMessageRequest
    ): Result<ChatMessageEntity> {
        return Result.success(
            chatService.sendMessage(principal.userId, convId, request)
        )
    }

    @PutMapping("/conversations/{convId}/read")
    fun markRead(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable convId: Long
    ): Result<Void> {
        chatService.markRead(principal.userId, convId)
        return Result.success()
    }
}
