package com.eligo.server.security

import java.time.Instant

data class TokenPair(
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshToken: String,
    val refreshTokenExpiresAt: Instant
)
