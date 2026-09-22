package com.eligo.server.account.service

import com.eligo.server.account.dto.RefreshTokenRequest
import com.eligo.server.account.dto.WechatLoginRequest
import com.eligo.server.account.vo.LoginResponse
import com.eligo.server.security.UserPrincipal

interface AuthService {
    fun login(request: WechatLoginRequest): LoginResponse
    fun refresh(request: RefreshTokenRequest): LoginResponse
    fun logout(principal: UserPrincipal)
}
