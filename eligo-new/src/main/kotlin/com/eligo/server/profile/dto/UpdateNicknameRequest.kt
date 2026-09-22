package com.eligo.server.profile.dto

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class UpdateNicknameRequest(
    @field:NotNull @field:Size(max = 256) val nickname: String?
)
