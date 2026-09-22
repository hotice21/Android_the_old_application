package com.eligo.server.profile.dto

import jakarta.validation.constraints.NotBlank

data class UpdateAvatarRequest(
    @field:NotBlank val fileId: String?
)
