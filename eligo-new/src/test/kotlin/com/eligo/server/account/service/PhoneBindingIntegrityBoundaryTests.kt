package com.eligo.server.account.service

import com.eligo.server.account.entity.UserEntity
import com.eligo.server.account.entity.UserPhoneBindingEntity
import com.eligo.server.account.mapper.AccountSecurityEventMapper
import com.eligo.server.account.mapper.UserMapper
import com.eligo.server.account.mapper.UserPhoneBindingMapper
import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import com.eligo.server.integration.wechat.AuthorizedPhone
import com.eligo.server.profile.service.ProfileCompletionUpdater
import com.eligo.server.security.SensitiveDataCodec
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.DuplicateKeyException
import java.sql.SQLIntegrityConstraintViolationException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.mockito.kotlin.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class PhoneBindingIntegrityBoundaryTests {

    private val bindings = mock(UserPhoneBindingMapper::class.java)
    private val users = mock(UserMapper::class.java)
    private val events = mock(AccountSecurityEventMapper::class.java)
    private val codec = mock(SensitiveDataCodec::class.java)
    private val masker = PhoneMasker()
    private val lookupKey = PhoneLookupKey()
    private lateinit var service: DefaultPhoneBindingTransactionService

    @BeforeEach
    fun setUp() {
        service = DefaultPhoneBindingTransactionService(
                        bindings,
                        users,
                        events,
                        codec,
                        masker,
                        lookupKey,
                        mock(ProfileCompletionUpdater::class.java),
                        Clock.fixed(Instant.parse("2026-07-22T08:00:00Z"), ZoneOffset.UTC))
        val user = UserEntity()
        user.id = 202L
        user.status = 1
        `when`(users.lockById(202L)).thenReturn(Optional.of(user))
        `when`(bindings.findActiveByUserId(202L)).thenReturn(Optional.empty())
        `when`(bindings.findActiveByCountryCodeAndLookupHash(any(), any()))
            .thenReturn(Optional.empty())
        `when`(codec.lookupHash("phone:86:13800121234")).thenReturn(ByteArray(32))
        `when`(codec.encrypt("13800121234")).thenReturn("加密值")
    }

    @Test
    fun targetPhoneNumberUniqueRaceIsConvertedToStableConflict() {
        `when`(bindings.insert(any<UserPhoneBindingEntity>()))
            .thenThrow(duplicate("uk_phone_active_number"))

        assertThatThrownBy { service.bindOrReplace(202L, AuthorizedPhone("86", "13800121234")) }
            .isInstanceOfSatisfying(BusinessException::class.java) { exception ->
                assertThat(exception.errorCode)
                    .isEqualTo(AccountUserFileErrorCode.PHONE_ALREADY_BOUND)
            }
    }

    @Test
    fun anotherUniqueConstraintViolationIsNotMisreportedAsPhoneOwnershipConflict() {
        val failure = duplicate("uk_phone_active_user")
        `when`(bindings.insert(any<UserPhoneBindingEntity>())).thenThrow(failure)

        assertThatThrownBy { service.bindOrReplace(202L, AuthorizedPhone("86", "13800121234")) }
            .isSameAs(failure)
    }

    @Test
    fun nonDuplicateIntegrityViolationIsNotMisreportedAsPhoneOwnershipConflict() {
        val failure = DataIntegrityViolationException("非空约束失败")
        `when`(bindings.insert(any<UserPhoneBindingEntity>())).thenThrow(failure)

        assertThatThrownBy { service.bindOrReplace(202L, AuthorizedPhone("86", "13800121234")) }
            .isSameAs(failure)
    }

    private fun duplicate(constraintName: String): DuplicateKeyException {
        return DuplicateKeyException(
                "重复键",
                SQLIntegrityConstraintViolationException(
                        "Duplicate entry for key 'user_phone_bindings."
                                + constraintName
                                + "'",
                        "23000",
                        1062))
    }
}
