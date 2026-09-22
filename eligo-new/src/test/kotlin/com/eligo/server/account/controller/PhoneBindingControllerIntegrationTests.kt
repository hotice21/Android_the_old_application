package com.eligo.server.account.controller

import com.eligo.server.account.service.PhoneBindingService
import com.eligo.server.account.vo.PhoneBindingView
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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import java.time.Instant
import org.hamcrest.Matchers.nullValue
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(PhoneBindingController::class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(
    GlobalExceptionHandler::class,
    SecurityConfig::class,
    SessionAuthenticationFilter::class,
    AccountRestrictionFilter::class
)
class PhoneBindingControllerIntegrationTests {

    companion object {
        private val PRINCIPAL: UserPrincipal = UserPrincipal(202L, "session-a")
    }

    @Autowired private lateinit var mockMvc: MockMvc
    @MockitoBean private lateinit var service: PhoneBindingService
    @MockitoBean private lateinit var sessionAccessReader: SessionAccessReader
    @MockitoBean private lateinit var accountRestrictionReader: AccountRestrictionReader

    @Test
    fun getsUnboundStateWithoutLeakingOtherFields() {
        `when`(service.current(PRINCIPAL)).thenReturn(PhoneBindingView.unbound())

        mockMvc.perform(get("/api/v1/account/phone-binding").with(authentication(auth())))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.bound").value(false))
                .andExpect(jsonPath("$.data.countryCode").hasJsonPath())
                .andExpect(jsonPath("$.data.countryCode").value(nullValue()))
                .andExpect(jsonPath("$.data.maskedPhone").hasJsonPath())
                .andExpect(jsonPath("$.data.maskedPhone").value(nullValue()))
                .andExpect(jsonPath("$.data.boundAt").hasJsonPath())
                .andExpect(jsonPath("$.data.boundAt").value(nullValue()))
    }

    @Test
    fun bindsWithPhoneCodeAndReturnsOnlyMaskedPhone() {
        `when`(service.bindOrReplace(PRINCIPAL, "phone-code"))
            .thenReturn(
                    PhoneBindingView(
                            true,
                            "86",
                            "138****1234",
                            Instant.parse("2026-07-22T08:00:00Z")))

        mockMvc.perform(
                        put("/api/v1/account/phone-binding")
                                .with(authentication(auth()))
                                .contentType("application/json")
                                .content("{\"phoneCode\":\"phone-code\"}"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.bound").value(true))
                .andExpect(jsonPath("$.data.countryCode").value("86"))
                .andExpect(jsonPath("$.data.maskedPhone").value("138****1234"))
                .andExpect(jsonPath("$.data.phone").doesNotExist())
                .andExpect(jsonPath("$.data.phoneCode").doesNotExist())
        verify(service).bindOrReplace(PRINCIPAL, "phone-code")
    }

    @Test
    fun rejectsBlankPhoneCodeBeforeService() {
        mockMvc.perform(
                        put("/api/v1/account/phone-binding")
                                .with(authentication(auth()))
                                .contentType("application/json")
                                .content("{\"phoneCode\":\" \"}"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value(10001))
    }

    @Test
    fun conflictAndRepeatedUnbindUseConfirmedErrors() {
        `when`(service.bindOrReplace(PRINCIPAL, "phone-code"))
            .thenThrow(BusinessException(AccountUserFileErrorCode.PHONE_ALREADY_BOUND))
        mockMvc.perform(
                        put("/api/v1/account/phone-binding")
                                .with(authentication(auth()))
                                .contentType("application/json")
                                .content("{\"phoneCode\":\"phone-code\"}"))
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.code").value(11101))

        org.mockito.Mockito.doThrow(
                        BusinessException(AccountUserFileErrorCode.PHONE_NOT_BOUND))
                .`when`(service)
                .unbind(PRINCIPAL)
        mockMvc.perform(delete("/api/v1/account/phone-binding").with(authentication(auth())))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.code").value(11102))
    }

    @Test
    fun unbindReturnsNullData() {
        mockMvc.perform(delete("/api/v1/account/phone-binding").with(authentication(auth())))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data").doesNotExist())
        verify(service).unbind(PRINCIPAL)
    }

    private fun auth(): org.springframework.security.core.Authentication {
        return UsernamePasswordAuthenticationToken(PRINCIPAL, "", listOf())
    }
}
