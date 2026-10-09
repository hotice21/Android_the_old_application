package com.eligo.server.location.dto

import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

data class UpdateLocationRequest(
    @field:NotNull val locationSource: Int,
    val provinceName: String? = null,
    val cityName: String? = null,
    val districtName: String? = null,
    val streetName: String? = null,
    val latitude: BigDecimal? = null,
    val longitude: BigDecimal? = null
)
