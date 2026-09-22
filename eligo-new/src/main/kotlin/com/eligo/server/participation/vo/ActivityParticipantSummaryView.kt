package com.eligo.server.participation.vo

import com.eligo.server.activity.vo.PublicImageView
import java.time.Instant

data class ActivityParticipantSummaryView(
    val userId: String?,
    val nickname: String?,
    val avatar: PublicImageView?,
    val joinedAt: Instant?
)
