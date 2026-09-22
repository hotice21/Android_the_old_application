package com.eligo.server.account.service

import com.eligo.server.account.vo.PhoneBindingView

interface PhoneBindingReader {

    fun current(userId: Long): PhoneBindingView
}
