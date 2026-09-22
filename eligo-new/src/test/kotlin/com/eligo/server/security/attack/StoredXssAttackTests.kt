package com.eligo.server.security.attack

import com.eligo.server.comment.controller.ActivityCommentController
import com.eligo.server.comment.service.ActivityCommentCreateOutcome
import com.eligo.server.comment.service.ActivityCommentService
import com.eligo.server.comment.vo.ActivityCommentAuthorView
import com.eligo.server.comment.vo.ActivityCommentView
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.Base64
import java.util.Date

/**
 * 漏洞测试：存储型 XSS。
 *
 * 攻击者通过评论提交脚本 payload。服务端必须：
 * 1. 以 application/json 返回（而非 text/html），payload 作为纯字符串字段，
 *    浏览器不会将其作为脚本执行；
 * 2. 原样存储与返回，由前端 Vue 文本插值完成转义。
 */
@WebMvcTest(ActivityCommentController::class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(
    GlobalExceptionHandler::class,
    SecurityConfig::class,
    SessionAuthenticationFilter::class,
    AccountRestrictionFilter::class
)
class StoredXssAttackTests {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var comments: ActivityCommentService

    @MockitoBean
    private lateinit var sessionAccessReader: SessionAccessReader

    @MockitoBean
    private lateinit var accountRestrictionReader: AccountRestrictionReader

    private val xssPayloads = listOf(
        "<script>alert(document.cookie)</script>",
        "\"><img src=x onerror=alert(1)>",
        "<svg/onload=alert('xss')>",
        "javascript:alert(1)"
    )

    @Test
    fun xssPayloadsReturnedAsJsonStringNotExecutableHtml() {
        xssPayloads.forEachIndexed { index, payload ->
            whenever(comments.create(any(), any(), any(), any()))
                .thenReturn(ActivityCommentCreateOutcome(commentView(5001L + index, payload), false))

            mockMvc.perform(
                post("/api/v1/activities/100/comments")
                    .header("Authorization", "Bearer ${validUserToken()}")
                    .header("Idempotency-Key", "idem-xss-$index")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"content\":\"${escapeJson(payload)}\"}")
            )
                .andExpect(status().isCreated)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.content").value(payload))
        }
    }

    private fun commentView(id: Long, payload: String): ActivityCommentView =
        ActivityCommentView(
            commentId = id.toString(),
            activityId = "100",
            parentCommentId = null,
            deleted = false,
            content = payload,
            author = ActivityCommentAuthorView("202", "测试用户", null),
            createdAt = Instant.parse("2026-09-21T10:00:00Z"),
            deletedAt = null
        )

    private fun escapeJson(value: String): String =
        value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")

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
