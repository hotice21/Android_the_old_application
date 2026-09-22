package com.eligo.server.account.service

import com.eligo.server.account.entity.AccountSecurityEventEntity
import com.eligo.server.account.entity.UserLoginSessionEntity
import com.eligo.server.account.mapper.AccountSecurityEventMapper
import com.eligo.server.account.mapper.UserDeviceMapper
import com.eligo.server.account.mapper.UserLoginSessionMapper
import com.eligo.server.account.mapper.UserMapper
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.mockito.kotlin.any
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class RefreshReplayHandlerTests {

    @Test
    fun revokesSessionAndRecordsSecurityEventInNewTransaction() {
        val sessionMapper = mock(UserLoginSessionMapper::class.java)
        val eventMapper = mock(AccountSecurityEventMapper::class.java)
        val userMapper = mock(UserMapper::class.java)
        val deviceMapper = mock(UserDeviceMapper::class.java)
        val session = activeSession()
        `when`(userMapper.lockById(202L)).thenReturn(Optional.of(user()))
        `when`(deviceMapper.lockOwnedById(301L, 202L)).thenReturn(Optional.of(device()))
        `when`(sessionMapper.lockBySessionKey("session-a")).thenReturn(Optional.of(session))
        val handler = DefaultRefreshReplayHandler(userMapper, deviceMapper, sessionMapper, eventMapper)

        handler.handleAuthenticatedReplay(candidate())

        verify(sessionMapper).revokeById(401L, "REFRESH_TOKEN_REPLAY")
        verify(eventMapper).insert(any<AccountSecurityEventEntity>())
        val order = inOrder(userMapper, deviceMapper, sessionMapper)
        order.verify(userMapper).lockById(202L)
        order.verify(deviceMapper).lockOwnedById(301L, 202L)
        order.verify(sessionMapper).lockBySessionKey("session-a")
        val transactional = DefaultRefreshReplayHandler::class.java
            .getMethod("handleAuthenticatedReplay", RefreshReplayCandidate::class.java)
            .getAnnotation(Transactional::class.java)
        assertThat(transactional).isNotNull()
        assertThat(transactional.propagation).isEqualTo(Propagation.REQUIRES_NEW)
    }

    @Test
    fun changedFinalSessionReturnsWithoutRevocationOrEvent() {
        val sessionMapper = mock(UserLoginSessionMapper::class.java)
        val eventMapper = mock(AccountSecurityEventMapper::class.java)
        val userMapper = mock(UserMapper::class.java)
        val deviceMapper = mock(UserDeviceMapper::class.java)
        `when`(userMapper.lockById(202L)).thenReturn(Optional.of(user()))
        `when`(deviceMapper.lockOwnedById(301L, 202L)).thenReturn(Optional.of(device()))
        val changed = activeSession()
        changed.deviceId = 999L
        `when`(sessionMapper.lockBySessionKey("session-a")).thenReturn(Optional.of(changed))
        val handler = DefaultRefreshReplayHandler(userMapper, deviceMapper, sessionMapper, eventMapper)

        handler.handleAuthenticatedReplay(candidate())

        verify(sessionMapper, never()).revokeById(any<Long>(), any())
        verify(eventMapper, never()).insert(any<AccountSecurityEventEntity>())
    }

    private fun candidate(): RefreshReplayCandidate {
        return RefreshReplayCandidate(202L, 301L, 401L, "session-a", 1)
    }

    private fun user(): com.eligo.server.account.entity.UserEntity {
        val user = com.eligo.server.account.entity.UserEntity()
        user.id = 202L
        user.status = 1
        return user
    }

    private fun device(): com.eligo.server.account.entity.UserDeviceEntity {
        val device = com.eligo.server.account.entity.UserDeviceEntity()
        device.id = 301L
        device.userId = 202L
        device.status = 1
        return device
    }

    private fun activeSession(): UserLoginSessionEntity {
        val session = UserLoginSessionEntity()
        session.id = 401L
        session.userId = 202L
        session.deviceId = 301L
        session.sessionKey = "session-a"
        session.refreshTokenVersion = 2
        session.status = 1
        session.expiresAt = LocalDateTime.now().plusDays(1)
        return session
    }
}
