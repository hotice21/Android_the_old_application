package com.eligo.server.profile.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PastOrPresent
import jakarta.validation.constraints.Size
import java.time.LocalDate

data class UpdateProfileRequest(
    @field:Size(max = 256) val nickname: String?,
    @field:NotNull @field:PastOrPresent val birthDate: LocalDate?,
    @field:NotNull val gender: String?,
    @field:NotNull val provinceCode: String?,
    @field:NotNull val cityCode: String?,
    @field:NotNull val districtCode: String?,
    @field:Email @field:Size(max = 254) val email: String?,
    @field:Size(max = 1000) val bio: String?
)
