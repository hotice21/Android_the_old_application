package com.eligo.server.account.service

import com.eligo.server.account.entity.AccountSecurityEventEntity
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
import org.mockito.ArgumentCaptor
import org.springframework.dao.DuplicateKeyException
import java.nio.charset.StandardCharsets
import java.sql.SQLIntegrityConstraintViolationException
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.mockito.kotlin.any
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class PhoneBindingServiceTests {

    companion object {
        private val CLOCK: Clock =
                Clock.fixed(Instant.parse("2026-07-22T08:00:00Z"), ZoneOffset.UTC)
        private val PHONE_HASH: ByteArray = "phone-hash".toByteArray(StandardCharsets.UTF_8)
    }

    private val bindings = mock(UserPhoneBindingMapper::class.java)
    private val users = mock(UserMapper::class.java)
    private val events = mock(AccountSecurityEventMapper::class.java)
    private val codec = mock(SensitiveDataCodec::class.java)
    private val profileCompletionUpdater = mock(ProfileCompletionUpdater::class.java)
    private lateinit var service: DefaultPhoneBindingTransactionService

    @BeforeEach
    fun setUp() {
        service = DefaultPhoneBindingTransactionService(
                        bindings,
                        users,
                        events,
                        codec,
                        PhoneMasker(),
                        PhoneLookupKey(),
                        profileCompletionUpdater,
                        CLOCK)
        `when`(codec.lookupHash("phone:86:13800121234")).thenReturn(PHONE_HASH)
        `when`(codec.encrypt("13800121234")).thenReturn("加密值")
        `when`(codec.decrypt("加密值")).thenReturn("13800121234")
        `when`(users.lockById(202L)).thenReturn(Optional.of(user(202L)))
    }

    @Test
    fun unboundQueryReturnsFalseAndNullDetails() {
        `when`(bindings.findActiveByUserId(202L)).thenReturn(Optional.empty())

        val result = service.current(202L)

        assertThat(result.bound).isFalse()
        assertThat(result.countryCode).isNull()
        assertThat(result.maskedPhone).isNull()
        assertThat(result.boundAt).isNull()
    }

    @Test
    fun firstBindingEncryptsPhoneAndStoresLookupHashAndLastFour() {
        `when`(bindings.findActiveByUserId(202L)).thenReturn(Optional.empty())
        `when`(bindings.findActiveByCountryCodeAndLookupHash("86", PHONE_HASH))
            .thenReturn(Optional.empty())

        val result = service.bindOrReplace(202L, AuthorizedPhone("86", "13800121234"))

        assertThat(result.bound).isTrue()
        assertThat(result.countryCode).isEqualTo("86")
        assertThat(result.maskedPhone).isEqualTo("138****1234")
        assertThat(result.boundAt).isEqualTo(Instant.parse("2026-07-22T08:00:00Z"))

        val saved = ArgumentCaptor.forClass(UserPhoneBindingEntity::class.java)
        verify(bindings).insert(saved.capture())
        assertThat(saved.value.phoneCiphertext)
            .containsExactly(*"加密值".toByteArray(StandardCharsets.UTF_8))
        assertThat(saved.value.phoneLookupHash).containsExactly(*PHONE_HASH)
        assertThat(saved.value.phoneLastFour).isEqualTo("1234")
        assertThat(saved.value.countryCode).isEqualTo("86")
        assertThat(saved.value.status).isEqualTo(1)
        verify(profileCompletionUpdater).recalculate(202L)
    }

    @Test
    fun bindingCurrentPhoneIsIdempotentWithoutMutationOrEvent() {
        val current = binding(301L, 202L, "86", "加密值", PHONE_HASH)
        `when`(bindings.findActiveByUserId(202L)).thenReturn(Optional.of(current))

        val result = service.bindOrReplace(202L, AuthorizedPhone("86", "13800121234"))

        assertThat(result.maskedPhone).isEqualTo("138****1234")
        verify(bindings, never()).markUnbound(any<Long>(), any(), any())
        verify(bindings, never()).insert(any<UserPhoneBindingEntity>())
        verify(events, never()).insert(any<AccountSecurityEventEntity>())
        verify(profileCompletionUpdater, never()).recalculate(any<Long>())
    }

    @Test
    fun replacementLocksUserThenReadsCurrentBindingBeforeUnbindingAndInserting() {
        val oldHash = "old-hash".toByteArray(StandardCharsets.UTF_8)
        val current = binding(301L, 202L, "86", "旧加密值", oldHash)
        `when`(bindings.findActiveByUserId(202L)).thenReturn(Optional.of(current))
        `when`(bindings.markUnbound(301L, "USER_REPLACED", LocalDateTime.parse("2026-07-22T08:00:00"))).thenReturn(1)
        `when`(bindings.findActiveByCountryCodeAndLookupHash("86", PHONE_HASH))
            .thenReturn(Optional.empty())

        service.bindOrReplace(202L, AuthorizedPhone("86", "13800121234"))

        val order = inOrder(users, bindings, events)
        order.verify(users).lockById(202L)
        order.verify(bindings).findActiveByUserId(202L)
        order.verify(bindings).findActiveByCountryCodeAndLookupHash("86", PHONE_HASH)
        order.verify(bindings)
                .markUnbound(
                        301L,
                        "USER_REPLACED",
                        LocalDateTime.parse("2026-07-22T08:00:00"))
        order.verify(bindings).insert(any<UserPhoneBindingEntity>())
        order.verify(events).insert(any<AccountSecurityEventEntity>())
    }

    @Test
    fun phoneOwnedByAnotherActiveUserReturnsStableConflict() {
        `when`(bindings.findActiveByUserId(202L)).thenReturn(Optional.empty())
        `when`(bindings.findActiveByCountryCodeAndLookupHash("86", PHONE_HASH))
            .thenReturn(Optional.of(binding(302L, 999L, "86", "他人密文", PHONE_HASH)))

        assertPhoneError(
                { service.bindOrReplace(202L, AuthorizedPhone("86", "13800121234")) },
                AccountUserFileErrorCode.PHONE_ALREADY_BOUND)
        verify(bindings, never()).insert(any<UserPhoneBindingEntity>())
        verify(events, never()).insert(any<AccountSecurityEventEntity>())
    }

    @Test
    fun uniqueIndexRaceIsConvertedToStableConflict() {
        `when`(bindings.findActiveByUserId(202L)).thenReturn(Optional.empty())
        `when`(bindings.findActiveByCountryCodeAndLookupHash("86", PHONE_HASH))
            .thenReturn(Optional.empty())
        `when`(bindings.insert(any<UserPhoneBindingEntity>()))
            .thenThrow(DuplicateKeyException(
                            "重复键",
                            SQLIntegrityConstraintViolationException(
                                    "Duplicate entry for key 'user_phone_bindings.uk_phone_active_number'",
                                    "23000",
                                    1062)))

        assertPhoneError(
                { service.bindOrReplace(202L, AuthorizedPhone("86", "13800121234")) },
                AccountUserFileErrorCode.PHONE_ALREADY_BOUND)
        verify(events, never()).insert(any<AccountSecurityEventEntity>())
    }

    @Test
    fun unbindUpdatesCurrentBindingAndRecordsMaskedSecurityEvent() {
        val current = binding(301L, 202L, "86", "加密值", PHONE_HASH)
        `when`(bindings.lockActiveByUserId(202L)).thenReturn(Optional.of(current))
        `when`(bindings.markUnbound(301L, "USER_UNBOUND", LocalDateTime.parse("2026-07-22T08:00:00"))).thenReturn(1)

        service.unbind(202L)

        verify(bindings)
                .markUnbound(
                        301L,
                        "USER_UNBOUND",
                        LocalDateTime.parse("2026-07-22T08:00:00"))
        val saved = ArgumentCaptor.forClass(AccountSecurityEventEntity::class.java)
        verify(events).insert(saved.capture())
        assertThat(saved.value.eventType).isEqualTo("PHONE_UNBOUND")
        assertThat(saved.value.detailJson).contains("138****1234")
        assertThat(saved.value.detailJson).doesNotContain("13800121234")
        assertThat(saved.value.detailJson).doesNotContain("phone-code")
        assertThat(saved.value.detailJson).doesNotContain("phone-hash")
        verify(profileCompletionUpdater).recalculate(202L)
    }

    @Test
    fun repeatedUnbindReturnsNotBound() {
        `when`(bindings.lockActiveByUserId(202L)).thenReturn(Optional.empty())

        assertPhoneError(
                { service.unbind(202L) }, AccountUserFileErrorCode.PHONE_NOT_BOUND)
        verify(events, never()).insert(any<AccountSecurityEventEntity>())
    }

    @Test
    fun bindingSecurityEventContainsOnlyActionAndMaskedPhone() {
        `when`(bindings.findActiveByUserId(202L)).thenReturn(Optional.empty())
        `when`(bindings.findActiveByCountryCodeAndLookupHash("86", PHONE_HASH))
            .thenReturn(Optional.empty())

        service.bindOrReplace(202L, AuthorizedPhone("86", "13800121234"))

        val saved = ArgumentCaptor.forClass(AccountSecurityEventEntity::class.java)
        verify(events).insert(saved.capture())
        assertThat(saved.value.eventType).isEqualTo("PHONE_BOUND")
        assertThat(saved.value.detailJson).isEqualTo("{\"maskedPhone\":\"138****1234\"}")
        assertThat(saved.value.detailJson).doesNotContain("13800121234")
        assertThat(saved.value.detailJson).doesNotContain("phone-code")
        assertThat(saved.value.detailJson).doesNotContain("phone-hash")
    }

    private fun user(id: Long): UserEntity {
        val user = UserEntity()
        user.id = id
        user.status = 1
        return user
    }

    private fun binding(
            id: Long,
            userId: Long,
            countryCode: String,
            ciphertext: String,
            lookupHash: ByteArray
    ): UserPhoneBindingEntity {
        val binding = UserPhoneBindingEntity()
        binding.id = id
        binding.userId = userId
        binding.countryCode = countryCode
        binding.phoneCiphertext = ciphertext.toByteArray(StandardCharsets.UTF_8)
        binding.phoneLookupHash = lookupHash
        binding.phoneLastFour = "1234"
        binding.status = 1
        binding.boundAt = LocalDateTime.parse("2026-07-20T08:00:00")
        return binding
    }

    private fun assertPhoneError(operation: () -> Unit, expected: AccountUserFileErrorCode) {
        assertThatThrownBy(operation)
            .isInstanceOfSatisfying(BusinessException::class.java) { exception ->
                assertThat(exception.errorCode).isEqualTo(expected)
            }
    }
}
