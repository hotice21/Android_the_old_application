package com.eligo.server.activity

import com.eligo.server.activity.service.ActivityCommandService
import com.eligo.server.activity.service.ActivityCreateOutcome
import com.eligo.server.activity.service.ActivityReadService
import com.eligo.server.activity.vo.ActivityOwnerSummaryView
import com.eligo.server.activity.vo.ManagedActivityDetailView
import com.eligo.server.activity.vo.ManagedActivitySummaryView
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
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@WebMvcTest(com.eligo.server.activity.controller.ActivityController::class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(
    GlobalExceptionHandler::class,
    SecurityConfig::class,
    SessionAuthenticationFilter::class,
    AccountRestrictionFilter::class
)
class ActivityCommandControllerIntegrationTests {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var readService: ActivityReadService

    @MockitoBean
    private lateinit var commandService: ActivityCommandService

    @MockitoBean
    private lateinit var sessionAccessReader: SessionAccessReader

    @MockitoBean
    private lateinit var accountRestrictionReader: AccountRestrictionReader

    @Test
    fun personalCreateReturnsCreatedManagedDraft() {
        `when`(commandService.createPersonal(eq(PRINCIPAL), any(), eq("create-1234")))
            .thenReturn(ActivityCreateOutcome(managed("USER", "202"), false))

        mockMvc.perform(
            post("/api/v1/users/me/activities")
                .with(authentication(auth()))
                .header("Idempotency-Key", "create-1234")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title":"个人草稿","mediaFileIds":[]}
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.activityId").value("1001"))
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.owner.ownerType").value("USER"))
    }

    @Test
    fun personalCreateWithoutIdempotencyKeyReturnsMissingParameter() {
        mockMvc.perform(
            post("/api/v1/users/me/activities")
                .with(authentication(auth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"个人草稿\",\"mediaFileIds\":[]}")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value(10004))
            .andExpect(jsonPath("$.data[0].field").value("Idempotency-Key"))

        verifyNoInteractions(commandService)
    }

    @Test
    fun personalCreateAcceptsTitleAtUnicodeCodePointLimit() {
        val title = "😀".repeat(20)
        `when`(commandService.createPersonal(eq(PRINCIPAL), any(), eq("create-unicode-title")))
            .thenReturn(ActivityCreateOutcome(managed("USER", "202"), false))

        mockMvc.perform(
            post("/api/v1/users/me/activities")
                .with(authentication(auth()))
                .header("Idempotency-Key", "create-unicode-title")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"$title\",\"mediaFileIds\":[]}")
        )
            .andExpect(status().isCreated)
    }

    @Test
    fun personalCreateRejectsTitleAboveUnicodeCodePointLimit() {
        val title = "😀".repeat(21)
        `when`(
            commandService.createPersonal(
                eq(PRINCIPAL), any(), eq("create-unicode-title-too-long")
            )
        ).thenThrow(BusinessException(CommonErrorCode.VALIDATION_FAILED))

        mockMvc.perform(
            post("/api/v1/users/me/activities")
                .with(authentication(auth()))
                .header("Idempotency-Key", "create-unicode-title-too-long")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"$title\",\"mediaFileIds\":[]}")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value(10001))
    }

    @Test
    fun personalCreateRejectsLatitudeOutsideContractRange() {
        mockMvc.perform(
            post("/api/v1/users/me/activities")
                .with(authentication(auth()))
                .header("Idempotency-Key", "create-invalid-latitude")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title":"坐标越界草稿",
                      "mediaFileIds":[],
                      "latitude":90.0000001,
                      "longitude":114.0578650
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value(10001))

        verifyNoInteractions(commandService)
    }

    @Test
    fun organizationCreateReturnsCreatedManagedDraft() {
        `when`(commandService.createOrganization(eq(PRINCIPAL), eq(401L), any(), eq("create-5678")))
            .thenReturn(ActivityCreateOutcome(managed("ORGANIZATION", "401"), false))

        mockMvc.perform(
            post("/api/v1/organizations/401/activities")
                .with(authentication(auth()))
                .header("Idempotency-Key", "create-5678")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"企业草稿\"}")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.owner.ownerType").value("ORGANIZATION"))
            .andExpect(jsonPath("$.data.owner.ownerId").value("401"))
    }

    @Test
    fun organizationCreateWithoutIdempotencyKeyReturnsMissingParameter() {
        mockMvc.perform(
            post("/api/v1/organizations/401/activities")
                .with(authentication(auth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"企业草稿\"}")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value(10004))
            .andExpect(jsonPath("$.data[0].field").value("Idempotency-Key"))

        verifyNoInteractions(commandService)
    }

    @Test
    fun organizationCreateAcceptsTitleAtUnicodeCodePointLimit() {
        val title = "😀".repeat(20)
        `when`(
            commandService.createOrganization(
                eq(PRINCIPAL), eq(401L), any(), eq("create-organization-unicode-title")
            )
        ).thenReturn(ActivityCreateOutcome(managed("ORGANIZATION", "401"), false))

        mockMvc.perform(
            post("/api/v1/organizations/401/activities")
                .with(authentication(auth()))
                .header("Idempotency-Key", "create-organization-unicode-title")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"$title\"}")
        )
            .andExpect(status().isCreated)
    }

    @Test
    fun organizationCreateRejectsTitleAboveUnicodeCodePointLimit() {
        val title = "😀".repeat(21)
        `when`(
            commandService.createOrganization(
                eq(PRINCIPAL), eq(401L), any(), eq("create-organization-unicode-title-too-long")
            )
        ).thenThrow(BusinessException(CommonErrorCode.VALIDATION_FAILED))

        mockMvc.perform(
            post("/api/v1/organizations/401/activities")
                .with(authentication(auth()))
                .header("Idempotency-Key", "create-organization-unicode-title-too-long")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"$title\"}")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value(10001))
    }

    @Test
    fun organizationCreateRejectsLongitudeOutsideContractRange() {
        mockMvc.perform(
            post("/api/v1/organizations/401/activities")
                .with(authentication(auth()))
                .header("Idempotency-Key", "create-invalid-longitude")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title":"企业坐标越界草稿",
                      "latitude":22.5430960,
                      "longitude":180.0000001
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value(10001))

        verifyNoInteractions(commandService)
    }

    @Test
    fun publicationCommandReturnsManagedPublishedActivity() {
        `when`(commandService.publish(PRINCIPAL, 1001L)).thenReturn(managedPublished())

        mockMvc.perform(
            put("/api/v1/activities/1001/publication").with(authentication(auth()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.activityId").value("1001"))
            .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
    }

    @Test
    fun cancellationCommandReturnsManagedCancelledActivity() {
        `when`(commandService.cancel(PRINCIPAL, 1001L)).thenReturn(managedCancelled())

        mockMvc.perform(
            put("/api/v1/activities/1001/cancellation").with(authentication(auth()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.activityId").value("1001"))
            .andExpect(jsonPath("$.data.status").value("CANCELLED"))
    }

    @Test
    fun deleteDraftReturnsSuccess() {
        mockMvc.perform(
            delete("/api/v1/activities/1001").with(authentication(auth()))
        ).andExpect(status().isOk)

        verify(commandService).deleteDraft(PRINCIPAL, 1001L)
    }

    @Test
    fun personalUpdateReturnsUpdatedManagedActivity() {
        `when`(commandService.updatePersonal(eq(PRINCIPAL), eq(1001L), any()))
            .thenReturn(managedPublished())

        mockMvc.perform(
            put("/api/v1/users/me/activities/1001")
                .with(authentication(auth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"version":1,"title":"更新活动","mediaFileIds":[]}
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.activityId").value("1001"))
            .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
    }

    @Test
    fun organizationUpdateReturnsUpdatedManagedActivity() {
        `when`(commandService.updateOrganization(eq(PRINCIPAL), eq(401L), eq(1001L), any()))
            .thenReturn(managed("ORGANIZATION", "401"))

        mockMvc.perform(
            put("/api/v1/organizations/401/activities/1001")
                .with(authentication(auth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"version":0,"title":"企业更新","mediaFileIds":[]}
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.owner.ownerType").value("ORGANIZATION"))
    }

    @Test
    fun organizationUpdateRejectsPersonalOnlyFields() {
        mockMvc.perform(
            put("/api/v1/organizations/401/activities/1001")
                .with(authentication(auth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"version":0,"title":"企业更新","signupDetails":"不允许"}
                    """.trimIndent()
                )
        )
            .andExpect(status().isBadRequest)
        verifyNoInteractions(commandService)
    }

    @Test
    fun anonymousActivityUpdateIsRejectedBeforeCommandService() {
        mockMvc.perform(
            put("/api/v1/users/me/activities/1001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"title\":\"更新活动\"}")
        ).andExpect(status().isUnauthorized)
        verifyNoInteractions(commandService)
    }

    @Test
    fun anonymousCancellationIsRejectedBeforeCommandService() {
        mockMvc.perform(put("/api/v1/activities/1001/cancellation"))
            .andExpect(status().isUnauthorized)
        verifyNoInteractions(commandService)
    }

    @Test
    fun anonymousDraftDeletionIsRejectedBeforeCommandService() {
        mockMvc.perform(delete("/api/v1/activities/1001"))
            .andExpect(status().isUnauthorized)
        verifyNoInteractions(commandService)
    }

    @Test
    fun authenticatedUserCanReadManagedActivityList() {
        `when`(readService.listManagedActivities(PRINCIPAL, null, 20, null, null))
            .thenReturn(CursorPage(listOf(managedSummary()), null, false))

        mockMvc.perform(
            get("/api/v1/users/me/managed-activities").with(authentication(auth()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].activityId").value("1001"))
            .andExpect(jsonPath("$.data.items[0].status").value("DRAFT"))
            .andExpect(jsonPath("$.data.items[0].version").value(0))
            .andExpect(jsonPath("$.data.items[0].owner.ownerId").value("202"))
    }

    @Test
    fun authenticatedUserCanReadManagedActivityDetail() {
        `when`(readService.getManagedActivity(PRINCIPAL, 1001L)).thenReturn(managedPublished())

        mockMvc.perform(
            get("/api/v1/users/me/managed-activities/1001").with(authentication(auth()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.activityId").value("1001"))
            .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
            .andExpect(jsonPath("$.data.description").value("活动介绍"))
            .andExpect(jsonPath("$.data.version").value(1))
    }

    @Test
    fun anonymousManagedActivityListIsRejectedBeforeReadService() {
        mockMvc.perform(get("/api/v1/users/me/managed-activities"))
            .andExpect(status().isUnauthorized)
        verifyNoInteractions(readService)
    }

    @Test
    fun anonymousManagedActivityDetailIsRejectedBeforeReadService() {
        mockMvc.perform(get("/api/v1/users/me/managed-activities/1001"))
            .andExpect(status().isUnauthorized)
        verifyNoInteractions(readService)
    }

    @Test
    fun anonymousCreateIsRejectedBeforeCommandService() {
        mockMvc.perform(
            post("/api/v1/users/me/activities")
                .header("Idempotency-Key", "create-1234")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"个人草稿\"}")
        ).andExpect(status().isUnauthorized)
        verifyNoInteractions(commandService)
    }

    private fun managed(ownerType: String, ownerId: String): ManagedActivityDetailView {
        return ManagedActivityDetailView(
            "1001", "DRAFT", "测试草稿", null, null,
            ActivityOwnerSummaryView(ownerType, ownerId, "测试主体", null),
            null, null, null, null, null, 0, "FREE", 0,
            Instant.parse("2026-08-07T00:00:00Z"),
            null, emptyList<PublicImageView>(), null, null, null, null,
            Instant.parse("2026-08-07T00:00:00Z"), null
        )
    }

    private fun managedPublished(): ManagedActivityDetailView {
        return managedWithStatus("PUBLISHED")
    }

    private fun managedCancelled(): ManagedActivityDetailView {
        return managedWithStatus("CANCELLED")
    }

    private fun managedWithStatus(status: String): ManagedActivityDetailView {
        return ManagedActivityDetailView(
            "1001", status, "测试活动", "HIKING",
            PublicImageView("501", "/api/v1/files/501/content"),
            ActivityOwnerSummaryView("USER", "202", "测试主体", null),
            Instant.parse("2026-08-07T03:00:00Z"),
            Instant.parse("2026-08-07T04:00:00Z"),
            "440305", "活动地址", 20, 0, "FREE", 1,
            Instant.parse("2026-08-07T00:00:00Z"),
            "活动介绍", emptyList<PublicImageView>(),
            Instant.parse("2026-08-07T01:00:00Z"),
            Instant.parse("2026-08-07T02:00:00Z"),
            "报名说明", "组织者留言",
            Instant.parse("2026-08-07T00:00:00Z"),
            Instant.parse("2026-08-07T00:00:00Z")
        )
    }

    private fun managedSummary(): ManagedActivitySummaryView {
        return ManagedActivitySummaryView(
            "1001", "DRAFT", "测试草稿", null, null,
            ActivityOwnerSummaryView("USER", "202", "测试主体", null),
            null, null, null, null, null, 0, "FREE", 0,
            Instant.parse("2026-08-07T00:00:00Z")
        )
    }

    private fun auth(): org.springframework.security.core.Authentication {
        return UsernamePasswordAuthenticationToken(PRINCIPAL, "", listOf())
    }

    companion object {
        private val PRINCIPAL = UserPrincipal(202L, "session-a")
    }
}
