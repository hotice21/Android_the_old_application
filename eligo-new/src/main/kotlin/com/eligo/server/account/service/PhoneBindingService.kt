package com.eligo.server.account.service

import com.eligo.server.account.vo.PhoneBindingView
import com.eligo.server.security.UserPrincipal

interface PhoneBindingService : PhoneBindingReader {

    fun current(principal: UserPrincipal): PhoneBindingView

    fun bindOrReplace(principal: UserPrincipal, phoneCode: String): PhoneBindingView

    fun unbind(principal: UserPrincipal)
}
