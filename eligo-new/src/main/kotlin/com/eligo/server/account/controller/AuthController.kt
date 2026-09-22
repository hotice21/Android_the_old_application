package com.eligo.server.account.controller

import com.eligo.server.account.dto.RefreshTokenRequest
import com.eligo.server.account.dto.WechatLoginRequest
import com.eligo.server.account.service.AuthService
import com.eligo.server.account.vo.LoginResponse
import com.eligo.server.common.api.Result
import com.eligo.server.security.UserPrincipal
import jakarta.validation.Valid
import org.springframework.beans.factory.ObjectProvider
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val authService: ObjectProvider<AuthService>) {

    @PostMapping("/wechat-login")
    fun login(@Valid @RequestBody request: WechatLoginRequest): Result<LoginResponse?> =
        Result.success(authService.getObject().login(request))

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshTokenRequest): Result<LoginResponse?> =
        Result.success(authService.getObject().refresh(request))

    @PostMapping("/logout")
    fun logout(@AuthenticationPrincipal principal: UserPrincipal): Result<Void> {
        authService.getObject().logout(principal)
        return Result.success()
    }
}
