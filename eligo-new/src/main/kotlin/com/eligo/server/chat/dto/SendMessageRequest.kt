package com.eligo.server.chat.dto

import jakarta.validation.constraints.NotNull

data class SendMessageRequest(
    val content: String? = null,
    @field:NotNull val messageType: Int,
    val fileId: Long? = null,
    val voiceFileId: Long? = null,
    val voiceDurationSeconds: Int? = null
)
