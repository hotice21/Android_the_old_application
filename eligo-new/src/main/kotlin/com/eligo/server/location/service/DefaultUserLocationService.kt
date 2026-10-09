package com.eligo.server.location.service

import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.location.dto.UpdateLocationRequest
import com.eligo.server.location.entity.UserLocationEntity
import com.eligo.server.location.mapper.UserLocationMapper
import java.time.LocalDateTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DefaultUserLocationService(
    private val userLocations: UserLocationMapper
) : UserLocationService {

    override fun getMyLocation(userId: Long): UserLocationEntity? {
        return userLocations.selectById(userId)
    }

    @Transactional
    override fun updateLocation(
        userId: Long,
        request: UpdateLocationRequest
    ): UserLocationEntity {
        val now = LocalDateTime.now()
        val existing = userLocations.selectById(userId)
        return if (existing != null) {
            existing.locationSource = request.locationSource
            existing.provinceName = request.provinceName
            existing.cityName = request.cityName
            existing.districtName = request.districtName
            existing.streetName = request.streetName
            existing.latitude = request.latitude
            existing.longitude = request.longitude
            existing.updatedAt = now
            userLocations.updateById(existing)
            existing
        } else {
            val entity = UserLocationEntity().apply {
                this.userId = userId
                this.locationSource = request.locationSource
                this.provinceName = request.provinceName
                this.cityName = request.cityName
                this.districtName = request.districtName
                this.streetName = request.streetName
                this.latitude = request.latitude
                this.longitude = request.longitude
                this.createdAt = now
                this.updatedAt = now
            }
            userLocations.insert(entity)
            entity
        }
    }
}
