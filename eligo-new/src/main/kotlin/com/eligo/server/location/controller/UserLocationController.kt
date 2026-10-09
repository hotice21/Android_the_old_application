package com.eligo.server.location.controller

import com.eligo.server.common.api.Result
import com.eligo.server.location.dto.UpdateLocationRequest
import com.eligo.server.location.entity.UserLocationEntity
import com.eligo.server.location.service.UserLocationService
import com.eligo.server.security.UserPrincipal
import java.math.BigDecimal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
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
        @RequestParam(required = false) locationSource: Int?,
        @RequestParam(required = false) provinceName: String?,
        @RequestParam(required = false) cityName: String?,
        @RequestParam(required = false) districtName: String?,
        @RequestParam(required = false) streetName: String?,
        @RequestParam(required = false) latitude: BigDecimal?,
        @RequestParam(required = false) longitude: BigDecimal?
    ): Result<UserLocationEntity> {
        val request = UpdateLocationRequest(
            locationSource = locationSource ?: 0,
            provinceName = provinceName,
            cityName = cityName,
            districtName = districtName,
            streetName = streetName,
            latitude = latitude,
            longitude = longitude
        )
        return Result.success(userLocationService.updateLocation(principal.userId, request))
    }
}
