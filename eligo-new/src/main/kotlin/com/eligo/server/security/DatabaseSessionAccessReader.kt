package com.eligo.server.security

import com.eligo.server.account.mapper.UserDeviceMapper
import com.eligo.server.account.mapper.UserLoginSessionMapper
import com.eligo.server.account.mapper.UserMapper
import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Profile("!test")
class DatabaseSessionAccessReader(
    private val sessionMapper: UserLoginSessionMapper,
    private val deviceMapper: UserDeviceMapper,
    private val userMapper: UserMapper
) : SessionAccessReader {

    @Transactional
    override fun requireActive(sessionKey: String, userId: Long): SessionAccessState {
        val user = userMapper.selectById(userId) ?: throw sessionInvalid()
        if (user.status == 4) {
            throw BusinessException(CommonErrorCode.ACCESS_DENIED)
        }
        if (user.status != 1 && user.status != 2) {
            throw sessionInvalid()
        }
        val session = sessionMapper.findActiveBySessionKey(sessionKey)
            .filter { it.userId == userId }
            .orElseThrow { sessionInvalid() }
        val device = deviceMapper.selectById(session.deviceId) ?: throw sessionInvalid()
        if (device.status != 1 || device.userId != userId) {
            throw sessionInvalid()
        }
        deviceMapper.touchLastSeenIfStale(device.id!!)
        return SessionAccessState(session.id!!, device.id!!, user.status!!)
    }

    private fun sessionInvalid(): BusinessException =
        BusinessException(AccountUserFileErrorCode.LOGIN_SESSION_INVALID)
}
