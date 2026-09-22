package com.eligo.server.activity

import com.eligo.server.activity.dto.OrganizationActivityCreateRequest
import com.eligo.server.activity.dto.OrganizationActivityUpdateRequest
import com.eligo.server.activity.dto.PersonalActivityCreateRequest
import com.eligo.server.activity.dto.PersonalActivityUpdateRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class ActivityCoordinateSystemContractTests {

    @Test
    fun allActivityWriteRequestsExposeCoordinateSystem() {
        val now = Instant.parse("2026-08-22T00:00:00Z")
        val latitude = BigDecimal("22.5430960")
        val longitude = BigDecimal("114.0578650")

        val personalCreate = PersonalActivityCreateRequest(
            "活动", null, null, null, listOf(), now, now, now, now,
            null, null, latitude, longitude, 10, null, null, null,
            null, null, null, null, "市民中心东门", "GCJ-02"
        )
        val organizationCreate = OrganizationActivityCreateRequest(
            "活动", null, null, null, listOf(), now, now, now, now,
            null, null, latitude, longitude, 10, null, null, null,
            null, null, "市民中心东门", "GCJ-02"
        )
        val personalUpdate = PersonalActivityUpdateRequest(
            1, "活动", null, null, null, listOf(), now, now, now, now,
            null, null, latitude, longitude, 10, null, null, null,
            null, null, null, null, "市民中心东门", "GCJ-02"
        )
        val organizationUpdate = OrganizationActivityUpdateRequest(
            1, "活动", null, null, null, listOf(), now, now, now, now,
            null, null, latitude, longitude, 10, null, null, null,
            null, null, "市民中心东门", "GCJ-02"
        )

        assertThat(personalCreate.coordinateSystem).isEqualTo("GCJ-02")
        assertThat(organizationCreate.coordinateSystem).isEqualTo("GCJ-02")
        assertThat(personalUpdate.coordinateSystem).isEqualTo("GCJ-02")
        assertThat(organizationUpdate.coordinateSystem).isEqualTo("GCJ-02")
    }
}
