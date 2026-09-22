package com.eligo.server.account.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class PhoneBindingRequest(
    @field:NotBlank
    @field:Size(max = 512)
    val phoneCode: String
)
