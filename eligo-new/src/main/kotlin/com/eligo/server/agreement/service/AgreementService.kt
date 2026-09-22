package com.eligo.server.agreement.service

import com.eligo.server.agreement.entity.AgreementType
import com.eligo.server.agreement.vo.AgreementConsentView
import com.eligo.server.agreement.vo.AgreementDetail
import com.eligo.server.agreement.vo.AgreementSummary
import com.eligo.server.security.UserPrincipal
import java.util.Optional
import java.util.OptionalLong

interface AgreementService {
    fun current(userId: OptionalLong, type: Optional<AgreementType>): List<AgreementSummary>
    fun detail(userId: OptionalLong, agreementId: Long): AgreementDetail
    fun consent(principal: UserPrincipal, agreementId: Long): AgreementConsentView
    fun pendingRequiredAgreementIds(userId: Long): List<Long>
}
