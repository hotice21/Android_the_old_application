package com.eligo.server.chat.service

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.eligo.server.block.service.UserBlockService
import com.eligo.server.chat.dto.SendMessageRequest
import com.eligo.server.chat.entity.ChatConversationEntity
import com.eligo.server.chat.entity.ChatMessageEntity
import com.eligo.server.chat.mapper.ChatConversationMapper
import com.eligo.server.chat.mapper.ChatMessageMapper
import com.eligo.server.common.api.CursorPage
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.contentfilter.service.ContentFilterService
import com.eligo.server.friend.service.FriendService
import java.time.LocalDateTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DefaultChatService(
    private val conversations: ChatConversationMapper,
    private val messages: ChatMessageMapper,
    private val friendService: FriendService,
    private val blockService: UserBlockService,
    private val contentFilterService: ContentFilterService
) : ChatService {

    override fun getOrCreateConversation(
        myUserId: Long,
        otherUserId: Long
    ): ChatConversationEntity {
        val (userA, userB) = orderIds(myUserId, otherUserId)
        val existing = conversations.selectOne(
            QueryWrapper<ChatConversationEntity>()
                .eq("user_a_id", userA)
                .eq("user_b_id", userB)
        )
        if (existing != null) return existing

        val now = LocalDateTime.now()
        val entity = ChatConversationEntity().apply {
            this.userAId = userA
            this.userBId = userB
            this.userAUnreadCount = 0
            this.userBUnreadCount = 0
            this.createdAt = now
            this.updatedAt = now
        }
        conversations.insert(entity)
        return entity
    }

    override fun getMyConversations(myUserId: Long): List<ChatConversationEntity> {
        return conversations.selectList(
            QueryWrapper<ChatConversationEntity>()
                .eq("user_a_id", myUserId)
                .or()
                .eq("user_b_id", myUserId)
                .orderByDesc("updated_at")
        )
    }

    override fun getMessages(
        myUserId: Long,
        conversationId: Long,
        cursor: String?,
        limit: Int
    ): CursorPage<ChatMessageEntity> {
        val conversation = requireConversation(conversationId, myUserId)
        val query = if (cursor != null) {
            val cursorId = cursor.toLongOrNull()
                ?: throw BusinessException(CommonErrorCode.VALIDATION_FAILED)
            QueryWrapper<ChatMessageEntity>()
                .eq("conversation_id", conversation.id!!)
                .lt("id", cursorId)
                .orderByDesc("id")
                .last("LIMIT ${limit + 1}")
        } else {
            QueryWrapper<ChatMessageEntity>()
                .eq("conversation_id", conversation.id!!)
                .orderByDesc("id")
                .last("LIMIT ${limit + 1}")
        }
        val rows = messages.selectList(query)
        val hasMore = rows.size > limit
        val truncated = if (hasMore) rows.take(limit) else rows
        val nextCursor = if (truncated.isNotEmpty()) truncated.last().id?.toString() else null
        return CursorPage(truncated.reversed(), nextCursor, hasMore)
    }

    @Transactional
    override fun sendMessage(
        myUserId: Long,
        conversationId: Long,
        request: SendMessageRequest
    ): ChatMessageEntity {
        val conversation = requireConversation(conversationId, myUserId)
        val otherUserId = when {
            conversation.userAId == myUserId -> conversation.userBId!!
            conversation.userBId == myUserId -> conversation.userAId!!
            else -> throw BusinessException(CommonErrorCode.ACCESS_DENIED)
        }

        // 关键检查：好友关系 + 拉黑
        if (!friendService.isFriend(myUserId, otherUserId)) {
            throw BusinessException(CommonErrorCode.ACCESS_DENIED)
        }
        if (blockService.isBlockingUser(myUserId, otherUserId)) {
            throw BusinessException(CommonErrorCode.ACCESS_DENIED)
        }
        if (blockService.isBlockedByUser(myUserId, otherUserId)) {
            throw BusinessException(CommonErrorCode.ACCESS_DENIED)
        }

        val now = LocalDateTime.now()
        // messageType=1 为文本消息，进行敏感词过滤
        val filteredContent = when (request.messageType) {
            1 -> {
                if (!request.content.isNullOrBlank()) {
                    contentFilterService.filter(request.content).filtered
                } else {
                    request.content
                }
            }
            else -> request.content
        }
        val preview = when (request.messageType) {
            1 -> filteredContent?.take(200)
            2 -> "[图片]"
            3 -> "[语音]"
            else -> filteredContent?.take(200)
        }
        val message = ChatMessageEntity().apply {
            this.conversationId = conversation.id
            this.senderUserId = myUserId
            this.content = filteredContent
            this.messageType = request.messageType
            this.fileId = request.fileId
            this.voiceFileId = request.voiceFileId
            this.voiceDurationSeconds = request.voiceDurationSeconds
            this.status = 1
            this.sentAt = now
        }
        messages.insert(message)

        // 更新 conversation 的最后消息 + 对方未读数
        val updateEntity = ChatConversationEntity().apply {
            id = conversation.id
            lastMessagePreview = preview
            lastMessageType = request.messageType
            lastMessageAt = now
            updatedAt = now
        }
        if (conversation.userAId == myUserId) {
            updateEntity.userBUnreadCount = (conversation.userBUnreadCount ?: 0) + 1
        } else {
            updateEntity.userAUnreadCount = (conversation.userAUnreadCount ?: 0) + 1
        }
        conversations.updateById(updateEntity)

        return message
    }

    @Transactional
    override fun markRead(myUserId: Long, conversationId: Long) {
        val conversation = requireConversation(conversationId, myUserId)
        val updateEntity = ChatConversationEntity().apply {
            id = conversation.id
            updatedAt = LocalDateTime.now()
        }
        if (conversation.userAId == myUserId) {
            updateEntity.userAUnreadCount = 0
        } else {
            updateEntity.userBUnreadCount = 0
        }
        conversations.updateById(updateEntity)
    }

    private fun requireConversation(
        conversationId: Long,
        myUserId: Long
    ): ChatConversationEntity {
        val conversation = conversations.selectById(conversationId)
            ?: throw BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND)
        if (conversation.userAId != myUserId && conversation.userBId != myUserId) {
            throw BusinessException(CommonErrorCode.ACCESS_DENIED)
        }
        return conversation
    }

    private fun orderIds(a: Long, b: Long): Pair<Long, Long> {
        return if (a < b) a to b else b to a
    }
}
