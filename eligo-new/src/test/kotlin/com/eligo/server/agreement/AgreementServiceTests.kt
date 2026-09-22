package com.eligo.server.agreement

import java.util.function.Function

import java.util.function.Consumer

import com.eligo.server.agreement.entity.AgreementConsentEntity
import com.eligo.server.agreement.entity.AgreementEntity
import com.eligo.server.agreement.entity.AgreementType
import com.eligo.server.agreement.mapper.AgreementConsentMapper
import com.eligo.server.agreement.mapper.AgreementMapper
import com.eligo.server.agreement.service.DefaultAgreementService
import com.eligo.server.agreement.vo.AgreementConsentView
import com.eligo.server.agreement.vo.AgreementDetail
import com.eligo.server.agreement.vo.AgreementSummary
import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import com.eligo.server.security.UserPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.whenever
import org.slf4j.MDC
import org.springframework.dao.DuplicateKeyException
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Optional
import java.util.OptionalLong

class AgreementServiceTests {
    private val agreementMapper = mock(AgreementMapper::class.java)
    private val consentMapper = mock(AgreementConsentMapper::class.java)
    private val service = DefaultAgreementService(agreementMapper, consentMapper)

    @BeforeEach
    fun setUp() {
        whenever(agreementMapper.findCurrent(null)).thenReturn(
            listOf(
                agreement(101L, AgreementType.USER_AGREEMENT, "1.0", 3, true),
                agreement(102L, AgreementType.PRIVACY_POLICY, "2.0", 3, false)
            )
        )
    }

    @AfterEach
    fun clearRequestId() {
        MDC.remove("requestId")
    }

    @Test
    fun anonymousCurrentAgreementsDoNotExposeConsentFacts() {
        val result = service.current(OptionalLong.empty(), Optional.empty())

        assertThat(result).extracting(Function { it.agreementId }).containsExactly("101", "102")
        assertThat(result).extracting(Function { it.agreed }).containsOnlyNulls()
        verify(consentMapper, never()).findByUserIdAndAgreementId(any<Long>(), any<Long>())
    }

    @Test
    fun authenticatedCurrentAgreementsIdentifyPreviouslyAcceptedVersions() {
        whenever(agreementMapper.findCurrent(AgreementType.USER_AGREEMENT.databaseCode))
            .thenReturn(listOf(agreement(101L, AgreementType.USER_AGREEMENT, "1.0", 3, true)))
        whenever(consentMapper.findByUserIdAndAgreementId(202L, 101L))
            .thenReturn(Optional.of(consent(301L, 202L, 101L, "2026-07-21T08:00:00")))

        val result = service.current(
            OptionalLong.of(202L), Optional.of(AgreementType.USER_AGREEMENT)
        )

        assertThat(result).singleElement().satisfies(Consumer {  item -> assertThat(item.agreed).isTrue()  })
    }

    @Test
    fun retiredAgreementIsVisibleOnlyToUserWhoAcceptedThatVersion() {
        val retired = agreement(100L, AgreementType.USER_AGREEMENT, "0.9", 4, false)
        whenever(agreementMapper.selectById(100L)).thenReturn(retired)
        whenever(consentMapper.findByUserIdAndAgreementId(202L, 100L))
            .thenReturn(Optional.of(consent(300L, 202L, 100L, "2026-06-01T08:00:00")))

        val detail = service.detail(OptionalLong.of(202L), 100L)

        assertThat(detail.agreementId).isEqualTo("100")
        assertThat(detail.agreed).isTrue()
        assertThatThrownBy { service.detail(OptionalLong.empty(), 100L) }
            .isInstanceOfSatisfying(BusinessException::class.java) { exception ->
                assertThat(exception.errorCode)
                    .isEqualTo(AccountUserFileErrorCode.AGREEMENT_NOT_FOUND_OR_INACTIVE)
            }
    }

    @Test
    fun draftOrScheduledAgreementIsNeverPublic() {
        whenever(agreementMapper.selectById(103L))
            .thenReturn(agreement(103L, AgreementType.USER_AGREEMENT, "3.0", 2, true))

        assertThatThrownBy { service.detail(OptionalLong.of(202L), 103L) }
            .isInstanceOfSatisfying(BusinessException::class.java) { exception ->
                assertThat(exception.errorCode)
                    .isEqualTo(AccountUserFileErrorCode.AGREEMENT_NOT_FOUND_OR_INACTIVE)
            }
    }

    @Test
    fun firstConsentCreatesAppendOnlyFact() {
        val current = agreement(101L, AgreementType.USER_AGREEMENT, "1.0", 3, true)
        whenever(agreementMapper.selectById(101L)).thenReturn(current)
        whenever(consentMapper.findByUserIdAndAgreementId(202L, 101L)).thenReturn(Optional.empty())
        MDC.put("requestId", "request-consent-001")

        val result = service.consent(UserPrincipal(202L, "session-a"), 101L)

        assertThat(result.agreementId).isEqualTo("101")
        assertThat(result.created).isTrue()
        val insertedConsent = ArgumentCaptor.forClass(AgreementConsentEntity::class.java)
        verify(consentMapper).insert(insertedConsent.capture())
        assertThat(insertedConsent.value.requestId).isEqualTo("request-consent-001")
        assertThat(insertedConsent.value.ipAddressCiphertext).isNull()
        assertThat(insertedConsent.value.userAgentSummary).isNull()
        assertThat(insertedConsent.value.clientVersion).isNull()
    }

