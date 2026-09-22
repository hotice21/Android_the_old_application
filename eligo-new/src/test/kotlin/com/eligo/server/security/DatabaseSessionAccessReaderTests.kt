package com.eligo.server.security

import com.eligo.server.account.entity.UserDeviceEntity
import com.eligo.server.account.entity.UserEntity
import com.eligo.server.account.entity.UserLoginSessionEntity
import com.eligo.server.account.mapper.UserDeviceMapper
import com.eligo.server.account.mapper.UserLoginSessionMapper
import com.eligo.server.account.mapper.UserMapper
import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.Optional

class DatabaseSessionAccessReaderTests {
    private val sessionMapper = mock(UserLoginSessionMapper::class.java)
    private val deviceMapper = mock(UserDeviceMapper::class.java)
    private val userMapper = mock(UserMapper::class.java)
    private val reader =
        DatabaseSessionAccessReader(sessionMapper, deviceMapper, userMapper)

    @Test
    fun activeSessionDeviceAndAccountAreAccepted() {
        prepare(1, 1)

        val state = reader.requireActive("session-a", 202L)

        assertThat(state).isEqualTo(SessionAccessState(401L, 301L, 1))
        verify(deviceMapper).touchLastSeenIfStale(301L)
    }

    @Test
    fun inactiveDeviceReturnsSessionInvalid() {
        prepare(2, 1)

        assertError(AccountUserFileErrorCode.LOGIN_SESSION_INVALID)
        verify(deviceMapper, never()).touchLastSeenIfStale(301L)
    }

    @Test
    fun securityDisabledAccountReturnsPublicAccessDenied() {
        prepare(1, 4)

        assertError(CommonErrorCode.ACCESS_DENIED)
        verify(deviceMapper, never()).touchLastSeenIfStale(301L)
    }

    @Test
    fun securityDisabledAccountTakesPriorityOverInvalidSession() {
        val user = UserEntity()
        user.id = 202L
        user.status = 4
        `when`(userMapper.selectById(202L)).thenReturn(user)
        `when`(sessionMapper.findActiveBySessionKey("session-a")).thenReturn(Optional.empty())

        assertError(CommonErrorCode.ACCESS_DENIED)

        verify(sessionMapper, never()).findActiveBySessionKey("session-a")
        verify(deviceMapper, never()).selectById(301L)
    }

    private fun prepare(deviceStatus: Int, accountStatus: Int) {
        val session = UserLoginSessionEntity()
        session.id = 401L
        session.userId = 202L
        session.deviceId = 301L
        `when`(sessionMapper.findActiveBySessionKey("session-a")).thenReturn(Optional.of(session))
        val device = UserDeviceEntity()
        device.id = 301L
        device.userId = 202L
        device.status = deviceStatus
        `when`(deviceMapper.selectById(301L)).thenReturn(device)
        val user = UserEntity()
        user.id = 202L
        user.status = accountStatus
        `when`(userMapper.selectById(202L)).thenReturn(user)
    }

    private fun assertError(errorCode: Any) {
        assertThatThrownBy { reader.requireActive("session-a", 202L) }
            .isInstanceOfSatisfying(BusinessException::class.java) { exception ->
                assertThat(exception.errorCode).isEqualTo(errorCode)
            }
    }
}
