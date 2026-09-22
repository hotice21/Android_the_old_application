package com.eligo.server.account.vo

import java.time.Instant

data class DataExportView(
    val requestId: String?,
    val status: String?,
    val requestedAt: Instant?,
    val processedAt: Instant?,
    val resultExpiresAt: Instant?,
    val failureCode: String?
)
