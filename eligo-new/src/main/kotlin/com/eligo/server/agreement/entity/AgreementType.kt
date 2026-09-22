package com.eligo.server.agreement.entity

enum class AgreementType(val databaseCode: Int) {
    USER_AGREEMENT(1),
    PRIVACY_POLICY(2);

    companion object {
        fun fromDatabaseCode(databaseCode: Int): AgreementType =
            values().firstOrNull { it.databaseCode == databaseCode }
                ?: throw IllegalArgumentException("未知协议类型编码")
    }
}
