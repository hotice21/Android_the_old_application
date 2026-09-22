package com.eligo.server.activity.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant

@JsonIgnoreProperties(ignoreUnknown = false)
data class PersonalActivityUpdateRequest(
    @field:NotNull @field:PositiveOrZero val version: Int?,
    @field:NotBlank val title: String,
    @field:Size(max = 32) val categoryCode: String? = null,
    val description: String? = null,
    val coverFileId: String? = null,
    @field:Size(max = 5) val mediaFileIds: List<@NotBlank String>? = null,
    val registrationStartsAt: Instant? = null,
    val registrationEndsAt: Instant? = null,
    val startsAt: Instant? = null,
    val endsAt: Instant? = null,
    @field:Size(max = 9) val regionCode: String? = null,
    val addressDetail: String? = null,
    @field:Digits(integer = 2, fraction = 7)
    @field:DecimalMin("-90.0000000")
    @field:DecimalMax("90.0000000")
    val latitude: BigDecimal? = null,
    @field:Digits(integer = 3, fraction = 7)
    @field:DecimalMin("-180.0000000")
    @field:DecimalMax("180.0000000")
    val longitude: BigDecimal? = null,
    @field:Positive val capacity: Int? = null,
    val signupDetails: String? = null,
    val organizerMessage: String? = null,
    val registrationGender: String? = null,
    val organizerPhone: String? = null,
    val organizerWechat: String? = null,
    val organizerWechatQrFileId: String? = null,
    val refundPolicy: String? = null,
    val placeName: String? = null,
    val coordinateSystem: String? = null,
    @field:Size(max = 5) val topics: List<String>? = null
)
