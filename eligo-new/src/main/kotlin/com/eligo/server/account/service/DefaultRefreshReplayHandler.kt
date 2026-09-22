package com.eligo.server.account.service

import com.eligo.server.account.entity.AccountSecurityEventEntity
import com.eligo.server.account.entity.UserLoginSessionEntity
import com.eligo.server.account.mapper.AccountSecurityEventMapper
import com.eligo.server.account.mapper.UserDeviceMapper
import com.eligo.server.account.mapper.UserLoginSessionMapper
import com.eligo.server.account.mapper.UserMapper
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test")
class DefaultRefreshReplayHandler(
    private val userMapper: UserMapper,
    private val deviceMapper: UserDeviceMapper,
    private val sessionMapper: UserLoginSessionMapper,
    private val securityEventMapper: AccountSecurityEventMapper
) : RefreshReplayHandler {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun handleAuthenticatedReplay(candidate: RefreshReplayCandidate) {
        if (userMapper.lockById(candidate.userId).isEmpty) {
            return
        }
        if (deviceMapper.lockOwnedById(candidate.deviceId, candidate.userId)
                .filter { it.status == ACTIVE }.isEmpty
        ) {
            return
        }
        val session: UserLoginSessionEntity? = sessionMapper.lockBySessionKey(candidate.sessionKey).orElse(null)
        val now = LocalDateTime.now(ZoneOffset.UTC)
        if (session == null ||
            session.id != candidate.sessionId ||
            session.userId != candidate.userId ||
            session.deviceId != candidate.deviceId ||
            session.sessionKey != candidate.sessionKey ||
            session.status != ACTIVE ||
            !session.expiresAt!!.isAfter(now) ||
            candidate.presentedVersion >= session.refreshTokenVersion!!
        ) {
            return
        }
        sessionMapper.revokeById(session.id!!, "REFRESH_TOKEN_REPLAY")
        val event = AccountSecurityEventEntity()
        event.userId = session.userId
        event.deviceId = session.deviceId
        event.sessionId = session.id
        event.eventType = "REFRESH_TOKEN_REPLAY"
        event.severity = 3
        event.occurredAt = now
        event.createdAt = now
        securityEventMapper.insert(event)
    }

    companion object {
        private const val ACTIVE = 1
    }
}
