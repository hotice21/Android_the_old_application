package com.eligo.server.account.service

import com.eligo.server.account.dto.RefreshTokenRequest
import com.eligo.server.account.entity.UserLoginSessionEntity
import com.eligo.server.account.entity.AccountSecurityEventEntity
import com.eligo.server.account.mapper.AccountSecurityEventMapper
import com.eligo.server.account.mapper.UserDeviceMapper
import com.eligo.server.account.mapper.UserLoginSessionMapper
import com.eligo.server.account.mapper.UserMapper
import com.eligo.server.account.mapper.UserWechatAccountMapper
import com.eligo.server.agreement.service.AgreementService
import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import com.eligo.server.integration.wechat.WechatLoginClient
import com.eligo.server.integration.wechat.WechatProperties
import com.eligo.server.profile.service.ProfileCompletionReader
import com.eligo.server.security.JwtTokenService
import com.eligo.server.security.SensitiveDataCodec
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import java.time.LocalDateTime
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.mockito.kotlin.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class RefreshReplayProtectionTests {
    private val tokenService = mock(JwtTokenService::class.java)
    private val sessionMapper = mock(UserLoginSessionMapper::class.java)
    private val eventMapper = mock(AccountSecurityEventMapper::class.java)
    private val replayHandler = mock(RefreshReplayHandler::class.java)
    private val rotationHandler = mock(RefreshRotationHandler::class.java)
    private lateinit var authService: DefaultAuthService

    @BeforeEach
    fun setUp() {
        val codec = mock(SensitiveDataCodec::class.java)
        `when`(tokenService.refreshTokenHash(any())).thenReturn(ByteArray(32))
        authService = DefaultAuthService(
                WechatProperties("wx-app", "wx-secret", "https://api.weixin.qq.com"),
                mock(WechatLoginClient::class.java), codec, tokenService, mock(UserMapper::class.java),
                mock(UserWechatAccountMapper::class.java), mock(UserDeviceMapper::class.java), sessionMapper, eventMapper,
                replayHandler, rotationHandler, mock(AgreementService::class.java), mock(ProfileCompletionReader::class.java),
                mock(PlatformTransactionManager::class.java)
        )
    }

    @Test
    fun oldAuthenticatedVersionRevokesCurrentSessionAndRecordsEvent() {
        `when`(sessionMapper.findByRefreshTokenHash(any())).thenReturn(Optional.empty())
        `when`(tokenService.parseRefreshToken("old-token"))
            .thenReturn(JwtTokenService.RefreshTokenClaims("session-a", 1))
        `when`(sessionMapper.findActiveBySessionKey("session-a")).thenReturn(Optional.of(activeSession(2)))

        assertRefreshInvalid("old-token")

        verify(replayHandler).handleAuthenticatedReplay(
                RefreshReplayCandidate(202L, 301L, 401L, "session-a", 1))
        verify(sessionMapper, never()).lockBySessionKey("session-a")
    }

    @Test
    fun sameOrFutureVersionWithWrongDigestCannotRevokeSession() {
        `when`(sessionMapper.findByRefreshTokenHash(any())).thenReturn(Optional.empty())
        `when`(tokenService.parseRefreshToken("same-version"))
            .thenReturn(JwtTokenService.RefreshTokenClaims("session-a", 2))
        `when`(tokenService.parseRefreshToken("future-version"))
            .thenReturn(JwtTokenService.RefreshTokenClaims("session-a", 3))
        `when`(sessionMapper.findActiveBySessionKey("session-a")).thenReturn(Optional.of(activeSession(2)))

        assertRefreshInvalid("same-version")
        assertRefreshInvalid("future-version")

        verify(sessionMapper, never()).revokeById(any<Long>(), any())
        verify(eventMapper, never()).insert(any<AccountSecurityEventEntity>())
        verify(replayHandler, never()).handleAuthenticatedReplay(any())
    }

    @Test
    fun malformedTokenDoesNotLeakOrRevokeSession() {
        `when`(sessionMapper.findByRefreshTokenHash(any())).thenReturn(Optional.empty())
        `when`(tokenService.parseRefreshToken("malformed-secret"))
            .thenThrow(IllegalArgumentException("刷新令牌格式无效"))

        assertThatThrownBy { authService.refresh(RefreshTokenRequest("malformed-secret", "installation-0001")) }
            .isInstanceOfSatisfying(BusinessException::class.java) { exception ->
                assertThat(exception.errorCode).isEqualTo(AccountUserFileErrorCode.REFRESH_TOKEN_INVALID)
                assertThat(exception).hasMessageNotContaining("malformed-secret")
            }
        verify(sessionMapper, never()).revokeById(any<Long>(), any())
        verify(replayHandler, never()).handleAuthenticatedReplay(any())
    }

    private fun assertRefreshInvalid(token: String) {
        assertThatThrownBy { authService.refresh(RefreshTokenRequest(token, "installation-0001")) }
            .isInstanceOfSatisfying(BusinessException::class.java) { exception ->
                assertThat(exception.errorCode).isEqualTo(AccountUserFileErrorCode.REFRESH_TOKEN_INVALID)
            }
    }

    private fun activeSession(refreshVersion: Int): UserLoginSessionEntity {
        val session = UserLoginSessionEntity()
        session.id = 401L
        session.userId = 202L
        session.deviceId = 301L
        session.sessionKey = "session-a"
        session.refreshTokenVersion = refreshVersion
        session.status = 1
        session.expiresAt = LocalDateTime.now().plusDays(1)
        return session
    }
}
