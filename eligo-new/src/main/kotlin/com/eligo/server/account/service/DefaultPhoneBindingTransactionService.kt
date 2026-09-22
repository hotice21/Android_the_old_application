package com.eligo.server.account.service

import com.eligo.server.account.entity.AccountSecurityEventEntity
import com.eligo.server.account.entity.UserPhoneBindingEntity
import com.eligo.server.account.mapper.AccountSecurityEventMapper
import com.eligo.server.account.mapper.UserMapper
import com.eligo.server.account.mapper.UserPhoneBindingMapper
import com.eligo.server.account.vo.PhoneBindingView
import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.integration.wechat.AuthorizedPhone
import com.eligo.server.profile.service.ProfileCompletionUpdater
import com.eligo.server.security.SensitiveDataCodec
import java.nio.charset.StandardCharsets
import java.sql.SQLException
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Arrays
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test")
class DefaultPhoneBindingTransactionService(
    private val bindings: UserPhoneBindingMapper,
    private val users: UserMapper,
    private val events: AccountSecurityEventMapper,
    private val codec: SensitiveDataCodec,
    private val masker: PhoneMasker,
    private val lookupKey: PhoneLookupKey,
    private val profileCompletionUpdater: ProfileCompletionUpdater,
    private val clock: Clock
) : PhoneBindingTransactionService {

    @Autowired
    constructor(
        bindings: UserPhoneBindingMapper,
        users: UserMapper,
        events: AccountSecurityEventMapper,
        codec: SensitiveDataCodec,
        masker: PhoneMasker,
        lookupKey: PhoneLookupKey,
        profileCompletionUpdater: ProfileCompletionUpdater
    ) : this(bindings, users, events, codec, masker, lookupKey, profileCompletionUpdater, Clock.systemUTC())

    @Transactional(readOnly = true)
    override fun current(userId: Long): PhoneBindingView =
        bindings.findActiveByUserId(userId)
            .map { view(it) }
            .orElseGet { PhoneBindingView.unbound() }

    @Transactional
    override fun bindOrReplace(userId: Long, phone: AuthorizedPhone): PhoneBindingView {
        val normalized = normalize(phone)
        val lookupHash = codec.lookupHash(
            lookupKey.canonical(normalized.countryCode, normalized.purePhoneNumber)
        )
        users.lockById(userId).orElseThrow { BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND) }
        val current = bindings.findActiveByUserId(userId).orElse(null)
        if (current != null && matches(current, normalized.countryCode, lookupHash)) {
            return view(current)
        }

        bindings.findActiveByCountryCodeAndLookupHash(normalized.countryCode, lookupHash)
            .filter { it.userId != userId }
            .ifPresent { throw phoneAlreadyBound() }

        val now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
        if (current != null && bindings.markUnbound(current.id!!, USER_REPLACED, now) != 1) {
            throw BusinessException(CommonErrorCode.CONFLICT)
        }

        val created = create(userId, normalized, lookupHash, now)
        try {
            bindings.insert(created)
        } catch (exception: DuplicateKeyException) {
            if (isActivePhoneNumberConflict(exception)) {
                throw phoneAlreadyBound()
            }
            throw exception
        }
        val maskedPhone = masker.mask(normalized.purePhoneNumber)
        recordEvent(userId, if (current == null) "PHONE_BOUND" else "PHONE_REBOUND", maskedPhone, now)
        profileCompletionUpdater.recalculate(userId)
        return PhoneBindingView(true, normalized.countryCode, maskedPhone, now.toInstant(ZoneOffset.UTC))
    }

    @Transactional
    override fun unbind(userId: Long) {
        users.lockById(userId).orElseThrow { BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND) }
        val current = bindings.lockActiveByUserId(userId)
            .orElseThrow { BusinessException(AccountUserFileErrorCode.PHONE_NOT_BOUND) }
        val now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
        if (bindings.markUnbound(current.id!!, USER_UNBOUND, now) != 1) {
            throw BusinessException(AccountUserFileErrorCode.PHONE_NOT_BOUND)
        }
        recordEvent(userId, "PHONE_UNBOUND", maskedPhone(current), now)
        profileCompletionUpdater.recalculate(userId)
    }

    private fun normalize(phone: AuthorizedPhone): AuthorizedPhone =
        AuthorizedPhone(
            lookupKey.normalizeCountryCode(phone.countryCode),
            lookupKey.normalizePhone(phone.purePhoneNumber)
        )

    private fun create(
        userId: Long,
        phone: AuthorizedPhone,
        lookupHash: ByteArray,
        now: LocalDateTime
    ): UserPhoneBindingEntity {
        val binding = UserPhoneBindingEntity()
        binding.userId = userId
        binding.countryCode = phone.countryCode
        binding.phoneCiphertext = codec.encrypt(phone.purePhoneNumber).toByteArray(StandardCharsets.UTF_8)
        binding.phoneLookupHash = lookupHash
        binding.phoneLastFour = phone.purePhoneNumber.substring(phone.purePhoneNumber.length - 4)
        binding.status = ACTIVE
        binding.boundAt = now
        binding.createdAt = now
        binding.updatedAt = now
        return binding
    }

    private fun matches(current: UserPhoneBindingEntity, countryCode: String, lookupHash: ByteArray): Boolean =
        current.countryCode == countryCode &&
            Arrays.equals(current.phoneLookupHash, lookupHash)

    private fun view(binding: UserPhoneBindingEntity): PhoneBindingView =
        PhoneBindingView(true, binding.countryCode, maskedPhone(binding), binding.boundAt!!.toInstant(ZoneOffset.UTC))

    private fun maskedPhone(binding: UserPhoneBindingEntity): String {
        val plaintext = codec.decrypt(String(binding.phoneCiphertext!!, StandardCharsets.UTF_8))
        return masker.mask(plaintext)
    }

    private fun recordEvent(userId: Long, eventType: String, maskedPhone: String, now: LocalDateTime) {
        val event = AccountSecurityEventEntity()
        event.userId = userId
        event.eventType = eventType
        event.severity = 1
        event.detailJson = "{\"maskedPhone\":\"$maskedPhone\"}"
        event.occurredAt = now
        event.createdAt = now
        events.insert(event)
    }

    private fun isActivePhoneNumberConflict(exception: DuplicateKeyException): Boolean {
        var current: Throwable? = exception
        while (current != null) {
            val node = current
            if (node is SQLException && node.errorCode == 1062 &&
                "23000" == node.sqlState && containsConstraintName(node.message)
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun containsConstraintName(message: String?): Boolean =
        message != null && message.contains(ACTIVE_PHONE_CONSTRAINT)

    private fun phoneAlreadyBound(): BusinessException =
        BusinessException(AccountUserFileErrorCode.PHONE_ALREADY_BOUND)

    companion object {
        private const val ACTIVE = 1
        private const val USER_REPLACED = "USER_REPLACED"
        private const val USER_UNBOUND = "USER_UNBOUND"
        private const val ACTIVE_PHONE_CONSTRAINT = "uk_phone_active_number"
    }
}
