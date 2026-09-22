package com.eligo.server.activity

import com.eligo.server.activity.controller.ActivityController
import com.eligo.server.activity.service.ActivityCommandService
import com.eligo.server.activity.service.ActivityReadService
import com.eligo.server.activity.vo.ActivityMapItemView
import com.eligo.server.activity.vo.ActivityMapView
import com.eligo.server.activity.vo.ActivityOwnerSummaryView
import com.eligo.server.activity.vo.PublicActivityDetailView
import com.eligo.server.activity.vo.PublicActivitySummaryView
import com.eligo.server.activity.vo.PublicImageView
import com.eligo.server.common.api.CursorPage
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.common.web.GlobalExceptionHandler
import com.eligo.server.config.security.SecurityConfig
import com.eligo.server.security.AccountRestrictionFilter
import com.eligo.server.security.AccountRestrictionReader
import com.eligo.server.security.SessionAccessReader
import com.eligo.server.security.SessionAuthenticationFilter
import com.eligo.server.security.UserPrincipal
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Instant

@WebMvcTest(ActivityController::class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(
    GlobalExceptionHandler::class,
    SecurityConfig::class,
    SessionAuthenticationFilter::class,
    AccountRestrictionFilter::class
)
class ActivityControllerIntegrationTests {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var service: ActivityReadService

    @MockitoBean
    private lateinit var commandService: ActivityCommandService

    @MockitoBean
    private lateinit var sessionAccessReader: SessionAccessReader

    @MockitoBean
    private lateinit var accountRestrictionReader: AccountRestrictionReader

    @Test
    fun anonymousCanReadPublicActivityList() {
        `when`(service.listPublicActivities(null, 20, null, null, null, null))
            .thenReturn(CursorPage(listOf(summary()), null, false))

        mockMvc.perform(get("/api/v1/activities"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].activityId").value("1"))
            .andExpect(jsonPath("$.data.items[0].owner.displayName").value("小明"))
            .andExpect(jsonPath("$.data.items[0].latitude").value(22.5430960))
            .andExpect(jsonPath("$.data.items[0].longitude").value(114.0578650))
            .andExpect(jsonPath("$.data.items[0].distanceMeters").value(nullValue()))
            .andExpect(jsonPath("$.data.items[0].feeType").value("FREE"))
            .andExpect(jsonPath("$.data.hasMore").value(false))
    }

    @Test
    fun publicActivityListPassesKeywordToReadService() {
        `when`(service.listPublicActivities(null, 20, null, null, null, "夜跑"))
            .thenReturn(CursorPage(listOf(summary()), null, false))

        mockMvc.perform(
            get("/api/v1/activities")
                .queryParam("keyword", "夜跑")
        )
            .andExpect(status().isOk)

        verify(service).listPublicActivities(null, 20, null, null, null, "夜跑")
    }

    @Test
    fun publicActivityListPassesTopicAndReturnsTopics() {
        val item = summary()
        val withTopics = PublicActivitySummaryView(
            item.activityId, item.status, item.title, item.categoryCode,
            item.cover, item.owner, item.startsAt, item.endsAt,
            item.regionCode, item.addressDetail, item.latitude, item.longitude,
            item.capacity, item.participantCount, item.registrationStatus,
            item.feeType, item.placeName, item.coordinateSystem,
            item.distanceMeters, listOf("Hiking", "夜跑")
        )
        `when`(
            service.listPublicActivities(
                null, 20, null, null, null, null, "Hiking",
                null, null, null
            )
        ).thenReturn(CursorPage(listOf(withTopics), null, false))

        mockMvc.perform(
            get("/api/v1/activities")
                .queryParam("topic", "Hiking")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].topics[0]").value("Hiking"))
            .andExpect(jsonPath("$.data.items[0].topics[1]").value("夜跑"))

        verify(service).listPublicActivities(
            null, 20, null, null, null, null, "Hiking",
            null, null, null
        )
    }

    @Test
    fun publicActivityListPassesNearbyCoordinatesAndRadiusToReadService() {
        val latitude = BigDecimal("22.5430960")
        val longitude = BigDecimal("114.0578650")
        `when`(
            service.listPublicActivities(
                null, 20, null, null, null, null,
                latitude, longitude, 5000
            )
        ).thenReturn(CursorPage(listOf(summaryWithDistance()), null, false))

        mockMvc.perform(
            get("/api/v1/activities")
                .queryParam("userLatitude", "22.5430960")
                .queryParam("userLongitude", "114.0578650")
                .queryParam("radiusMeters", "5000")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].distanceMeters").value(321))

        verify(service).listPublicActivities(
            null, 20, null, null, null, null,
            latitude, longitude, 5000
        )
    }

    @Test
    fun anonymousCanReadActivitiesInsideMapBounds() {
        `when`(
            service.listActivitiesOnMap(
                BigDecimal("22.5"),
                BigDecimal("22.6"),
                BigDecimal("113.9"),
                BigDecimal("114.2"),
                "hiking", 100
            )
        ).thenReturn(ActivityMapView(listOf(mapItem()), false))

        mockMvc.perform(
            get("/api/v1/activities/map")
                .queryParam("minLatitude", "22.5")
                .queryParam("maxLatitude", "22.6")
                .queryParam("minLongitude", "113.9")
                .queryParam("maxLongitude", "114.2")
                .queryParam("categoryCode", "hiking")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].activityId").value("1"))
            .andExpect(jsonPath("$.data.items[0].latitude").value(22.5430960))
            .andExpect(jsonPath("$.data.items[0].longitude").value(114.0578650))
            .andExpect(jsonPath("$.data.items[0].registrationStatus").value("OPEN"))
            .andExpect(jsonPath("$.data.truncated").value(false))
    }

    @Test
    fun anonymousCanReadNearbyMapByCenterAndRadiusWithoutBounds() {
        val latitude = BigDecimal("22.5430960")
        val longitude = BigDecimal("114.0578650")
        `when`(
            service.listActivitiesOnMap(
                null, null, null, null,
                latitude, longitude, 5000, null, 100
            )
        ).thenReturn(ActivityMapView(listOf(mapItemWithDistance()), false))

        mockMvc.perform(
            get("/api/v1/activities/map")
                .queryParam("userLatitude", "22.5430960")
                .queryParam("userLongitude", "114.0578650")
                .queryParam("radiusMeters", "5000")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].distanceMeters").value(321))

        verify(service).listActivitiesOnMap(
            null, null, null, null,
            latitude, longitude, 5000, null, 100
        )
    }

    @Test
    fun mapQueryMissingRequiredBoundReturnsStableError() {
        `when`(
            service.listActivitiesOnMap(
                BigDecimal("22.5"),
                BigDecimal("22.6"),
                BigDecimal("113.9"),
                null, null, 100
            )
        ).thenThrow(BusinessException(CommonErrorCode.VALIDATION_FAILED))

        mockMvc.perform(
            get("/api/v1/activities/map")
                .queryParam("minLatitude", "22.5")
                .queryParam("maxLatitude", "22.6")
                .queryParam("minLongitude", "113.9")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value(10001))
    }

    @Test
    fun mapQueryNonNumericBoundReturnsGlobalTypeMismatchError() {
        mockMvc.perform(
            get("/api/v1/activities/map")
                .queryParam("minLatitude", "not-a-number")
                .queryParam("maxLatitude", "22.6")
                .queryParam("minLongitude", "113.9")
                .queryParam("maxLongitude", "114.2")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value(10005))
            .andExpect(jsonPath("$.data[0].field").value("minLatitude"))

        verifyNoInteractions(service)
    }

    @Test
    fun mapQueryInvalidLimitReturnsValidationError() {
        `when`(
            service.listActivitiesOnMap(
                BigDecimal("22.5"),
                BigDecimal("22.6"),
                BigDecimal("113.9"),
                BigDecimal("114.2"),
                null, 201
            )
        ).thenThrow(BusinessException(CommonErrorCode.VALIDATION_FAILED))

        mockMvc.perform(
            get("/api/v1/activities/map")
                .queryParam("minLatitude", "22.5")
                .queryParam("maxLatitude", "22.6")
                .queryParam("minLongitude", "113.9")
                .queryParam("maxLongitude", "114.2")
                .queryParam("limit", "201")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value(10001))
    }

    @Test
    fun anonymousCanReadPublicActivityDetail() {
        `when`(service.getPublicActivity(null, 1L)).thenReturn(detail())

        mockMvc.perform(get("/api/v1/activities/1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.activityId").value("1"))
            .andExpect(jsonPath("$.data.description").value("活动介绍"))
            .andExpect(jsonPath("$.data.latitude").value(22.5430960))
            .andExpect(jsonPath("$.data.longitude").value(114.0578650))
            .andExpect(jsonPath("$.data.distanceMeters").value(nullValue()))
            .andExpect(jsonPath("$.data.media[0].fileId").value("31"))
            .andExpect(jsonPath("$.data.myParticipationStatus").isEmpty)
            .andExpect(jsonPath("$.data.viewerIsOwner").value(false))
    }

    @Test
    fun authenticatedViewerIdentityIsPassedToPublicDetail() {
        val principal = UserPrincipal(22L, "session-viewer")
        `when`(service.getPublicActivity(principal, 1L)).thenReturn(detail())

        mockMvc.perform(
            get("/api/v1/activities/1")
                .with(authentication(UsernamePasswordAuthenticationToken(principal, "", listOf())))
        )
            .andExpect(status().isOk)
    }

    @Test
    fun legacyActivityKeepsExplicitNullCoordinateKeys() {
        `when`(service.listPublicActivities(null, 20, null, null, null, null))
            .thenReturn(CursorPage(listOf(summaryWithoutCoordinates()), null, false))

        mockMvc.perform(get("/api/v1/activities"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].latitude").value(nullValue()))
            .andExpect(jsonPath("$.data.items[0].longitude").value(nullValue()))
    }

    @Test
    fun invalidActivityIdIsRejectedBeforeServiceCall() {
        mockMvc.perform(get("/api/v1/activities/not-a-number"))
            .andExpect(status().isBadRequest)
    }

    private fun summary(): PublicActivitySummaryView {
        return PublicActivitySummaryView(
            "1",
            "PUBLISHED",
            "测试活动",
            "HIKING",
            PublicImageView("11", "/api/v1/files/11/content"),
            ActivityOwnerSummaryView(
                "USER", "21", "小明",
                PublicImageView("12", "/api/v1/files/12/content")
            ),
            Instant.parse("2026-08-07T02:00:00Z"),
            Instant.parse("2026-08-07T03:00:00Z"),
            "440305",
            "深圳市南山区测试地址",
            BigDecimal("22.5430960"),
            BigDecimal("114.0578650"),
            20,
            2,
            "OPEN",
            "FREE"
        )
    }

    private fun mapItem(): ActivityMapItemView {
        return ActivityMapItemView(
            "1",
            "测试活动",
            "HIKING",
            PublicImageView("11", "/api/v1/files/11/content"),
            Instant.parse("2026-08-07T02:00:00Z"),
            Instant.parse("2026-08-07T03:00:00Z"),
            "440305",
            "深圳市南山区测试地址",
            BigDecimal("22.5430960"),
            BigDecimal("114.0578650"),
            "OPEN",
            "FREE"
        )
    }

    private fun summaryWithDistance(): PublicActivitySummaryView {
        val summary = summary()
        return PublicActivitySummaryView(
            summary.activityId, summary.status, summary.title,
            summary.categoryCode, summary.cover, summary.owner,
            summary.startsAt, summary.endsAt, summary.regionCode,
            summary.addressDetail, summary.latitude, summary.longitude,
            summary.capacity, summary.participantCount,
            summary.registrationStatus, summary.feeType,
            "市民中心东门", "GCJ-02", 321L
        )
    }

    private fun mapItemWithDistance(): ActivityMapItemView {
        val item = mapItem()
        return ActivityMapItemView(
            item.activityId, item.title, item.categoryCode, item.cover,
            item.startsAt, item.endsAt, item.regionCode, item.addressDetail,
            item.latitude, item.longitude, item.registrationStatus, item.feeType,
            "市民中心东门", "GCJ-02", 321L
        )
    }

    private fun detail(): PublicActivityDetailView {
        return PublicActivityDetailView(
            "1",
            "PUBLISHED",
            "测试活动",
            "HIKING",
            PublicImageView("11", "/api/v1/files/11/content"),
            ActivityOwnerSummaryView(
                "USER", "21", "小明",
                PublicImageView("12", "/api/v1/files/12/content")
            ),
            Instant.parse("2026-08-07T02:00:00Z"),
            Instant.parse("2026-08-07T03:00:00Z"),
            "440305",
            "深圳市南山区测试地址",
            BigDecimal("22.5430960"),
            BigDecimal("114.0578650"),
            20,
            2,
            "OPEN",
            "FREE",
            "活动介绍",
            listOf(PublicImageView("31", "/api/v1/files/31/content")),
            Instant.parse("2026-08-07T00:00:00Z"),
            Instant.parse("2026-08-07T01:00:00Z"),
            "报名说明",
            "组织者留言",
            null,
            false,
            Instant.parse("2026-08-06T00:00:00Z"),
            Instant.parse("2026-08-07T01:00:00Z"),
            null,
            null,
            null,
            null
        )
    }

    private fun summaryWithoutCoordinates(): PublicActivitySummaryView {
        return PublicActivitySummaryView(
            "2",
            "PUBLISHED",
            "历史活动",
            "HIKING",
            null,
            ActivityOwnerSummaryView("USER", "21", "小明", null),
            Instant.parse("2026-08-07T02:00:00Z"),
            Instant.parse("2026-08-07T03:00:00Z"),
            "440305",
            "深圳市南山区历史地址",
            null,
            null,
            20,
            0,
            "OPEN",
            "FREE"
        )
    }
}
