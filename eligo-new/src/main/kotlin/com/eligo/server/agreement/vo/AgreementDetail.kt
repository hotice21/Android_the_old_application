package com.eligo.server.agreement.vo

import com.eligo.server.agreement.entity.AgreementType
import java.time.Instant

data class AgreementDetail(
    val agreementId: String,
    val type: AgreementType,
    val versionCode: String,
    val title: String,
    val requiresReconsent: Boolean,
    val effectiveAt: Instant,
    val agreed: Boolean?,
    val content: String,
    val contentHash: String
)
