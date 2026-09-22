package com.eligo.server.account.service

import com.eligo.server.account.entity.AccountSecurityEventEntity
import com.eligo.server.account.entity.UserDeviceEntity
import com.eligo.server.account.entity.UserLoginSessionEntity
import com.eligo.server.account.mapper.AccountSecurityEventMapper
import com.eligo.server.account.mapper.UserDeviceMapper
import com.eligo.server.account.mapper.UserLoginSessionMapper
import com.eligo.server.account.mapper.UserMapper
import com.eligo.server.account.vo.SessionView
import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import com.eligo.server.security.UserPrincipal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test")
class DefaultSessionService(
    private val sessionMapper: UserLoginSessionMapper,
    private val deviceMapper: UserDeviceMapper,
    private val eventMapper: AccountSecurityEventMapper,
    private val userMapper: UserMapper
) : SessionService {

    @Transactional(readOnly = true)
    override fun listActive(principal: UserPrincipal): List<SessionView> =
        sessionMapper.findActiveByUserId(principal.userId).map { session ->
            val device = deviceMapper.selectById(session.deviceId)!!
            SessionView(
                session.id.toString(),
                device.deviceName,
                device.platformCode,
                device.osVersion,
                device.appVersion,
                device.regionCode,
                toInstant(device.lastSeenAt!!),
                toInstant(session.expiresAt!!),
                session.sessionKey == principal.sessionKey
            )
        }

    @Transactional
    override fun revokeOther(principal: UserPrincipal, sessionId: Long) {
        val candidate = sessionMapper.findOwnedById(sessionId, principal.userId)
            .orElseThrow { BusinessException(AccountUserFileErrorCode.DEVICE_OR_SESSION_NOT_FOUND) }
        userMapper.lockById(principal.userId)
            .orElseThrow { BusinessException(AccountUserFileErrorCode.DEVICE_OR_SESSION_NOT_FOUND) }
        val device = deviceMapper.lockOwnedById(candidate.deviceId!!, principal.userId)
            .filter { it.status == 1 }
            .orElseThrow { BusinessException(AccountUserFileErrorCode.DEVICE_OR_SESSION_NOT_FOUND) }
        val session = sessionMapper.lockOwnedById(sessionId, principal.userId)
            .filter { it.userId == principal.userId }
            .filter { it.deviceId == device.id }
            .filter { it.sessionKey == candidate.sessionKey }
            .filter { it.status == 1 }
            .filter { it.expiresAt!!.isAfter(LocalDateTime.now(ZoneOffset.UTC)) }
            .filter { it.sessionKey != principal.sessionKey }
            .orElseThrow { BusinessException(AccountUserFileErrorCode.DEVICE_OR_SESSION_NOT_FOUND) }
        sessionMapper.revokeById(session.id!!, "USER_REVOKED_OTHER")
        deviceMapper.markUserLoggedOut(session.deviceId!!)
        val event = AccountSecurityEventEntity()
        val now = LocalDateTime.now(ZoneOffset.UTC)
        event.userId = principal.userId
        event.deviceId = session.deviceId
        event.sessionId = session.id
        event.eventType = "USER_REVOKED_SESSION"
        event.severity = 1
        event.occurredAt = now
        event.createdAt = now
        eventMapper.insert(event)
    }

    private fun toInstant(time: LocalDateTime): Instant = time.toInstant(ZoneOffset.UTC)
}
