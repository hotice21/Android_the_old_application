package com.eligo.server.account.controller

import com.eligo.server.account.dto.PhoneBindingRequest
import com.eligo.server.account.service.PhoneBindingService
import com.eligo.server.account.vo.PhoneBindingView
import com.eligo.server.common.api.Result
import com.eligo.server.security.UserPrincipal
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class PhoneBindingController(private val service: PhoneBindingService) {

    @GetMapping("/api/v1/account/phone-binding")
    fun current(@AuthenticationPrincipal principal: UserPrincipal): Result<PhoneBindingView?> =
        Result.success(service.current(principal))

    @PutMapping("/api/v1/account/phone-binding")
    fun bindOrReplace(
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody request: PhoneBindingRequest
    ): Result<PhoneBindingView?> =
        Result.success(service.bindOrReplace(principal, request.phoneCode))

    @DeleteMapping("/api/v1/account/phone-binding")
    fun unbind(@AuthenticationPrincipal principal: UserPrincipal): Result<Void> {
        service.unbind(principal)
        return Result.success()
    }
}
