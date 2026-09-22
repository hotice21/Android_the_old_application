package com.eligo.server.security.attack

import com.eligo.server.activity.controller.ActivityController
import com.eligo.server.activity.service.ActivityCommandService
import com.eligo.server.activity.service.ActivityReadService
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.common.web.GlobalExceptionHandler
import com.eligo.server.config.security.SecurityConfig
import com.eligo.server.security.AccountRestrictionFilter
import com.eligo.server.security.AccountRestrictionReader
import com.eligo.server.security.SessionAccessReader
import com.eligo.server.security.SessionAuthenticationFilter
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.Base64
import java.util.Date

/**
 * 漏洞测试：水平越权（IDOR）。
 *
 * 用户 202 尝试读取 / 修改 / 删除不属于自己的活动（999）。
 * 服务层检测到归属不符时必须抛出 403，请求不得成功。
 */
@WebMvcTest(ActivityController::class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(
    GlobalExceptionHandler::class,
    SecurityConfig::class,
    SessionAuthenticationFilter::class,
    AccountRestrictionFilter::class
)
class IdorAttackTests {

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
    fun cannotReadAnotherUsersManagedActivity() {
        whenever(readService.getManagedActivity(any(), any()))
            .thenThrow(BusinessException(CommonErrorCode.ACCESS_DENIED))

        mockMvc.perform(
            get("/api/v1/users/me/managed-activities/999")
                .header("Authorization", "Bearer ${validUserToken()}")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value(10101))
    }

    @Test
    fun cannotUpdateAnotherUsersActivity() {
        whenever(commandService.updatePersonal(any(), any(), any()))
            .thenThrow(BusinessException(CommonErrorCode.ACCESS_DENIED))

        mockMvc.perform(
            put("/api/v1/users/me/activities/999")
                .header("Authorization", "Bearer ${validUserToken()}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"version":0,"title":"越权修改"}""")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value(10101))
    }

    @Test
    fun cannotDeleteAnotherUsersActivity() {
        whenever(commandService.deleteDraft(any(), any()))
            .thenThrow(BusinessException(CommonErrorCode.ACCESS_DENIED))

        mockMvc.perform(
            delete("/api/v1/activities/999")
                .header("Authorization", "Bearer ${validUserToken()}")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value(10101))
    }

    @Test
    fun cannotPublishAnotherUsersActivity() {
        whenever(commandService.publish(any(), any()))
            .thenThrow(BusinessException(CommonErrorCode.ACCESS_DENIED))

        mockMvc.perform(
            put("/api/v1/activities/999/publication")
                .header("Authorization", "Bearer ${validUserToken()}")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value(10101))
    }

    private fun validUserToken(): String {
        val claims = JWTClaimsSet.Builder()
            .issuer("eligo")
            .subject("202")
            .claim("sid", "session-a")
            .issueTime(Date.from(Instant.now().minusSeconds(30)))
            .expirationTime(Date.from(Instant.now().plusSeconds(900)))
            .build()
        val token = SignedJWT(JWSHeader(JWSAlgorithm.HS256), claims)
        token.sign(MACSigner(Base64.getDecoder().decode(SIGNING_KEY_BASE64)))
        return token.serialize()
    }

    companion object {
        private const val SIGNING_KEY_BASE64 =
            "IB8eHRwbGhkYFxYVFBMSERAPDg0MCwoJCAcGBQQDAgE="
    }
}
