package com.eligo.server.account.service

import com.eligo.server.account.dto.RefreshTokenRequest
import com.eligo.server.account.entity.UserDeviceEntity
import com.eligo.server.account.entity.UserEntity
import com.eligo.server.account.entity.UserLoginSessionEntity
import com.eligo.server.account.mapper.UserDeviceMapper
import com.eligo.server.account.mapper.UserLoginSessionMapper
import com.eligo.server.account.mapper.UserMapper
import com.eligo.server.security.JwtTokenService
import com.eligo.server.security.SensitiveDataCodec
import com.eligo.server.security.TokenPair
import org.junit.jupiter.api.Test
import org.mockito.InOrder
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDateTime
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class RefreshRotationHandlerTests {
    private val codec = mock(SensitiveDataCodec::class.java)
    private val tokenService = mock(JwtTokenService::class.java)
    private val userMapper = mock(UserMapper::class.java)
    private val deviceMapper = mock(UserDeviceMapper::class.java)
    private val sessionMapper = mock(UserLoginSessionMapper::class.java)

    @Test
    fun locksUserThenDeviceThenSessionAndRotatesInNewTransaction() {
        val digest = ByteArray(32)
        val candidate = session()
        val user = user()
        val device = device()
        `when`(userMapper.lockById(202L)).thenReturn(Optional.of(user))
        `when`(deviceMapper.lockOwnedById(301L, 202L)).thenReturn(Optional.of(device))
        `when`(sessionMapper.lockByIdAndRefreshTokenHash(401L, digest)).thenReturn(Optional.of(candidate))
        `when`(codec.lookupHash("installation:installation-0001")).thenReturn(device.installationIdHash)
        `when`(tokenService.issueTokenPair(any(), eq(1))).thenReturn(tokenPair())
        `when`(sessionMapper.rotateRefreshToken(eq(401L), eq(0), anyOrNull(), anyOrNull())).thenReturn(1)
        val handler = handler()

        val result = handler.rotate(
                RefreshTokenRequest("refresh-token", "installation-0001"), digest,
                JwtTokenService.RefreshTokenClaims("session-a", 0), candidate)

        assertThat(result.status).isEqualTo(RefreshRotationResult.Status.SUCCESS)
        val order: InOrder = inOrder(userMapper, deviceMapper, sessionMapper)
        order.verify(userMapper).lockById(202L)
        order.verify(deviceMapper).lockOwnedById(301L, 202L)
        order.verify(sessionMapper).lockByIdAndRefreshTokenHash(401L, digest)
        val annotation = DefaultRefreshRotationHandler::class.java
                .getMethod("rotate", RefreshTokenRequest::class.java, ByteArray::class.java,
                        JwtTokenService.RefreshTokenClaims::class.java, UserLoginSessionEntity::class.java)
                .getAnnotation(Transactional::class.java)
        assertThat(annotation.propagation).isEqualTo(Propagation.REQUIRES_NEW)
    }

    @Test
    fun missingFinalSessionReturnsStaleWithoutCallingReplayHandlerOrTakingReverseLocks() {
        val digest = ByteArray(32)
        val candidate = session()
        `when`(userMapper.lockById(202L)).thenReturn(Optional.of(user()))
        `when`(deviceMapper.lockOwnedById(301L, 202L)).thenReturn(Optional.of(device()))
        `when`(sessionMapper.lockByIdAndRefreshTokenHash(401L, digest)).thenReturn(Optional.empty())

        val result = handler().rotate(
                RefreshTokenRequest("refresh-token", "installation-0001"), digest,
                JwtTokenService.RefreshTokenClaims("session-a", 0), candidate)

        assertThat(result.status).isEqualTo(RefreshRotationResult.Status.STALE_OR_INVALID)
        verify(sessionMapper, never()).rotateRefreshToken(any<Long>(), any<Int>(), any(), any())
    }

    private fun handler(): DefaultRefreshRotationHandler {
        return DefaultRefreshRotationHandler(codec, tokenService, userMapper, deviceMapper, sessionMapper)
    }

    private fun user(): UserEntity {
        val user = UserEntity()
        user.id = 202L
        user.status = 1
        return user
    }

    private fun device(): UserDeviceEntity {
        val device = UserDeviceEntity()
        device.id = 301L
        device.userId = 202L
        device.status = 1
        device.installationIdHash = ByteArray(32)
        return device
    }

    private fun session(): UserLoginSessionEntity {
        val session = UserLoginSessionEntity()
        session.id = 401L
        session.userId = 202L
        session.deviceId = 301L
        session.sessionKey = "session-a"
        session.refreshTokenVersion = 0
        session.status = 1
        session.expiresAt = LocalDateTime.now().plusDays(1)
        return session
    }

    private fun tokenPair(): TokenPair {
        return TokenPair("access", Instant.now().plusSeconds(900),
                "refresh-new", Instant.now().plusSeconds(3600))
    }
}
