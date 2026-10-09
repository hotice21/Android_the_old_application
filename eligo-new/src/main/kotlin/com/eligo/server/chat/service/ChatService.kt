package com.eligo.server.chat.service

import com.eligo.server.chat.dto.SendMessageRequest
import com.eligo.server.chat.entity.ChatConversationEntity
import com.eligo.server.chat.entity.ChatMessageEntity
import com.eligo.server.common.api.CursorPage

interface ChatService {
    fun getOrCreateConversation(myUserId: Long, otherUserId: Long): ChatConversationEntity
    fun getMyConversations(myUserId: Long): List<ChatConversationEntity>
    fun getMessages(
        myUserId: Long,
        conversationId: Long,
        cursor: String?,
        limit: Int
    ): CursorPage<ChatMessageEntity>
    fun sendMessage(
        myUserId: Long,
        conversationId: Long,
        request: SendMessageRequest
    ): ChatMessageEntity
    fun markRead(myUserId: Long, conversationId: Long)
}
