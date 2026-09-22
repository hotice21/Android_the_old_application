package com.eligo.server.agreement.service

import com.eligo.server.agreement.entity.AgreementConsentEntity
import com.eligo.server.agreement.entity.AgreementEntity
import com.eligo.server.agreement.entity.AgreementType
import com.eligo.server.agreement.mapper.AgreementConsentMapper
import com.eligo.server.agreement.mapper.AgreementMapper
import com.eligo.server.agreement.vo.AgreementConsentView
import com.eligo.server.agreement.vo.AgreementDetail
import com.eligo.server.agreement.vo.AgreementSummary
import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.web.RequestIdContext
import com.eligo.server.security.UserPrincipal
import org.springframework.context.annotation.Profile
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.HexFormat
import java.util.Optional
import java.util.OptionalLong

@Service
@Profile("!test")
class DefaultAgreementService(
    private val agreementMapper: AgreementMapper,
    private val consentMapper: AgreementConsentMapper
) : AgreementService {

    @Transactional(readOnly = true)
    override fun current(userId: OptionalLong, type: Optional<AgreementType>): List<AgreementSummary> {
        val typeCode = type.map { it.databaseCode }.orElse(null)
        return agreementMapper.findCurrent(typeCode).map { agreement ->
            summary(agreement, agreed(userId, agreement.id!!))
        }
    }

    @Transactional(readOnly = true)
    override fun detail(userId: OptionalLong, agreementId: Long): AgreementDetail {
        val agreement = agreementMapper.selectById(agreementId)
        if (agreement == null || !isPubliclyVisible(agreement, userId)) {
            throw agreementUnavailable()
        }
        val agreed = agreed(userId, agreementId)
        return AgreementDetail(
            agreement.id.toString(),
            AgreementType.fromDatabaseCode(agreement.agreementType!!),
            agreement.versionCode!!,
            agreement.title!!,
            agreement.requiresReconsent == 1,
            agreement.effectiveAt!!.toInstant(ZoneOffset.UTC),
            agreed,
            agreement.content!!,
            HexFormat.of().formatHex(agreement.contentHash!!)
        )
    }

    @Transactional
    override fun consent(principal: UserPrincipal, agreementId: Long): AgreementConsentView {
        val existing = consentMapper.findByUserIdAndAgreementId(principal.userId, agreementId)
        if (existing.isPresent) {
            return consentView(existing.get(), false)
        }

        val agreement = agreementMapper.selectById(agreementId)
        if (agreement == null || !isCurrentlyEffective(agreement)) {
            throw agreementUnavailable()
        }

        val now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS)
        val consent = AgreementConsentEntity()
        consent.userId = principal.userId
        consent.agreementId = agreementId
        consent.agreedAt = now
        consent.requestId = RequestIdContext.current()
        consent.createdAt = now
        return try {
            consentMapper.insert(consent)
            consentView(consent, true)
        } catch (exception: DuplicateKeyException) {
            val winner = consentMapper
                .lockByUserIdAndAgreementId(principal.userId, agreementId)
                .orElseThrow { exception }
            consentView(winner, false)
        }
    }

    @Transactional(readOnly = true)
    override fun pendingRequiredAgreementIds(userId: Long): List<Long> =
        consentMapper.findPendingRequiredAgreementIds(userId).toList()

    private fun isPubliclyVisible(agreement: AgreementEntity, userId: OptionalLong): Boolean {
        if (isCurrentlyEffective(agreement)) {
            return true
        }
        return agreement.status == RETIRED &&
            userId.isPresent &&
            consentMapper.findByUserIdAndAgreementId(userId.asLong, agreement.id!!).isPresent
    }

    private fun isCurrentlyEffective(agreement: AgreementEntity): Boolean =
        agreement.status == EFFECTIVE &&
            agreement.effectiveAt != null &&
            !agreement.effectiveAt!!.isAfter(LocalDateTime.now(ZoneOffset.UTC))

    private fun agreed(userId: OptionalLong, agreementId: Long): Boolean? {
        if (userId.isEmpty) {
            return null
        }
        return consentMapper.findByUserIdAndAgreementId(userId.asLong, agreementId).isPresent
    }

    private fun summary(agreement: AgreementEntity, agreed: Boolean?): AgreementSummary =
        AgreementSummary(
            agreement.id.toString(),
            AgreementType.fromDatabaseCode(agreement.agreementType!!),
            agreement.versionCode!!,
            agreement.title!!,
            agreement.requiresReconsent == 1,
            agreement.effectiveAt!!.toInstant(ZoneOffset.UTC),
            agreed
        )

    private fun consentView(consent: AgreementConsentEntity, created: Boolean): AgreementConsentView =
        AgreementConsentView(
            consent.agreementId.toString(),
            consent.agreedAt!!.toInstant(ZoneOffset.UTC),
            created
        )

    private fun agreementUnavailable(): BusinessException =
        BusinessException(AccountUserFileErrorCode.AGREEMENT_NOT_FOUND_OR_INACTIVE)

    companion object {
        private const val EFFECTIVE = 3
        private const val RETIRED = 4
    }
}
