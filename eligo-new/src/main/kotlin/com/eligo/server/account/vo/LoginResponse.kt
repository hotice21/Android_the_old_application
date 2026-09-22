package com.eligo.server.account.vo

import java.time.Instant

data class LoginResponse(
    val userId: String?,
    val accountStatus: String?,
    val sessionId: String?,
    val accessToken: String?,
    val accessTokenExpiresAt: Instant?,
    val refreshToken: String?,
    val refreshTokenExpiresAt: Instant?,
    val profileCompleted: Boolean,
    val pendingAgreementIds: List<String>?
)
