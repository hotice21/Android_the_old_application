package com.eligo.server.agreement

import com.eligo.server.agreement.controller.AgreementController
import com.eligo.server.agreement.entity.AgreementType
import com.eligo.server.agreement.service.AgreementService
import com.eligo.server.agreement.vo.AgreementConsentView
import com.eligo.server.agreement.vo.AgreementDetail
import com.eligo.server.agreement.vo.AgreementSummary
import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.web.GlobalExceptionHandler
import com.eligo.server.config.security.SecurityConfig
import com.eligo.server.security.AccountRestrictionFilter
import com.eligo.server.security.AccountRestrictionReader
import com.eligo.server.security.SessionAccessReader
import com.eligo.server.security.SessionAuthenticationFilter
import com.eligo.server.security.UserPrincipal
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.whenever
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.Optional
import java.util.OptionalLong

@WebMvcTest(AgreementController::class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(
    GlobalExceptionHandler::class, SecurityConfig::class,
    SessionAuthenticationFilter::class, AccountRestrictionFilter::class
)
class AgreementControllerIntegrationTests {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var agreementService: AgreementService

    @MockitoBean
    private lateinit var sessionAccessReader: SessionAccessReader

    @MockitoBean
    private lateinit var accountRestrictionReader: AccountRestrictionReader

    @Test
    fun anonymousCanQueryCurrentAgreements() {
        whenever(agreementService.current(OptionalLong.empty(), Optional.empty())).thenReturn(
            listOf(
                AgreementSummary(
                    "101", AgreementType.USER_AGREEMENT, "1.0", "用户协议",
                    true, Instant.parse("2026-07-21T07:00:00Z"), null
                )
            )
        )

        mockMvc.perform(get("/api/v1/agreements/current"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].agreementId").value("101"))
            .andExpect(jsonPath("$.data.items[0].agreed").doesNotExist())
    }

    @Test
    fun anonymousCanReadEffectiveAgreementDetail() {
        whenever(agreementService.detail(OptionalLong.empty(), 101L)).thenReturn(
            AgreementDetail(
                "101", AgreementType.USER_AGREEMENT, "1.0", "用户协议", true,
                Instant.parse("2026-07-21T07:00:00Z"), null, "协议正文", "68617368"
            )
        )

        mockMvc.perform(get("/api/v1/agreements/101"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.content").value("协议正文"))
    }

    @Test
    fun inactiveAgreementReturnsStableError() {
        whenever(agreementService.detail(OptionalLong.empty(), 103L))
            .thenThrow(BusinessException(AccountUserFileErrorCode.AGREEMENT_NOT_FOUND_OR_INACTIVE))

        mockMvc.perform(get("/api/v1/agreements/103"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value(11302))
    }

    @Test
    fun firstConsentReturnsCreatedAndDoesNotExposeInternalCreatedFlag() {
        val principal = UserPrincipal(202L, "session-a")
        whenever(accountRestrictionReader.read(202L))
            .thenReturn(AccountRestrictionReader.State(1, true, listOf(101L)))
        whenever(agreementService.consent(principal, 101L)).thenReturn(
            AgreementConsentView("101", Instant.parse("2026-07-21T08:00:00Z"), true)
        )

        mockMvc.perform(
            post("/api/v1/agreements/101/consents")
                .with(authentication(UsernamePasswordAuthenticationToken(principal, "", listOf())))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.agreementId").value("101"))
            .andExpect(jsonPath("$.data.agreedAt").value("2026-07-21T08:00:00Z"))
            .andExpect(jsonPath("$.data.created").doesNotExist())

        verify(agreementService).consent(principal, 101L)
    }

    @Test
    fun repeatedConsentReturnsOkWithOriginalFact() {
        val principal = UserPrincipal(202L, "session-a")
        whenever(accountRestrictionReader.read(202L))
            .thenReturn(AccountRestrictionReader.State(1, true, listOf()))
        whenever(agreementService.consent(principal, 101L)).thenReturn(
            AgreementConsentView("101", Instant.parse("2026-07-21T08:00:00Z"), false)
        )

        mockMvc.perform(
            post("/api/v1/agreements/101/consents")
                .with(authentication(UsernamePasswordAuthenticationToken(principal, "", listOf())))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.agreedAt").value("2026-07-21T08:00:00Z"))
    }
}
