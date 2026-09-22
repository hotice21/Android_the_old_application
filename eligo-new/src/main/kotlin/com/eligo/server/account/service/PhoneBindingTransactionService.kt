package com.eligo.server.account.service

import com.eligo.server.account.vo.PhoneBindingView
import com.eligo.server.integration.wechat.AuthorizedPhone

interface PhoneBindingTransactionService {

    fun current(userId: Long): PhoneBindingView

    fun bindOrReplace(userId: Long, phone: AuthorizedPhone): PhoneBindingView

    fun unbind(userId: Long)
}
