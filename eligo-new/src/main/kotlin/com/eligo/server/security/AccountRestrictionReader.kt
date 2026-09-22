package com.eligo.server.security

fun interface AccountRestrictionReader {
    fun read(userId: Long): State

    fun canDownloadExport(userId: Long, fileId: Long): Boolean = false

    data class State(
        val accountStatus: Int,
        val profileCompleted: Boolean,
        val pendingRequiredAgreementIds: List<Long>
    )
}
