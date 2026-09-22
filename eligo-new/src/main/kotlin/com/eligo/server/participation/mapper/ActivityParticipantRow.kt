package com.eligo.server.participation.mapper

import java.time.LocalDateTime

data class ActivityParticipantRow(
    val userId: Long? = null,
    val nickname: String? = null,
    val avatarFileId: Long? = null,
    val joinedAt: LocalDateTime? = null
)
