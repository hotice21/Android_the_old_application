package com.eligo.server.profile.dto

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class UpdateInterestsRequest(
    @field:NotNull @field:Size(min = 1, max = 100) val interestTagIds: List<Long>?
)
