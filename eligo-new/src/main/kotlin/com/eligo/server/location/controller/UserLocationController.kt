package com.eligo.server.location.controller

import com.eligo.server.common.api.Result
import com.eligo.server.location.dto.UpdateLocationRequest
import com.eligo.server.location.entity.UserLocationEntity
import com.eligo.server.location.service.UserLocationService
import com.eligo.server.security.UserPrincipal
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users/me/location")
class UserLocationController(
    private val userLocationService: UserLocationService
) {

    @GetMapping
    fun getMyLocation(
        @AuthenticationPrincipal principal: UserPrincipal
    ): Result<UserLocationEntity?> {
        return Result.success(userLocationService.getMyLocation(principal.userId))
    }

    @PutMapping
    fun updateLocation(
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody request: UpdateLocationRequest
    ): Result<UserLocationEntity> {
        return Result.success(userLocationService.updateLocation(principal.userId, request))
    }
}
