package com.eligo.server.organization

import com.eligo.server.common.web.GlobalExceptionHandler
import com.eligo.server.config.security.SecurityConfig
import com.eligo.server.organization.controller.OrganizationAccessController
import com.eligo.server.organization.service.OrganizationAccessService
import com.eligo.server.organization.vo.MyOrganizationItemsView
import com.eligo.server.organization.vo.MyOrganizationSummaryView
import com.eligo.server.organization.vo.PublicOrganizationDetailView
import com.eligo.server.organization.vo.UserCapabilitiesView
import com.eligo.server.security.AccountRestrictionFilter
import com.eligo.server.security.AccountRestrictionReader
import com.eligo.server.security.SessionAccessReader
import com.eligo.server.security.SessionAuthenticationFilter
import com.eligo.server.security.UserPrincipal
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@WebMvcTest(OrganizationAccessController::class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(
    GlobalExceptionHandler::class,
    SecurityConfig::class,
    SessionAuthenticationFilter::class,
    AccountRestrictionFilter::class
)
class OrganizationAccessControllerIntegrationTests {

    private val principal = UserPrincipal(202L, "session-a")

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var service: OrganizationAccessService

    @MockitoBean
    lateinit var sessionAccessReader: SessionAccessReader

    @MockitoBean
    lateinit var accountRestrictionReader: AccountRestrictionReader

    @Test
    fun returnsCapabilitiesFromBackendCalculation() {
        whenever(service.getMyCapabilities(principal))
            .thenReturn(
                UserCapabilitiesView(
                    true,
                    listOf(
                        "PUBLIC_READ",
                        "PUBLISH_PERSONAL_ACTIVITY",
                        "PARTICIPATE")))

        mockMvc.perform(
            get("/api/v1/users/me/capabilities")
                .with(authentication(auth())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.profileCompleted").value(true))
            .andExpect(jsonPath("$.data.capabilities[0]").value("PUBLIC_READ"))
            .andExpect(
                jsonPath("$.data.capabilities[2]")
                    .value("PARTICIPATE"))
    }

    @Test
    fun returnsOnlySafeOrganizationSummary() {
        whenever(service.listMyOrganizations(principal))
            .thenReturn(
                MyOrganizationItemsView(
                    listOf(
                        MyOrganizationSummaryView(
                            "401",
                            "山海户外",
                            MyOrganizationSummaryView.AvatarView(
                                "901",
                                "/api/v1/files/901/content"),
                            "OWNER"))))

        mockMvc.perform(
            get("/api/v1/users/me/organizations")
                .with(authentication(auth())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].organizationId").value("401"))
            .andExpect(jsonPath("$.data.items[0].name").value("山海户外"))
            .andExpect(jsonPath("$.data.items[0].avatar.fileId").value("901"))
            .andExpect(jsonPath("$.data.items[0].role").value("OWNER"))
            .andExpect(jsonPath("$.data.items[0].contactPhone").doesNotExist())
            .andExpect(jsonPath("$.data.items[0].status").doesNotExist())
    }

    @Test
    fun returnsPublicOrganizationDetailToAnonymousVisitor() {
        whenever(service.getPublicOrganization(401L))
            .thenReturn(
                PublicOrganizationDetailView(
                    "401",
                    "山海户外",
                    PublicOrganizationDetailView.AvatarView(
                        "901", "/api/v1/files/901/content"),
                    "公开企业简介",
                    PublicOrganizationDetailView.RegionView(
                        "44", "广东省", "4403", "深圳市",
                        "440305", "南山区"),
                    "公开地址",
                    4L))

        mockMvc.perform(get("/api/v1/organizations/401"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.organizationId").value("401"))
            .andExpect(jsonPath("$.data.name").value("山海户外"))
            .andExpect(jsonPath("$.data.summary").value("公开企业简介"))
            .andExpect(jsonPath("$.data.region.districtCode").value("440305"))
            .andExpect(jsonPath("$.data.addressDetail").value("公开地址"))
            .andExpect(jsonPath("$.data.followerCount").value(4))
            .andExpect(jsonPath("$.data.contactPhone").doesNotExist())
            .andExpect(jsonPath("$.data.status").doesNotExist())
    }

    @Test
    fun anonymousCapabilityRequestIsRejected() {
        mockMvc.perform(get("/api/v1/users/me/capabilities"))
            .andExpect(status().isUnauthorized)
        verifyNoInteractions(service)
    }

    private fun auth(): Authentication {
        return UsernamePasswordAuthenticationToken(principal, "", emptyList())
    }
}
