package com.eligo.server.account.vo

import java.time.Instant

data class DownloadUrlView(
    val downloadUrl: String?,
    val expiresAt: Instant?
)
