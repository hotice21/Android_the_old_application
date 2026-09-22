package com.eligo.server.account.service

import com.eligo.server.account.dto.RefreshTokenRequest
import com.eligo.server.account.dto.WechatLoginRequest
import com.eligo.server.account.entity.UserDeviceEntity
import com.eligo.server.account.entity.AccountSecurityEventEntity
import com.eligo.server.account.entity.UserEntity
import com.eligo.server.account.entity.UserLoginSessionEntity
import com.eligo.server.account.entity.UserWechatAccountEntity
import com.eligo.server.account.mapper.AccountSecurityEventMapper
import com.eligo.server.account.mapper.UserDeviceMapper
import com.eligo.server.account.mapper.UserLoginSessionMapper
import com.eligo.server.account.mapper.UserMapper
import com.eligo.server.account.mapper.UserWechatAccountMapper
import com.eligo.server.account.vo.LoginResponse
import com.eligo.server.agreement.service.AgreementService
import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import com.eligo.server.integration.wechat.WechatLoginClient
import com.eligo.server.integration.wechat.WechatProperties
import com.eligo.server.integration.wechat.WechatSession
import com.eligo.server.profile.service.ProfileCompletionReader
import com.eligo.server.security.JwtTokenService
import com.eligo.server.security.SensitiveDataCodec
import com.eligo.server.security.TokenPair
import com.eligo.server.security.UserPrincipal
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.dao.DuplicateKeyException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.SimpleTransactionStatus
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDateTime
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class AuthServiceTests {

    private val wechatLoginClient = mock(WechatLoginClient::class.java)
    private val sensitiveDataCodec = mock(SensitiveDataCodec::class.java)
    private val jwtTokenService = mock(JwtTokenService::class.java)
    private val userMapper = mock(UserMapper::class.java)
    private val wechatAccountMapper = mock(UserWechatAccountMapper::class.java)
    private val deviceMapper = mock(UserDeviceMapper::class.java)
    private val loginSessionMapper = mock(UserLoginSessionMapper::class.java)
    private val securityEventMapper = mock(AccountSecurityEventMapper::class.java)
    private val refreshReplayHandler = mock(RefreshReplayHandler::class.java)
    private val refreshRotationHandler = mock(RefreshRotationHandler::class.java)
    private val agreementService = mock(AgreementService::class.java)
    private val profileCompletionReader = mock(ProfileCompletionReader::class.java)
    private val transactionManager = mock(PlatformTransactionManager::class.java)

    private lateinit var authService: DefaultAuthService

    @BeforeEach
    fun setUp() {
        `when`(transactionManager.getTransaction(any<TransactionDefinition>()))
            .thenAnswer { SimpleTransactionStatus() }
        val properties = WechatProperties("wx-app", "wx-secret", "https://api.weixin.qq.com")
        authService = DefaultAuthService(
                properties,
                wechatLoginClient,
                sensitiveDataCodec,
                jwtTokenService,
                userMapper,
                wechatAccountMapper,
                deviceMapper,
                loginSessionMapper,
                securityEventMapper,
                refreshReplayHandler,
                refreshRotationHandler,
                agreementService,
                profileCompletionReader,
                transactionManager
        )
        `when`(wechatLoginClient.exchangeCode(any())).thenReturn(WechatSession("openid-a", "unionid-a", "session-value"))
        `when`(sensitiveDataCodec.encrypt(any())).thenAnswer { invocation -> "cipher:" + invocation.getArgument(0) }
        `when`(sensitiveDataCodec.lookupHash(any())).thenAnswer { invocation ->
            fixedHash(invocation.getArgument(0, String::class.java))
        }
        `when`(jwtTokenService.issueTokenPair(any<UserPrincipal>(), any<Int>()))
            .thenReturn(tokenPair("access-a", "refresh-token-a"))
        `when`(jwtTokenService.refreshTokenHash(any())).thenReturn("hash".toByteArray())
        assignIdentifiers()
        `when`(agreementService.pendingRequiredAgreementIds(any<Long>())).thenReturn(listOf(501L, 502L))
        `when`(profileCompletionReader.isCompleted(any<Long>())).thenReturn(false)
    }

    @Test
    fun refreshOrchestratorDoesNotOpenATransaction() {
        assertThat(DefaultAuthService::class.java.getMethod("refresh", RefreshTokenRequest::class.java)
            .getAnnotation(Transactional::class.java)).isNull()
    }

    @Test
    fun loginOrchestratorDoesNotOpenATransaction() {
        assertThat(DefaultAuthService::class.java.getMethod("login", WechatLoginRequest::class.java)
            .getAnnotation(Transactional::class.java)).isNull()
    }

    @Test
    fun firstWechatLoginCreatesUserBindingDeviceAndSession() {
        `when`(wechatAccountMapper.findActiveByAppIdAndOpenidHash(eq("wx-app"), any())).thenReturn(Optional.empty())
        `when`(deviceMapper.findByUserIdAndInstallationHash(eq(101L), any())).thenReturn(Optional.empty())
        `when`(deviceMapper.findActiveForUpdate(101L)).thenReturn(listOf())
        `when`(userMapper.lockById(101L)).thenReturn(Optional.of(activeUser(101L)))

        val response = authService.login(loginRequest("installation-a"))

        assertThat(response.userId).isEqualTo("101")
        assertThat(response.sessionId).isEqualTo("401")
        assertThat(response.accessToken).isEqualTo("access-a")
        assertThat(response.refreshToken).isEqualTo("refresh-token-a")
        assertThat(response.pendingAgreementIds).containsExactly("501", "502")
        verify(userMapper).insertEmptyProfile(101L)
    }

    @Test
    fun loginReturnsPersistedProfileCompletionState() {
        prepareExistingUser(202L)
        `when`(deviceMapper.findByUserIdAndInstallationHash(eq(202L), any())).thenReturn(Optional.empty())
        `when`(deviceMapper.findActiveForUpdate(202L)).thenReturn(listOf())
        `when`(profileCompletionReader.isCompleted(202L)).thenReturn(true)

        val response = authService.login(loginRequest("installation-a"))

        assertThat(response.profileCompleted).isTrue()
        verify(profileCompletionReader).isCompleted(202L)
    }

    @Test
    fun concurrentFirstLoginRetriesWithOrdinaryLookupWithoutHoldingIdentityLock() {
        val winner = binding(202L)
        `when`(wechatAccountMapper.findActiveByAppIdAndOpenidHash(eq("wx-app"), any()))
            .thenReturn(Optional.empty(), Optional.of(winner))
        `when`(wechatAccountMapper.lockActiveByAppIdAndOpenidHash(eq("wx-app"), any()))
            .thenReturn(Optional.of(winner))
        `when`(wechatAccountMapper.insert(any<UserWechatAccountEntity>()))
            .thenThrow(DuplicateKeyException("并发唯一约束冲突"))
        `when`(userMapper.lockById(202L)).thenReturn(Optional.of(activeUser(202L)))
        `when`(deviceMapper.findByUserIdAndInstallationHash(eq(202L), any())).thenReturn(Optional.empty())
        `when`(deviceMapper.findActiveForUpdate(202L)).thenReturn(listOf())

        val response = authService.login(loginRequest("installation-a"))

        assertThat(response.userId).isEqualTo("202")
        verify(wechatLoginClient).exchangeCode("wechat-code")
        verify(wechatAccountMapper, times(2))
            .findActiveByAppIdAndOpenidHash(eq("wx-app"), any())
        verify(userMapper, never()).deleteById(101L)
        verify(wechatAccountMapper, never())
            .lockActiveByAppIdAndOpenidHash(eq("wx-app"), any())
        verify(transactionManager, times(2))
            .getTransaction(any<TransactionDefinition>())
        verify(transactionManager).rollback(any<TransactionStatus>())
        verify(transactionManager).commit(any<TransactionStatus>())
    }

    @Test
    fun repeatedIdentityCreationRaceReturnsStableWechatConflict() {
        `when`(wechatAccountMapper.findActiveByAppIdAndOpenidHash(eq("wx-app"), any()))
            .thenReturn(Optional.empty(), Optional.empty())
        `when`(wechatAccountMapper.insert(any<UserWechatAccountEntity>()))
            .thenThrow(DuplicateKeyException("持续身份唯一键冲突"))

        assertThatThrownBy { authService.login(loginRequest("installation-a")) }
            .isInstanceOfSatisfying(BusinessException::class.java) { exception ->
                assertThat(exception.errorCode)
                    .isEqualTo(AccountUserFileErrorCode.WECHAT_IDENTITY_CONFLICT)
            }

        verify(wechatLoginClient).exchangeCode("wechat-code")
        verify(wechatAccountMapper, times(2))
            .findActiveByAppIdAndOpenidHash(eq("wx-app"), any())
        verify(wechatAccountMapper, never())
            .lockActiveByAppIdAndOpenidHash(eq("wx-app"), any())
        verify(transactionManager, times(2))
            .getTransaction(any<TransactionDefinition>())
        verify(transactionManager, times(2)).rollback(any<TransactionStatus>())
        verify(transactionManager, never()).commit(any<TransactionStatus>())
    }

    @Test
    fun nonIdentityDuplicateDoesNotRetryWholeLogin() {
        prepareExistingUser(202L)
        `when`(deviceMapper.findByUserIdAndInstallationHash(eq(202L), any()))
            .thenReturn(Optional.empty())
        `when`(deviceMapper.findActiveForUpdate(202L)).thenReturn(listOf())
        val duplicate = DuplicateKeyException("设备唯一键冲突")
        `when`(deviceMapper.insert(any<UserDeviceEntity>())).thenThrow(duplicate)

        assertThatThrownBy { authService.login(loginRequest("installation-a")) }
            .isSameAs(duplicate)

        verify(wechatAccountMapper).findActiveByAppIdAndOpenidHash(eq("wx-app"), any())
        verify(transactionManager).getTransaction(any<TransactionDefinition>())
        verify(transactionManager).rollback(any<TransactionStatus>())
        verify(transactionManager, never()).commit(any<TransactionStatus>())
    }

    @Test
    fun repeatedLoginReusesDeviceAndRevokesPreviousSession() {
        val existingDevice = device(301L, LocalDateTime.now().minusHours(1))
        prepareExistingUser(202L)
        `when`(deviceMapper.findByUserIdAndInstallationHash(eq(202L), any())).thenReturn(Optional.of(existingDevice))
        `when`(deviceMapper.findActiveForUpdate(202L)).thenReturn(listOf(existingDevice))

        authService.login(loginRequest("installation-a"))

        verify(loginSessionMapper).revokeActiveByDevice(301L, "REPLACED_BY_LOGIN")
        verify(deviceMapper, never()).insert(any<UserDeviceEntity>())
        val order = inOrder(userMapper, deviceMapper, loginSessionMapper)
        order.verify(userMapper).lockById(202L)
        order.verify(deviceMapper).findActiveForUpdate(202L)
        order.verify(loginSessionMapper).revokeActiveByDevice(301L, "REPLACED_BY_LOGIN")
    }

    @Test
    fun fourthInstallationEvictsOldestActiveDevice() {
        prepareExistingUser(202L)
        val oldest = device(501L, LocalDateTime.now().minusDays(3))
        val middle = device(502L, LocalDateTime.now().minusDays(2))
        val newest = device(503L, LocalDateTime.now().minusDays(1))
        `when`(deviceMapper.findByUserIdAndInstallationHash(eq(202L), any())).thenReturn(Optional.empty())
        `when`(deviceMapper.findActiveForUpdate(202L)).thenReturn(listOf(oldest, middle, newest))

        authService.login(loginRequest("installation-d"))

        verify(loginSessionMapper).revokeActiveByDevice(501L, "EVICTED_BY_LIMIT")
        verify(deviceMapper).markEvicted(501L)
        verify(securityEventMapper, times(2)).insert(any<AccountSecurityEventEntity>())
    }

    @Test
    fun dormantExistingInstallationDoesNotOccupyActiveSessionSlot() {
        prepareExistingUser(202L)
        val dormant = device(301L, LocalDateTime.now().minusDays(4))
        val oldestActive = device(501L, LocalDateTime.now().minusDays(3))
        val middle = device(502L, LocalDateTime.now().minusDays(2))
        val newest = device(503L, LocalDateTime.now().minusDays(1))
        `when`(deviceMapper.findByUserIdAndInstallationHash(eq(202L), any())).thenReturn(Optional.of(dormant))
        `when`(deviceMapper.findActiveForUpdate(202L)).thenReturn(listOf(oldestActive, middle, newest))

        authService.login(loginRequest("installation-a"))

        verify(loginSessionMapper).revokeActiveByDevice(501L, "EVICTED_BY_LIMIT")
        verify(deviceMapper).markEvicted(501L)
        verify(deviceMapper).activate(eq(301L), any(), any(), any(), any(), any())
    }

    @Test
    fun refreshRotatesDigestAndTokenVersionOnce() {
        val session = activeSession(401L, 202L, 301L, "session-a", 0)
        `when`(loginSessionMapper.findByRefreshTokenHash(any())).thenReturn(Optional.of(session))
        `when`(jwtTokenService.parseRefreshToken("refresh-cipher-a"))
            .thenReturn(JwtTokenService.RefreshTokenClaims("session-a", 0))
        `when`(refreshRotationHandler.rotate(any(), any(), any(), eq(session)))
            .thenReturn(RefreshRotationResult.success(
                    activeUser(202L), session, tokenPair("access-b", "refresh-token-b")))

        val response = authService.refresh(RefreshTokenRequest("refresh-cipher-a", "installation-a"))

        assertThat(response.accessToken).isEqualTo("access-b")
        assertThat(response.refreshToken).isNotEqualTo("refresh-cipher-a")
        assertThat(response.pendingAgreementIds).containsExactly("501", "502")
        verify(refreshRotationHandler).rotate(any(), any(), any(), eq(session))
    }

    @Test
    fun replayedOldRefreshTokenRevokesLocatedSessionAndRecordsSecurityEvent() {
        val session = activeSession(401L, 202L, 301L, "session-a", 2)
        `when`(loginSessionMapper.findByRefreshTokenHash(any())).thenReturn(Optional.empty())
        `when`(jwtTokenService.parseRefreshToken("old-refresh-cipher"))
            .thenReturn(JwtTokenService.RefreshTokenClaims("session-a", 1))
        `when`(loginSessionMapper.findActiveBySessionKey("session-a")).thenReturn(Optional.of(session))

        assertThatThrownBy { authService.refresh(RefreshTokenRequest("old-refresh-cipher", "installation-a")) }
            .isInstanceOfSatisfying(BusinessException::class.java) { exception ->
                assertThat(exception.errorCode).isEqualTo(AccountUserFileErrorCode.REFRESH_TOKEN_INVALID)
            }
        verify(refreshReplayHandler).handleAuthenticatedReplay(
                RefreshReplayCandidate(202L, 301L, 401L, "session-a", 1))
        verify(loginSessionMapper, never()).lockBySessionKey("session-a")
    }

    @Test
    fun staleRotationIsHandledOnlyAfterRotationTransactionReturns() {
        val candidate = activeSession(401L, 202L, 301L, "session-a", 1)
        val current = activeSession(401L, 202L, 301L, "session-a", 2)
        `when`(loginSessionMapper.findByRefreshTokenHash(any())).thenReturn(Optional.of(candidate))
        `when`(jwtTokenService.parseRefreshToken("stale-refresh-cipher"))
            .thenReturn(JwtTokenService.RefreshTokenClaims("session-a", 1))
        `when`(refreshRotationHandler.rotate(any(), any(), any(), eq(candidate)))
            .thenReturn(RefreshRotationResult.staleOrInvalid())
        `when`(loginSessionMapper.findActiveBySessionKey("session-a")).thenReturn(Optional.of(current))

        assertThatThrownBy { authService.refresh(
                RefreshTokenRequest("stale-refresh-cipher", "installation-a")) }
            .isInstanceOfSatisfying(BusinessException::class.java) { exception ->
                assertThat(exception.errorCode).isEqualTo(AccountUserFileErrorCode.REFRESH_TOKEN_INVALID)
            }

        val order = inOrder(refreshRotationHandler, refreshReplayHandler)
        order.verify(refreshRotationHandler).rotate(any(), any(), any(), eq(candidate))
        order.verify(refreshReplayHandler).handleAuthenticatedReplay(
                RefreshReplayCandidate(202L, 301L, 401L, "session-a", 1))
    }

    @Test
    fun logoutRevokesCurrentSessionAndMarksDeviceLoggedOut() {
        val session = activeSession(401L, 202L, 301L, "session-a", 0)
        val device = device(301L, LocalDateTime.now())
        `when`(loginSessionMapper.findBySessionKey("session-a")).thenReturn(Optional.of(session))
        `when`(userMapper.lockById(202L)).thenReturn(Optional.of(activeUser(202L)))
        `when`(deviceMapper.lockOwnedById(301L, 202L)).thenReturn(Optional.of(device))
        `when`(loginSessionMapper.lockBySessionKey("session-a")).thenReturn(Optional.of(session))

        authService.logout(UserPrincipal(202L, "session-a"))

        verify(loginSessionMapper).revokeById(401L, "USER_LOGOUT")
        verify(deviceMapper).markUserLoggedOut(301L)
        val order = inOrder(userMapper, deviceMapper, loginSessionMapper)
        order.verify(userMapper).lockById(202L)
        order.verify(deviceMapper).lockOwnedById(301L, 202L)
        order.verify(loginSessionMapper).lockBySessionKey("session-a")
    }

    private fun prepareExistingUser(userId: Long) {
        `when`(wechatAccountMapper.findActiveByAppIdAndOpenidHash(eq("wx-app"), any()))
            .thenReturn(Optional.of(binding(userId)))
        `when`(userMapper.lockById(userId)).thenReturn(Optional.of(activeUser(userId)))
    }

    private fun assignIdentifiers() {
        doAnswer { invocation ->
            (invocation.getArgument(0) as UserEntity).id = 101L
            1
        }.`when`(userMapper).insert(any<UserEntity>())
        doAnswer { invocation ->
            (invocation.getArgument(0) as UserWechatAccountEntity).id = 201L
            1
        }.`when`(wechatAccountMapper).insert(any<UserWechatAccountEntity>())
        doAnswer { invocation ->
            (invocation.getArgument(0) as UserDeviceEntity).id = 301L
            1
        }.`when`(deviceMapper).insert(any<UserDeviceEntity>())
        doAnswer { invocation ->
            (invocation.getArgument(0) as UserLoginSessionEntity).id = 401L
            1
        }.`when`(loginSessionMapper).insert(any<UserLoginSessionEntity>())
    }

    private fun loginRequest(installationId: String): WechatLoginRequest {
        return WechatLoginRequest(
                "wechat-code", installationId, "微信设备", "WECHAT_MINIPROGRAM",
                "18.0", "2.3.0", "CN-44"
        )
    }

    private fun activeUser(id: Long): UserEntity {
        val user = UserEntity()
        user.id = id
        user.status = 1
        return user
    }

    private fun binding(userId: Long): UserWechatAccountEntity {
        val binding = UserWechatAccountEntity()
        binding.id = 201L
        binding.userId = userId
        binding.status = 1
        return binding
    }

    private fun device(id: Long, lastSeenAt: LocalDateTime): UserDeviceEntity {
        val device = UserDeviceEntity()
        device.id = id
        device.userId = 202L
        device.installationIdHash = fixedHash("installation:installation-a")
        device.deviceName = "微信设备"
        device.platformCode = "WECHAT_MINIPROGRAM"
        device.appVersion = "2.3.0"
        device.status = 1
        device.lastSeenAt = lastSeenAt
        return device
    }

    private fun activeSession(
            id: Long, userId: Long, deviceId: Long, sessionKey: String, refreshVersion: Int
    ): UserLoginSessionEntity {
        val session = UserLoginSessionEntity()
        session.id = id
        session.userId = userId
        session.deviceId = deviceId
        session.sessionKey = sessionKey
        session.refreshTokenVersion = refreshVersion
        session.status = 1
        session.expiresAt = LocalDateTime.now().plusDays(1)
        return session
    }

    private fun tokenPair(accessToken: String, refreshToken: String): TokenPair {
        return TokenPair(
                accessToken,
                Instant.now().plusSeconds(900),
                refreshToken,
                Instant.now().plusSeconds(86400)
        )
    }

    private fun fixedHash(value: String): ByteArray {
        val source = value.toByteArray(StandardCharsets.UTF_8)
        val result = ByteArray(32)
        System.arraycopy(source, 0, result, 0, minOf(source.size, result.size))
        return result
    }
}
