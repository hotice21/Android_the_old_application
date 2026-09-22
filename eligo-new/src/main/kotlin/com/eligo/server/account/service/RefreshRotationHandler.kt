package com.eligo.server.account.service

import com.eligo.server.account.dto.RefreshTokenRequest
import com.eligo.server.account.entity.UserLoginSessionEntity
import com.eligo.server.security.JwtTokenService

interface RefreshRotationHandler {
    fun rotate(
        request: RefreshTokenRequest,
        tokenHash: ByteArray,
        claims: JwtTokenService.RefreshTokenClaims,
        candidate: UserLoginSessionEntity
    ): RefreshRotationResult
}
