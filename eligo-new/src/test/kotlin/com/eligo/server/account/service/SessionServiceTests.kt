package com.eligo.server.account.service

import java.util.function.Function

import com.eligo.server.account.entity.UserDeviceEntity
import com.eligo.server.account.entity.UserLoginSessionEntity
import com.eligo.server.account.entity.AccountSecurityEventEntity
import com.eligo.server.account.mapper.AccountSecurityEventMapper
import com.eligo.server.account.mapper.UserDeviceMapper
import com.eligo.server.account.mapper.UserLoginSessionMapper
import com.eligo.server.account.mapper.UserMapper
import com.eligo.server.account.vo.SessionView
import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import com.eligo.server.security.UserPrincipal
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.mockito.kotlin.any
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class SessionServiceTests {
    private val sessionMapper = mock(UserLoginSessionMapper::class.java)
    private val deviceMapper = mock(UserDeviceMapper::class.java)
    private val eventMapper = mock(AccountSecurityEventMapper::class.java)
    private val userMapper = mock(UserMapper::class.java)
    private val service = DefaultSessionService(sessionMapper, deviceMapper, eventMapper, userMapper)
    private val principal = UserPrincipal(202L, "session-current")

    @Test
    fun listMarksOnlyCurrentSessionAndReturnsDeviceSummary() {
        `when`(sessionMapper.findActiveByUserId(202L)).thenReturn(listOf(
                session(401L, 301L, "session-current"), session(402L, 302L, "session-other")))
        `when`(deviceMapper.selectById(301L)).thenReturn(device(301L, "当前设备"))
        `when`(deviceMapper.selectById(302L)).thenReturn(device(302L, "其他设备"))

        val result = service.listActive(principal)

        assertThat(result).extracting(Function { it.current }).containsExactly(true, false)
        assertThat(result).extracting(Function { it.deviceName }).containsExactly("当前设备", "其他设备")
    }

    @Test
    fun revokeOtherSessionRevokesSessionDeviceAndRecordsEvent() {
        `when`(sessionMapper.findOwnedById(402L, 202L)).thenReturn(Optional.of(session(402L, 302L, "session-other")))
        `when`(userMapper.lockById(202L)).thenReturn(Optional.of(user()))
        `when`(deviceMapper.lockOwnedById(302L, 202L)).thenReturn(Optional.of(device(302L, "其他设备")))
        `when`(sessionMapper.lockOwnedById(402L, 202L)).thenReturn(Optional.of(session(402L, 302L, "session-other")))

        service.revokeOther(principal, 402L)

        verify(sessionMapper).revokeById(402L, "USER_REVOKED_OTHER")
        verify(deviceMapper).markUserLoggedOut(302L)
        verify(eventMapper).insert(any<AccountSecurityEventEntity>())
        val order = inOrder(userMapper, deviceMapper, sessionMapper)
        order.verify(userMapper).lockById(202L)
        order.verify(deviceMapper).lockOwnedById(302L, 202L)
        order.verify(sessionMapper).lockOwnedById(402L, 202L)
    }

    @Test
    fun currentOrForeignSessionIsHiddenAsNotFound() {
        `when`(sessionMapper.findOwnedById(401L, 202L)).thenReturn(Optional.of(session(401L, 301L, "session-current")))
        `when`(userMapper.lockById(202L)).thenReturn(Optional.of(user()))
        `when`(deviceMapper.lockOwnedById(301L, 202L)).thenReturn(Optional.of(device(301L, "当前设备")))
        `when`(sessionMapper.lockOwnedById(401L, 202L)).thenReturn(Optional.of(session(401L, 301L, "session-current")))
        `when`(sessionMapper.findOwnedById(999L, 202L)).thenReturn(Optional.empty())

        assertNotFound(401L)
        assertNotFound(999L)
    }

    @Test
    fun changedSessionIdentityAfterCandidateReadIsHiddenAsNotFound() {
        `when`(sessionMapper.findOwnedById(402L, 202L))
                .thenReturn(Optional.of(session(402L, 302L, "session-other")))
        `when`(userMapper.lockById(202L)).thenReturn(Optional.of(user()))
        `when`(deviceMapper.lockOwnedById(302L, 202L)).thenReturn(Optional.of(device(302L, "其他设备")))
        `when`(sessionMapper.lockOwnedById(402L, 202L))
                .thenReturn(Optional.of(session(402L, 302L, "session-replaced")))

        assertNotFound(402L)
    }

    private fun assertNotFound(sessionId: Long) {
        assertThatThrownBy { service.revokeOther(principal, sessionId) }
            .isInstanceOfSatisfying(BusinessException::class.java) { exception ->
                assertThat(exception.errorCode).isEqualTo(AccountUserFileErrorCode.DEVICE_OR_SESSION_NOT_FOUND)
            }
    }

    private fun session(id: Long, deviceId: Long, key: String): UserLoginSessionEntity {
        val session = UserLoginSessionEntity()
        session.id = id
        session.userId = 202L
        session.deviceId = deviceId
        session.sessionKey = key
        session.status = 1
        session.expiresAt = LocalDateTime.now().plusDays(1)
        return session
    }

    private fun device(id: Long, name: String): UserDeviceEntity {
        val device = UserDeviceEntity()
        device.id = id
        device.userId = 202L
        device.deviceName = name
        device.platformCode = "WECHAT_MINIPROGRAM"
        device.appVersion = "2.3.0"
        device.lastSeenAt = LocalDateTime.now()
        device.status = 1
        return device
    }

    private fun user(): com.eligo.server.account.entity.UserEntity {
        val user = com.eligo.server.account.entity.UserEntity()
        user.id = 202L
        user.status = 1
        return user
    }
}
