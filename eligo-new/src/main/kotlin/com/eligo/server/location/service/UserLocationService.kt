package com.eligo.server.location.service

import com.eligo.server.location.dto.UpdateLocationRequest
import com.eligo.server.location.entity.UserLocationEntity

interface UserLocationService {
    fun getMyLocation(userId: Long): UserLocationEntity?
    fun updateLocation(userId: Long, request: UpdateLocationRequest): UserLocationEntity
}
