package com.eligo.server.account.service

import com.eligo.server.account.entity.UserEntity
import com.eligo.server.account.mapper.UserMapper
import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test")
class AccountStateLockService(private val users: UserMapper) {

    @Transactional(propagation = Propagation.MANDATORY)
    fun lockActive(userId: Long) {
        val user = lock(userId)
        if (user.status == null || user.status != ACTIVE) {
            throw BusinessException(AccountUserFileErrorCode.ACCOUNT_CANCELLATION_CONFLICT)
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    fun lockExisting(userId: Long) {
        lock(userId)
    }

    private fun lock(userId: Long): UserEntity =
        users.lockById(userId).orElseThrow { BusinessException(AccountUserFileErrorCode.LOGIN_SESSION_INVALID) }

    companion object {
        private const val ACTIVE = 1
    }
}
