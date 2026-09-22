package com.eligo.server.activity

import com.eligo.server.activity.dto.OrganizationActivityCreateRequest
import com.eligo.server.activity.dto.OrganizationActivityUpdateRequest
import com.eligo.server.activity.dto.PersonalActivityCreateRequest
import com.eligo.server.activity.dto.PersonalActivityUpdateRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class ActivityPlaceNameContractTests {

    @Test
    fun allActivityWriteRequestsCarryPlaceName() {
        val personalCreate = PersonalActivityCreateRequest(
            "活动", "HIKING", "说明", null, listOf(),
            TIME, TIME, TIME, TIME, "440305", "详细地址",
            LATITUDE, LONGITUDE, 20, "报名说明", "组织者留言",
            "UNLIMITED", null, null, null, null, "市民中心东门"
        )
        val organizationCreate = OrganizationActivityCreateRequest(
            "活动", "HIKING", "说明", null, listOf(),
            TIME, TIME, TIME, TIME, "440305", "详细地址",
            LATITUDE, LONGITUDE, 20, "UNLIMITED",
            null, null, null, null, "市民中心东门"
        )
        val personalUpdate = PersonalActivityUpdateRequest(
            0, "活动", "HIKING", "说明", null, listOf(),
            TIME, TIME, TIME, TIME, "440305", "详细地址",
            LATITUDE, LONGITUDE, 20, "报名说明", "组织者留言",
            "UNLIMITED", null, null, null, null, "市民中心东门"
        )
        val organizationUpdate = OrganizationActivityUpdateRequest(
            0, "活动", "HIKING", "说明", null, listOf(),
            TIME, TIME, TIME, TIME, "440305", "详细地址",
            LATITUDE, LONGITUDE, 20, "UNLIMITED",
            null, null, null, null, "市民中心东门"
        )

        assertThat(
            listOf(
                personalCreate.placeName,
                organizationCreate.placeName,
                personalUpdate.placeName,
                organizationUpdate.placeName
            )
        ).containsOnly("市民中心东门")
    }

    companion object {
        private val TIME = Instant.parse("2026-08-23T00:00:00Z")
        private val LATITUDE = BigDecimal("22.5430960")
        private val LONGITUDE = BigDecimal("114.0578650")
    }
}
