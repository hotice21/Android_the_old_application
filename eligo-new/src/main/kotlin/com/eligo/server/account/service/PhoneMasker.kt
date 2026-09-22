package com.eligo.server.account.service

import org.springframework.stereotype.Component

@Component
class PhoneMasker {

    fun mask(phone: String): String {
        if (!phone.matches("[0-9]{7,20}".toRegex())) {
            throw IllegalArgumentException("手机号格式无效")
        }
        val suffixLength = minOf(4, phone.length - 2)
        val prefixLength = minOf(3, phone.length - suffixLength - 1)
        val hiddenLength = phone.length - prefixLength - suffixLength
        return phone.substring(0, prefixLength) +
            "*".repeat(hiddenLength) +
            phone.substring(phone.length - suffixLength)
    }
}
