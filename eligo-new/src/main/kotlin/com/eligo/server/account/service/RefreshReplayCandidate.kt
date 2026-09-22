package com.eligo.server.account.service

data class RefreshReplayCandidate(
    val userId: Long,
    val deviceId: Long,
    val sessionId: Long,
    val sessionKey: String,
    val presentedVersion: Int
)
