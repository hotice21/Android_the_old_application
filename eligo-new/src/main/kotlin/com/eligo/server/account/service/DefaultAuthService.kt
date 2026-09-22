package com.eligo.server.account.service

import com.eligo.server.account.dto.RefreshTokenRequest
import com.eligo.server.account.dto.WechatLoginRequest
import com.eligo.server.account.entity.AccountSecurityEventEntity
import com.eligo.server.account.entity.UserDeviceEntity
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
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.integration.wechat.WechatLoginClient
import com.eligo.server.integration.wechat.WechatProperties
import com.eligo.server.integration.wechat.WechatSession
import com.eligo.server.profile.service.ProfileCompletionReader
import com.eligo.server.security.JwtTokenService
import com.eligo.server.security.SensitiveDataCodec
import com.eligo.server.security.TokenPair
import com.eligo.server.security.UserPrincipal
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.springframework.context.annotation.Profile
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

@Service
@Profile("!test")
class DefaultAuthService(
    private val wechatProperties: WechatProperties,
    private val wechatLoginClient: WechatLoginClient,
    private val sensitiveDataCodec: SensitiveDataCodec,
    private val jwtTokenService: JwtTokenService,
    private val userMapper: UserMapper,
    private val wechatAccountMapper: UserWechatAccountMapper,
    private val deviceMapper: UserDeviceMapper,
    private val loginSessionMapper: UserLoginSessionMapper,
    private val securityEventMapper: AccountSecurityEventMapper,
    private val refreshReplayHandler: RefreshReplayHandler,
    private val refreshRotationHandler: RefreshRotationHandler,
    private val agreementService: AgreementService,
    private val profileCompletionReader: ProfileCompletionReader,
    transactionManager: PlatformTransactionManager
) : AuthService {

    private val loginTransactions: TransactionTemplate =
        TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }

    override fun login(request: WechatLoginRequest): LoginResponse {
        val wechat = wechatLoginClient.exchangeCode(request.wechatCode!!)
        val openidHash = sensitiveDataCodec.lookupHash("wechat-openid:" + wechat.openid)
        return try {
            executeLoginTransaction(request, wechat, openidHash)
        } catch (exception: WechatIdentityCreationRace) {
            try {
                executeLoginTransaction(request, wechat, openidHash)
            } catch (repeatedRace: WechatIdentityCreationRace) {
                throw identityConflict()
            }
        }
    }

    private fun executeLoginTransaction(
        request: WechatLoginRequest,
        wechat: WechatSession,
        openidHash: ByteArray
    ): LoginResponse =
        loginTransactions.execute { loginInTransaction(request, wechat, openidHash) }!!

    private fun loginInTransaction(
        request: WechatLoginRequest,
        wechat: WechatSession,
        openidHash: ByteArray
    ): LoginResponse {
        val binding = wechatAccountMapper.findActiveByAppIdAndOpenidHash(wechatProperties.appId, openidHash)
            .orElseGet { createUserAndBinding(wechat, openidHash) }
        val user = userMapper.lockById(binding.userId!!).orElseThrow { identityConflict() }
        requireUsableAccount(user)

        val installationHash = sensitiveDataCodec.lookupHash("installation:" + request.installationId)
        val device = registerOrActivateDevice(user.id!!, installationHash, request)
        loginSessionMapper.revokeActiveByDevice(device.id!!, "REPLACED_BY_LOGIN")

        val sessionKey = UUID.randomUUID().toString().replace("-", "")
        val tokenPair = jwtTokenService.issueTokenPair(UserPrincipal(user.id!!, sessionKey), 0)
        val session = UserLoginSessionEntity()
        val now = LocalDateTime.now(ZoneOffset.UTC)
        session.userId = user.id
        session.deviceId = device.id
        session.sessionKey = sessionKey
        session.refreshTokenHash = jwtTokenService.refreshTokenHash(tokenPair.refreshToken)
        session.refreshTokenVersion = 0
        session.status = ACTIVE
        session.expiresAt = LocalDateTime.ofInstant(tokenPair.refreshTokenExpiresAt, ZoneOffset.UTC)
        session.version = 0
        session.createdAt = now
        session.updatedAt = now
        loginSessionMapper.insert(session)
        userMapper.touchLogin(user.id!!)
        wechatAccountMapper.touchLogin(binding.id!!)
        return response(user, session, tokenPair)
    }

    override fun refresh(request: RefreshTokenRequest): LoginResponse {
        val tokenHash = jwtTokenService.refreshTokenHash(request.refreshToken)
        val claims = parseRefreshToken(request.refreshToken)
        val candidate = loginSessionMapper.findByRefreshTokenHash(tokenHash)
        if (candidate.isEmpty) {
            detectReplay(claims)
            throw refreshInvalid()
        }
        val result = refreshRotationHandler.rotate(request, tokenHash, claims, candidate.get())
        if (result.status == RefreshRotationResult.Status.SUCCESS) {
            return response(result.user!!, result.session!!, result.tokenPair!!)
        }
        if (detectReplay(claims)) {
            throw refreshInvalid()
        }
        throw sessionInvalid()
    }

    @Transactional
    override fun logout(principal: UserPrincipal) {
        val candidate = loginSessionMapper.findBySessionKey(principal.sessionKey)
            .filter { it.userId == principal.userId }
            .orElseThrow { sessionInvalid() }
        userMapper.lockById(principal.userId).orElseThrow { sessionInvalid() }
        val device = deviceMapper.lockOwnedById(candidate.deviceId!!, principal.userId)
            .filter { it.status == ACTIVE }
            .orElseThrow { sessionInvalid() }
        val session = loginSessionMapper.lockBySessionKey(principal.sessionKey)
            .filter { it.userId == principal.userId }
            .filter { it.deviceId == device.id }
            .filter { it.sessionKey == principal.sessionKey }
            .filter { it.status == ACTIVE }
            .filter { it.expiresAt!!.isAfter(LocalDateTime.now(ZoneOffset.UTC)) }
            .orElseThrow { sessionInvalid() }
        loginSessionMapper.revokeById(session.id!!, "USER_LOGOUT")
        deviceMapper.markUserLoggedOut(session.deviceId!!)
    }

    private fun createUserAndBinding(wechat: WechatSession, openidHash: ByteArray): UserWechatAccountEntity {
        val user = UserEntity()
        val now = LocalDateTime.now(ZoneOffset.UTC)
        user.status = ACTIVE
        user.version = 0
        user.createdAt = now
        user.updatedAt = now
        userMapper.insert(user)

        val binding = UserWechatAccountEntity()
        binding.userId = user.id
        binding.appId = wechatProperties.appId
        binding.openidCiphertext = sensitiveDataCodec.encrypt(wechat.openid).toByteArray(StandardCharsets.UTF_8)
        binding.openidLookupHash = openidHash
        if (wechat.unionid != null && !wechat.unionid.isBlank()) {
            binding.unionidCiphertext = sensitiveDataCodec.encrypt(wechat.unionid).toByteArray(StandardCharsets.UTF_8)
            binding.unionidLookupHash = sensitiveDataCodec.lookupHash("wechat-unionid:" + wechat.unionid)
        }
        binding.status = ACTIVE
        binding.boundAt = now
        binding.createdAt = now
        binding.updatedAt = now
        try {
            wechatAccountMapper.insert(binding)
        } catch (exception: DuplicateKeyException) {
            throw WechatIdentityCreationRace(exception)
        }
        userMapper.insertEmptyProfile(user.id!!)
        return binding
    }

    private fun registerOrActivateDevice(
        userId: Long,
        installationHash: ByteArray,
        request: WechatLoginRequest
    ): UserDeviceEntity {
        val existing = deviceMapper.findByUserIdAndInstallationHash(userId, installationHash)
        val active = deviceMapper.findActiveForUpdate(userId)
        val alreadyActive = existing.isPresent && active.any { it.id == existing.get().id }
        if (!alreadyActive && active.size >= 3) {
            val oldest = active[0]
            loginSessionMapper.revokeActiveByDevice(oldest.id!!, "EVICTED_BY_LIMIT")
            deviceMapper.markEvicted(oldest.id!!)
            recordEvent(userId, oldest.id, null, "DEVICE_EVICTED_BY_LIMIT", 2, oldest.regionCode)
        }
        if (existing.isPresent) {
            val device = existing.get()
            deviceMapper.activate(
                device.id!!, request.deviceName, request.platform, request.osVersion,
                request.appVersion, request.regionCode
            )
            device.status = ACTIVE
            device.deviceName = request.deviceName
            device.platformCode = request.platform
            device.osVersion = request.osVersion
            device.appVersion = request.appVersion
            device.regionCode = request.regionCode
            return device
        }
        val now = LocalDateTime.now(ZoneOffset.UTC)
        val device = UserDeviceEntity()
        device.userId = userId
        device.installationIdHash = installationHash
        device.deviceName = request.deviceName
        device.platformCode = request.platform
        device.osVersion = request.osVersion
        device.appVersion = request.appVersion
        device.regionCode = request.regionCode
        device.status = ACTIVE
        device.firstSeenAt = now
        device.lastSeenAt = now
        device.statusChangedAt = now
        device.createdAt = now
        device.updatedAt = now
        deviceMapper.insert(device)
        recordEvent(userId, device.id, null, "NEW_INSTALLATION_LOGIN", 1, request.regionCode)
        return device
    }

    private fun detectReplay(claims: JwtTokenService.RefreshTokenClaims): Boolean {
        val session = loginSessionMapper.findActiveBySessionKey(claims.sessionKey).orElse(null)
        if (session != null && session.status == ACTIVE &&
            claims.version < session.refreshTokenVersion!!
        ) {
            refreshReplayHandler.handleAuthenticatedReplay(
                RefreshReplayCandidate(
                    session.userId!!, session.deviceId!!, session.id!!,
                    session.sessionKey!!, claims.version
                )
            )
            return true
        }
        return false
    }

    private fun parseRefreshToken(refreshToken: String): JwtTokenService.RefreshTokenClaims {
        return try {
            jwtTokenService.parseRefreshToken(refreshToken)
        } catch (exception: IllegalArgumentException) {
            throw refreshInvalid()
        }
    }

    private fun requireUsableAccount(user: UserEntity) {
        if (user.status == SECURITY_DISABLED) {
            throw BusinessException(CommonErrorCode.ACCESS_DENIED)
        }
        if (user.status != ACTIVE && user.status != 2) {
            throw identityConflict()
        }
    }

    private fun response(
        user: UserEntity,
        session: UserLoginSessionEntity,
        tokenPair: TokenPair
    ): LoginResponse {
        return LoginResponse(
            user.id.toString(),
            accountStatus(user.status!!),
            session.id.toString(),
            tokenPair.accessToken,
            tokenPair.accessTokenExpiresAt,
            tokenPair.refreshToken,
            tokenPair.refreshTokenExpiresAt,
            profileCompletionReader.isCompleted(user.id!!),
            agreementService.pendingRequiredAgreementIds(user.id!!).map { it.toString() }
        )
    }

    private fun accountStatus(status: Int): String = when (status) {
        1 -> "ACTIVE"
        2 -> "DEACTIVATION_PENDING"
        3 -> "DEACTIVATED"
        4 -> "SECURITY_DISABLED"
        else -> throw identityConflict()
    }

    private fun recordEvent(
        userId: Long,
        deviceId: Long?,
        sessionId: Long?,
        type: String,
        severity: Int,
        regionCode: String?
    ) {
        val event = AccountSecurityEventEntity()
        val now = LocalDateTime.now(ZoneOffset.UTC)
        event.userId = userId
        event.deviceId = deviceId
        event.sessionId = sessionId
        event.eventType = type
        event.severity = severity
        event.regionCode = regionCode
        event.occurredAt = now
        event.createdAt = now
        securityEventMapper.insert(event)
    }

    private fun identityConflict(): BusinessException =
        BusinessException(AccountUserFileErrorCode.WECHAT_IDENTITY_CONFLICT)

    private fun refreshInvalid(): BusinessException =
        BusinessException(AccountUserFileErrorCode.REFRESH_TOKEN_INVALID)

    private fun sessionInvalid(): BusinessException =
        BusinessException(AccountUserFileErrorCode.LOGIN_SESSION_INVALID)

    private class WechatIdentityCreationRace(cause: DuplicateKeyException) :
        RuntimeException(cause)

    companion object {
        private const val ACTIVE = 1
        private const val SECURITY_DISABLED = 4
    }
}
