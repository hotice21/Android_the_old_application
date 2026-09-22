package com.eligo.server.account.vo

import java.time.Instant

data class PhoneBindingView(
    val bound: Boolean,
    val countryCode: String?,
    val maskedPhone: String?,
    val boundAt: Instant?
) {
    companion object {
        @JvmStatic
        fun unbound(): PhoneBindingView = PhoneBindingView(false, null, null, null)
    }
}
