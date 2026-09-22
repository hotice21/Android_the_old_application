package com.eligo.server.account.service

import com.eligo.server.account.dto.RefreshTokenRequest
import com.eligo.server.account.entity.UserDeviceEntity
import com.eligo.server.account.entity.UserEntity
import com.eligo.server.account.entity.UserLoginSessionEntity
import com.eligo.server.account.mapper.UserDeviceMapper
import com.eligo.server.account.mapper.UserLoginSessionMapper
import com.eligo.server.account.mapper.UserMapper
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.security.JwtTokenService
import com.eligo.server.security.SensitiveDataCodec
import com.eligo.server.security.TokenPair
import com.eligo.server.security.UserPrincipal
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test")
class DefaultRefreshRotationHandler(
    private val sensitiveDataCodec: SensitiveDataCodec,
    private val jwtTokenService: JwtTokenService,
    private val userMapper: UserMapper,
    private val deviceMapper: UserDeviceMapper,
    private val sessionMapper: UserLoginSessionMapper
) : RefreshRotationHandler {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun rotate(
        request: RefreshTokenRequest,
        tokenHash: ByteArray,
        claims: JwtTokenService.RefreshTokenClaims,
        candidate: UserLoginSessionEntity
    ): RefreshRotationResult {
        val user = userMapper.lockById(candidate.userId!!).orElse(null)
            ?: return RefreshRotationResult.staleOrInvalid()
        if (user.status == 4) {
            throw BusinessException(CommonErrorCode.ACCESS_DENIED)
        }
        if (user.status != ACTIVE && user.status != 2) {
            return RefreshRotationResult.staleOrInvalid()
        }
        val device = deviceMapper.lockOwnedById(candidate.deviceId!!, candidate.userId!!).orElse(null)
            ?: return RefreshRotationResult.staleOrInvalid()
        if (device.status != ACTIVE) {
            return RefreshRotationResult.staleOrInvalid()
        }
        val session = sessionMapper.lockByIdAndRefreshTokenHash(candidate.id!!, tokenHash).orElse(null)
        val now = LocalDateTime.now(ZoneOffset.UTC)
        if (session == null || !isStillUsable(session, candidate, claims, device, now)) {
            return RefreshRotationResult.staleOrInvalid()
        }
        val installationHash = sensitiveDataCodec.lookupHash("installation:" + request.installationId)
        if (!MessageDigest.isEqual(device.installationIdHash, installationHash)) {
            return RefreshRotationResult.staleOrInvalid()
        }
        val nextVersion = session.refreshTokenVersion!! + 1
        val tokenPair: TokenPair = jwtTokenService.issueTokenPair(
            UserPrincipal(user.id!!, session.sessionKey!!), nextVersion
        )
        val expiresAt = LocalDateTime.ofInstant(tokenPair.refreshTokenExpiresAt, ZoneOffset.UTC)
        val updated = sessionMapper.rotateRefreshToken(
            session.id!!, session.refreshTokenVersion!!,
            jwtTokenService.refreshTokenHash(tokenPair.refreshToken), expiresAt
        )
        if (updated != 1) {
            return RefreshRotationResult.staleOrInvalid()
        }
        deviceMapper.touchLastSeenIfStale(device.id!!)
        session.refreshTokenVersion = nextVersion
        session.expiresAt = expiresAt
        return RefreshRotationResult.success(user, session, tokenPair)
    }

    private fun isStillUsable(
        session: UserLoginSessionEntity,
        candidate: UserLoginSessionEntity,
        claims: JwtTokenService.RefreshTokenClaims,
        device: UserDeviceEntity,
        now: LocalDateTime
    ): Boolean {
        return session.userId == candidate.userId &&
            session.deviceId == candidate.deviceId &&
            device.userId == session.userId &&
            session.status == ACTIVE &&
            session.expiresAt!!.isAfter(now) &&
            session.sessionKey == claims.sessionKey &&
            session.refreshTokenVersion == claims.version
    }

    companion object {
        private const val ACTIVE = 1
    }
}
