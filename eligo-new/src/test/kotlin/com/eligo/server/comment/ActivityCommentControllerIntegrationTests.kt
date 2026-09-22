package com.eligo.server.comment

import com.eligo.server.comment.controller.ActivityCommentController
import com.eligo.server.comment.dto.ActivityCommentCreateRequest
import com.eligo.server.comment.service.ActivityCommentCreateOutcome
import com.eligo.server.comment.service.ActivityCommentService
import com.eligo.server.comment.vo.ActivityCommentAuthorView
import com.eligo.server.comment.vo.ActivityCommentView
import com.eligo.server.common.api.CursorPage
import com.eligo.server.common.web.GlobalExceptionHandler
import com.eligo.server.config.security.SecurityConfig
import com.eligo.server.security.AccountRestrictionFilter
import com.eligo.server.security.AccountRestrictionReader
import com.eligo.server.security.SessionAccessReader
import com.eligo.server.security.SessionAuthenticationFilter
import com.eligo.server.security.UserPrincipal
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@WebMvcTest(ActivityCommentController::class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(
    GlobalExceptionHandler::class,
    SecurityConfig::class,
    SessionAuthenticationFilter::class,
    AccountRestrictionFilter::class
)
class ActivityCommentControllerIntegrationTests {

    private val principal = UserPrincipal(202L, "comment-session")
    private val now = Instant.parse("2026-08-22T08:00:00Z")

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var comments: ActivityCommentService

    @MockitoBean
    private lateinit var sessionAccessReader: SessionAccessReader

    @MockitoBean
    private lateinit var accountRestrictionReader: AccountRestrictionReader

    @Test
    fun createReturns201AndIdempotentReplayReturns200() {
        val view = view(false)
        whenever(
            comments.create(
                principal,
                301L,
                ActivityCommentCreateRequest("请问几点集合？", null),
                "comment-key-1"
            )
        ).thenReturn(ActivityCommentCreateOutcome(view, false))

        mockMvc.perform(
            post("/api/v1/activities/301/comments")
                .header("Idempotency-Key", "comment-key-1")
                .contentType("application/json")
                .content("{\"content\":\"请问几点集合？\"}")
                .with(authentication(auth()))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.commentId").value("501"))

        whenever(
            comments.create(
                principal,
                301L,
                ActivityCommentCreateRequest("请问几点集合？", null),
                "comment-key-1"
            )
        ).thenReturn(ActivityCommentCreateOutcome(view, true))
        mockMvc.perform(
            post("/api/v1/activities/301/comments")
                .header("Idempotency-Key", "comment-key-1")
                .contentType("application/json")
                .content("{\"content\":\"请问几点集合？\"}")
                .with(authentication(auth()))
        )
            .andExpect(status().isOk)
    }

    @Test
    fun listIsAnonymousAndDeleteRequiresAuthentication() {
        whenever(comments.list(301L, null, 20))
            .thenReturn(CursorPage(listOf(view(false)), null, false))
        whenever(comments.delete(principal, 301L, 501L)).thenReturn(view(true))

        mockMvc.perform(get("/api/v1/activities/301/comments"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].content").value("请问几点集合？"))
        mockMvc.perform(delete("/api/v1/activities/301/comments/501"))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(
            delete("/api/v1/activities/301/comments/501")
                .with(authentication(auth()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.deleted").value(true))
            .andExpect(jsonPath("$.data.content").isEmpty)
            .andExpect(jsonPath("$.data.author").isEmpty)
    }

    @Test
    fun anonymousCreateIsRejectedBeforeService() {
        mockMvc.perform(
            post("/api/v1/activities/301/comments")
                .header("Idempotency-Key", "comment-key-1")
                .contentType("application/json")
                .content("{\"content\":\"留言\"}")
        )
            .andExpect(status().isUnauthorized)
        verifyNoInteractions(comments)
    }

    @Test
    fun acceptsFiveHundredUnicodeCodePointsBeforeServiceValidation() {
        val content = "\uD83D\uDE00".repeat(500)
        whenever(
            comments.create(
                principal,
                301L,
                ActivityCommentCreateRequest(content, null),
                "comment-key-unicode"
            )
        ).thenReturn(ActivityCommentCreateOutcome(view(false), false))

        mockMvc.perform(
            post("/api/v1/activities/301/comments")
                .header("Idempotency-Key", "comment-key-unicode")
                .contentType("application/json")
                .content("{\"content\":\"$content\"}")
                .with(authentication(auth()))
        )
            .andExpect(status().isCreated)
    }

    private fun view(deleted: Boolean): ActivityCommentView {
        return ActivityCommentView(
            "501",
            "301",
            null,
            deleted,
            if (deleted) null else "请问几点集合？",
            if (deleted) null else ActivityCommentAuthorView("202", "测试用户", null),
            now,
            if (deleted) now.plusSeconds(60) else null
        )
    }

    private fun auth(): Authentication {
        return UsernamePasswordAuthenticationToken(principal, "", listOf())
    }
}
