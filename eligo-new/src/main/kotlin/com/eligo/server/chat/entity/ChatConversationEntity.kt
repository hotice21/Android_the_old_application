package com.eligo.server.chat.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

@TableName("chat_conversations")
class ChatConversationEntity {
    @TableId(type = IdType.ASSIGN_ID)
    var id: Long? = null
    var userAId: Long? = null
    var userBId: Long? = null
    var lastMessagePreview: String? = null
    var lastMessageType: Int? = null
    var lastMessageAt: LocalDateTime? = null
    var userAUnreadCount: Int? = null
    var userBUnreadCount: Int? = null
    var createdAt: LocalDateTime? = null
    var updatedAt: LocalDateTime? = null
}
