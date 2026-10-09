package com.eligo.server.chat.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

@TableName("chat_messages")
class ChatMessageEntity {
    @TableId(type = IdType.ASSIGN_ID)
    var id: Long? = null
    var conversationId: Long? = null
    var senderUserId: Long? = null
    var content: String? = null
    /** 1 文本 2 图片 3 语音 */
    var messageType: Int? = null
    var fileId: Long? = null
    var voiceFileId: Long? = null
    var voiceDurationSeconds: Int? = null
    /** 1 已发送 2 已读 */
    var status: Int? = null
    var sentAt: LocalDateTime? = null
}
