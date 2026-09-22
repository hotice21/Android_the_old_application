package com.eligo.server.file.vo

import java.time.Instant

data class FileView(
    val fileId: String,
    val purpose: String,
    val contentType: String,
    val sizeBytes: Long,
    val accessLevel: String,
    val scanStatus: String,
    val lifecycleStatus: String,
    val expiresAt: Instant?,
    val url: String?
) {
    constructor(
        fileId: String,
        contentType: String,
        sizeBytes: Long,
        accessLevel: String,
        scanStatus: String,
        lifecycleStatus: String,
        expiresAt: Instant?,
        url: String?
    ) : this(
        fileId,
        PURPOSE_AVATAR,
        contentType,
        sizeBytes,
        accessLevel,
        scanStatus,
        lifecycleStatus,
        expiresAt,
        url
    )

    companion object {
        private const val PURPOSE_AVATAR = "AVATAR"
    }
}
