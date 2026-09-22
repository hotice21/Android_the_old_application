package com.eligo.server.account.service

import java.util.function.Function

import com.eligo.server.account.entity.UserEntity
import com.eligo.server.account.mapper.UserMapper
import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import java.util.Optional
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.assertj.core.api.Assertions.assertThatThrownBy

class AccountStateLockServiceTests {

    companion object {
        private const val USER_ID = 202L
    }

    private val users = mock(UserMapper::class.java)
    private val service = AccountStateLockService(users)

    @Test
    fun acceptsLockedActiveAccount() {
        `when`(users.lockById(USER_ID)).thenReturn(Optional.of(user(1)))

        service.lockActive(USER_ID)
    }

    @Test
    fun rejectsLockedDeactivationPendingAccount() {
        `when`(users.lockById(USER_ID)).thenReturn(Optional.of(user(2)))

        assertThatThrownBy { service.lockActive(USER_ID) }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  exception -> (exception as BusinessException).errorCode  })
            .isEqualTo(AccountUserFileErrorCode.ACCOUNT_CANCELLATION_CONFLICT)
    }

    @Test
    fun locksExistingAccountWithoutRestrictingCleanupByStatus() {
        `when`(users.lockById(USER_ID)).thenReturn(Optional.of(user(2)))

        service.lockExisting(USER_ID)

        verify(users).lockById(USER_ID)
    }

    private fun user(status: Int): UserEntity {
        val user = UserEntity()
        user.id = USER_ID
        user.status = status
        return user
    }
}
