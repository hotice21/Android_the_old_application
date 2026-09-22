package com.eligo.server.post

import com.eligo.server.activity.vo.PublicImageView
import com.eligo.server.common.api.CursorPage
import com.eligo.server.common.web.GlobalExceptionHandler
import com.eligo.server.config.security.SecurityConfig
import com.eligo.server.post.controller.PostController
import com.eligo.server.post.dto.PostDraftCreateRequest
import com.eligo.server.post.dto.PostDraftReplaceRequest
import com.eligo.server.post.service.PostCommandService
import com.eligo.server.post.service.PostCreateOutcome
import com.eligo.server.post.service.PostReadService
import com.eligo.server.post.vo.ManagedPostDetailView
import com.eligo.server.post.vo.ManagedPostSummaryView
import com.eligo.server.post.vo.PostAuthorSummaryView
import com.eligo.server.post.vo.PublicPostView
import com.eligo.server.recommendation.service.RecommendedFeedService
import com.eligo.server.security.AccountRestrictionFilter
import com.eligo.server.security.AccountRestrictionReader
import com.eligo.server.security.SessionAccessReader
import com.eligo.server.security.SessionAuthenticationFilter
import com.eligo.server.security.UserPrincipal
import java.time.Instant
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.mockito.kotlin.any
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@WebMvcTest(PostController::class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(
    GlobalExceptionHandler::class,
    SecurityConfig::class,
    SessionAuthenticationFilter::class,
    AccountRestrictionFilter::class
)
class PostControllerIntegrationTests {

    private val principal = UserPrincipal(202L, "session-m4")

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var commands: PostCommandService

    @MockitoBean
    lateinit var reads: PostReadService

    @MockitoBean
    lateinit var recommendedFeed: RecommendedFeedService

    @MockitoBean
    lateinit var sessionAccessReader: SessionAccessReader

    @MockitoBean
    lateinit var accountRestrictionReader: AccountRestrictionReader

    @Test
    fun createsPersonalAndOrganizationDraftsWithReplayStatus() {
        whenever(commands.createPersonal(any(), any<PostDraftCreateRequest>(), any()))
            .thenReturn(PostCreateOutcome(managed("DRAFT"), false))
        whenever(commands.createOrganization(any(), any<Long>(), any<PostDraftCreateRequest>(), any()))
            .thenReturn(PostCreateOutcome(managed("DRAFT"), true))

        mockMvc.perform(post("/api/v1/users/me/posts")
            .with(authentication(auth()))
            .header("Idempotency-Key", "personal-key")
            .contentType(MediaType.APPLICATION_JSON)
            .content(draftJson()))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.postId").value("701"))
        mockMvc.perform(post("/api/v1/organizations/401/posts")
            .with(authentication(auth()))
            .header("Idempotency-Key", "organization-key")
            .contentType(MediaType.APPLICATION_JSON)
            .content(draftJson()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
    }

    @Test
    fun replacesPublishesAndDeletesManagedPosts() {
        whenever(commands.replacePersonal(any(), any<Long>(), any<PostDraftReplaceRequest>()))
            .thenReturn(managed("DRAFT"))
        whenever(commands.replaceOrganization(any(), any<Long>(), any<Long>(), any<PostDraftReplaceRequest>()))
            .thenReturn(managed("DRAFT"))
        whenever(commands.publish(principal, 701L)).thenReturn(managed("PUBLISHED"))

        mockMvc.perform(put("/api/v1/users/me/posts/701")
            .with(authentication(auth()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(replaceJson()))
            .andExpect(status().isOk)
        mockMvc.perform(put("/api/v1/organizations/401/posts/701")
            .with(authentication(auth()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(replaceJson()))
            .andExpect(status().isOk)
        mockMvc.perform(put("/api/v1/posts/701/publication")
            .with(authentication(auth())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
        mockMvc.perform(delete("/api/v1/posts/701")
            .with(authentication(auth())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.code").value(0))
    }

    @Test
    fun exposesAuthenticatedManagedPostsAndFollowingFeed() {
        whenever(reads.listManagedPosts(principal, "next", 10, "DRAFT", "USER"))
            .thenReturn(CursorPage(listOf(summary()), null, false))
        whenever(reads.getManagedPost(principal, 701L)).thenReturn(managed("DRAFT"))
        whenever(reads.listFollowingFeed(principal, null, 20))
            .thenReturn(CursorPage(listOf(publicPost()), null, false))

        mockMvc.perform(get("/api/v1/users/me/managed-posts")
            .param("cursor", "next")
            .param("limit", "10")
            .param("status", "DRAFT")
            .param("authorType", "USER")
            .with(authentication(auth())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].postId").value("701"))
        mockMvc.perform(get("/api/v1/users/me/managed-posts/701")
            .with(authentication(auth())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.version").value(1))
        mockMvc.perform(get("/api/v1/feed/following")
            .with(authentication(auth())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].visibility").value("PUBLIC"))
    }

    @Test
    fun permitsAnonymousPublicDetailAndLists() {
        whenever(reads.getPost(null, 701L)).thenReturn(publicPost())
        whenever(reads.listPublicPosts(null, 20))
            .thenReturn(CursorPage(listOf(publicPost()), null, false))
        whenever(reads.listUserPosts(null, 303L, null, 20))
            .thenReturn(CursorPage(listOf(publicPost()), null, false))
        whenever(reads.listOrganizationPosts(null, 401L, null, 20))
            .thenReturn(CursorPage(listOf(publicPost()), null, false))

        mockMvc.perform(get("/api/v1/posts/701"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.postId").value("701"))
        mockMvc.perform(get("/api/v1/posts"))
            .andExpect(status().isOk)
        mockMvc.perform(get("/api/v1/users/303/posts"))
            .andExpect(status().isOk)
        mockMvc.perform(get("/api/v1/organizations/401/posts"))
            .andExpect(status().isOk)
    }

    @Test
    fun exposesRecommendedFeedToAnonymousAndAuthenticatedReaders() {
        whenever(recommendedFeed.list(null, null, 20))
            .thenReturn(CursorPage(listOf(publicPost()), null, false))
        whenever(recommendedFeed.list(principal, null, 20))
            .thenReturn(CursorPage(listOf(publicPost()), null, false))

        mockMvc.perform(get("/api/v1/feed/recommended"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].postId").value("701"))
        mockMvc.perform(get("/api/v1/feed/recommended")
            .with(authentication(auth())))
            .andExpect(status().isOk)
    }

    @Test
    fun rejectsAnonymousWriteManageAndFeedOperations() {
        mockMvc.perform(post("/api/v1/users/me/posts")
            .header("Idempotency-Key", "key")
            .contentType(MediaType.APPLICATION_JSON)
            .content(draftJson()))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/v1/users/me/managed-posts"))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/v1/feed/following"))
            .andExpect(status().isUnauthorized)
        verifyNoInteractions(commands)
    }

    private fun managed(status: String): ManagedPostDetailView {
        return ManagedPostDetailView(
            "701", author(), status, "PUBLIC", "周末徒步", "一起出发",
            listOf(PublicImageView("801", "/api/v1/files/801/content")),
            null, 1, if ("PUBLISHED" == status) NOW else null, NOW, NOW, null)
    }

    private fun summary(): ManagedPostSummaryView {
        return ManagedPostSummaryView(
            "701", author(), "DRAFT", "PUBLIC", "周末徒步", "一起出发",
            emptyList(), null, 1, null, NOW)
    }

    private fun publicPost(): PublicPostView {
        return PublicPostView(
            "701", author(), "PUBLIC", "周末徒步", "一起出发",
            emptyList(), null, NOW)
    }

    private fun author(): PostAuthorSummaryView {
        return PostAuthorSummaryView("USER", "202", "山友", null)
    }

    private fun auth(): Authentication {
        return UsernamePasswordAuthenticationToken(principal, "", emptyList())
    }

    private fun draftJson(): String {
        return "{\"title\":\"周末徒步\",\"content\":\"一起出发\"," +
            "\"visibility\":\"PUBLIC\",\"mediaFileIds\":[]}"
    }

    private fun replaceJson(): String {
        return "{\"version\":1,\"title\":\"周末徒步\",\"content\":\"一起出发\"," +
            "\"visibility\":\"PUBLIC\",\"mediaFileIds\":[]}"
    }

    companion object {
        private val NOW = Instant.parse("2026-08-16T03:04:05Z")
    }
}
