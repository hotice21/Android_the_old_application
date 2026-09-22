package com.eligo.server.agreement.vo

import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.Instant

data class AgreementConsentView(
    val agreementId: String,
    val agreedAt: Instant,
    @get:JsonIgnore val created: Boolean
)
