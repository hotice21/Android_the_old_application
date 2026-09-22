package com.eligo.server.account.service

import org.springframework.stereotype.Component

@Component
class PhoneLookupKey {

    fun canonical(countryCode: String, purePhone: String): String =
        PURPOSE +
            ":" +
            normalizeCountryCode(countryCode) +
            ":" +
            normalizePhone(purePhone)

    fun normalizeCountryCode(countryCode: String): String {
        val normalized = countryCode.trim().replaceFirst("^\\+".toRegex(), "")
        if (!normalized.matches("[0-9]{1,8}".toRegex())) {
            throw IllegalArgumentException("国家码格式无效")
        }
        return normalized
    }

    fun normalizePhone(purePhone: String): String {
        val normalized = purePhone.trim()
        if (!normalized.matches("[0-9]{7,20}".toRegex())) {
            throw IllegalArgumentException("手机号格式无效")
        }
        return normalized
    }

    companion object {
        private const val PURPOSE = "phone"
    }
}
