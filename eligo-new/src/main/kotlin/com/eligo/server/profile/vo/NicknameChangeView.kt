package com.eligo.server.profile.vo

import java.time.Instant

data class NicknameChangeView(
    val nickname: String?,
    val nicknameChangedAt: Instant?,
    val nextNicknameChangeAt: Instant?
)
