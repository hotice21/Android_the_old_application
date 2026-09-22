package com.eligo.server.organization.seed

import com.eligo.server.account.service.AccountStateLockService
import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.jdbc.core.JdbcTemplate

class DevOrganizationSeedServiceTests {

    @Test
    fun inactiveOwnerIsRejectedBeforeSeedReadsOrWrites() {
        val jdbcTemplate = mock<JdbcTemplate>()
        val properties = DevOrganizationSeedProperties()
        val accountStates = mock<AccountStateLockService>()
        doThrow(BusinessException(AccountUserFileErrorCode.ACCOUNT_CANCELLATION_CONFLICT))
            .whenever(accountStates).lockActive(202L)
        val service = DevOrganizationSeedService(
            jdbcTemplate, properties, accountStates)

        assertThatThrownBy { service.seed(202L) }
            .isInstanceOfSatisfying(BusinessException::class.java) { exception ->
                assertThat(exception.errorCode)
                    .isEqualTo(AccountUserFileErrorCode.ACCOUNT_CANCELLATION_CONFLICT)
            }

        verify(accountStates).lockActive(202L)
        verifyNoInteractions(jdbcTemplate)
    }
}