    @Test
    fun repeatedConsentReturnsOriginalFactWithoutUpdate() {
        val original = consent(301L, 202L, 101L, "2026-07-21T08:00:00")
        original.requestId = "request-original"
        whenever(agreementMapper.selectById(101L))
            .thenReturn(agreement(101L, AgreementType.USER_AGREEMENT, "1.0", 3, true))
        whenever(consentMapper.findByUserIdAndAgreementId(202L, 101L)).thenReturn(Optional.of(original))
        MDC.put("requestId", "request-repeated")

        val result = service.consent(UserPrincipal(202L, "session-a"), 101L)

        assertThat(result.created).isFalse()
        assertThat(result.agreedAt).isEqualTo(original.agreedAt!!.toInstant(ZoneOffset.UTC))
        assertThat(original.requestId).isEqualTo("request-original")
        verify(consentMapper, never()).insert(any<AgreementConsentEntity>())
        verify(consentMapper, never()).updateById(any<AgreementConsentEntity>())
    }

    @Test
    fun repeatedConsentReturnsOriginalFactAfterAgreementRetires() {
        val original = consent(303L, 202L, 101L, "2026-07-21T08:00:00")
        whenever(consentMapper.findByUserIdAndAgreementId(202L, 101L)).thenReturn(Optional.of(original))
        whenever(agreementMapper.selectById(101L))
            .thenReturn(agreement(101L, AgreementType.USER_AGREEMENT, "1.0", 4, true))

        val result = service.consent(UserPrincipal(202L, "session-a"), 101L)

        assertThat(result.agreementId).isEqualTo("101")
        assertThat(result.agreedAt).isEqualTo(original.agreedAt!!.toInstant(ZoneOffset.UTC))
        assertThat(result.created).isFalse()
        verify(agreementMapper, never()).selectById(101L)
        verify(consentMapper, never()).insert(any<AgreementConsentEntity>())
        verify(consentMapper, never()).updateById(any<AgreementConsentEntity>())
    }

    @Test
    fun userWithoutOriginalFactCannotConsentToRetiredAgreement() {
        whenever(consentMapper.findByUserIdAndAgreementId(202L, 101L)).thenReturn(Optional.empty())
        whenever(agreementMapper.selectById(101L))
            .thenReturn(agreement(101L, AgreementType.USER_AGREEMENT, "1.0", 4, true))

        assertThatThrownBy { service.consent(UserPrincipal(202L, "session-a"), 101L) }
            .isInstanceOfSatisfying(BusinessException::class.java) { exception ->
                assertThat(exception.errorCode)
                    .isEqualTo(AccountUserFileErrorCode.AGREEMENT_NOT_FOUND_OR_INACTIVE)
            }
        verify(consentMapper, never()).insert(any<AgreementConsentEntity>())
        verify(consentMapper, never()).updateById(any<AgreementConsentEntity>())
    }

    @Test
    fun concurrentConsentUniqueConflictReturnsWinningOriginalFact() {
        val winner = consent(302L, 202L, 101L, "2026-07-21T08:00:01")
        whenever(agreementMapper.selectById(101L))
            .thenReturn(agreement(101L, AgreementType.USER_AGREEMENT, "1.0", 3, true))
        whenever(consentMapper.findByUserIdAndAgreementId(202L, 101L))
            .thenReturn(Optional.empty())
        whenever(consentMapper.lockByUserIdAndAgreementId(202L, 101L)).thenReturn(Optional.of(winner))
        whenever(consentMapper.insert(any<AgreementConsentEntity>()))
            .thenThrow(DuplicateKeyException("并发唯一约束冲突"))

        val result = service.consent(UserPrincipal(202L, "session-a"), 101L)

        assertThat(result.created).isFalse()
        assertThat(result.agreedAt).isEqualTo(winner.agreedAt!!.toInstant(ZoneOffset.UTC))
    }

    @Test
    fun pendingRequiredAgreementIdsComeFromCurrentUnacceptedVersions() {
        whenever(consentMapper.findPendingRequiredAgreementIds(202L)).thenReturn(listOf(101L, 102L))

        assertThat(service.pendingRequiredAgreementIds(202L)).containsExactly(101L, 102L)
    }

    private fun agreement(
        id: Long, type: AgreementType, versionCode: String, status: Int,
        requiresReconsent: Boolean
    ): AgreementEntity {
        val agreement = AgreementEntity()
        agreement.id = id
        agreement.agreementType = type.databaseCode
        agreement.versionCode = versionCode
        agreement.title = if (type == AgreementType.USER_AGREEMENT) "用户协议" else "隐私政策"
        agreement.content = "协议正文"
        agreement.contentHash = "hash".toByteArray(StandardCharsets.UTF_8)
        agreement.status = status
        agreement.requiresReconsent = if (requiresReconsent) 1 else 0
        agreement.effectiveAt = if (status == 3 || status == 4)
            LocalDateTime.parse("2026-07-21T07:00:00")
        else null
        agreement.retiredAt = if (status == 4) LocalDateTime.parse("2026-07-21T09:00:00") else null
        return agreement
    }

    private fun consent(id: Long, userId: Long, agreementId: Long, agreedAt: String): AgreementConsentEntity {
        val consent = AgreementConsentEntity()
        consent.id = id
        consent.userId = userId
        consent.agreementId = agreementId
        consent.agreedAt = LocalDateTime.parse(agreedAt)
        return consent
    }
}
