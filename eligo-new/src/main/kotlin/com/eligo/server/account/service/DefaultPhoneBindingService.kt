package com.eligo.server.account.service

import com.eligo.server.account.vo.PhoneBindingView
import com.eligo.server.integration.wechat.AuthorizedPhone
import com.eligo.server.integration.wechat.WechatPhoneClient
import com.eligo.server.security.UserPrincipal
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Service
@Profile("!test")
class DefaultPhoneBindingService(
    private val wechatPhoneClient: WechatPhoneClient,
    private val transactions: PhoneBindingTransactionService
) : PhoneBindingService {

    override fun current(principal: UserPrincipal): PhoneBindingView =
        transactions.current(principal.userId)

    override fun current(userId: Long): PhoneBindingView =
        transactions.current(userId)

    override fun bindOrReplace(principal: UserPrincipal, phoneCode: String): PhoneBindingView {
        val phone = wechatPhoneClient.exchangePhoneCode(phoneCode)
        return transactions.bindOrReplace(principal.userId, phone)
    }

    override fun unbind(principal: UserPrincipal) {
        transactions.unbind(principal.userId)
    }
}
