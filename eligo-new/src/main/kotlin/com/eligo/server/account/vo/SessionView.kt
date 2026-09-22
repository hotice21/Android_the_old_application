package com.eligo.server.account.vo

import java.time.Instant

data class SessionView(
    val sessionId: String?,
    val deviceName: String?,
    val platform: String?,
    val osVersion: String?,
    val appVersion: String?,
    val regionCode: String?,
    val lastActiveAt: Instant?,
    val expiresAt: Instant?,
    val current: Boolean
)
