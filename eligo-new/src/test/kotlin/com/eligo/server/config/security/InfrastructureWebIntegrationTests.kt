package com.eligo.server.config.security

import com.eligo.server.common.web.RequestTraceFilter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InfrastructureWebIntegrationTests {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jwtProperties: JwtProperties

    @Test
    fun livenessEndpointIsPublicAndDoesNotExposeDetails() {
        mockMvc.perform(get("/actuator/health/liveness"))
            .andExpect(status().isOk)
            .andExpect(header().exists(RequestTraceFilter.REQUEST_ID_HEADER))
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components").doesNotExist())
    }

    @Test
    fun readinessEndpointIsPublicAndDoesNotExposeDetails() {
        mockMvc.perform(get("/actuator/health/readiness"))
            .andExpect(status().isOk)
            .andExpect(header().exists(RequestTraceFilter.REQUEST_ID_HEADER))
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components").doesNotExist())
    }

    @Test
    fun jwtBoundaryPropertiesAreBound() {
        assertThat(jwtProperties.issuer).isEqualTo("eligo")
        assertThat(jwtProperties.accessTokenTtl).isEqualTo(Duration.ofMinutes(15))
        assertThat(jwtProperties.clockSkew).isEqualTo(Duration.ofSeconds(30))
    }
}
