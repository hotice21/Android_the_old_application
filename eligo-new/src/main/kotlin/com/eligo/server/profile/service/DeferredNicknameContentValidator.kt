package com.eligo.server.profile.service

import org.springframework.stereotype.Component

@Component
class DeferredNicknameContentValidator : NicknameContentValidator {

    override fun validate(normalizedNickname: String) {
    }
}
