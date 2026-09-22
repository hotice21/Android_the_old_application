package com.eligo.server.post.controller

import com.eligo.server.common.api.CursorPage
import com.eligo.server.common.api.Result
import com.eligo.server.post.dto.PostDraftCreateRequest
import com.eligo.server.post.dto.PostDraftReplaceRequest
import com.eligo.server.post.service.PostCommandService
import com.eligo.server.post.service.PostCreateOutcome
import com.eligo.server.post.service.PostReadService
import com.eligo.server.post.vo.ManagedPostDetailView
import com.eligo.server.post.vo.ManagedPostSummaryView
import com.eligo.server.post.vo.PublicPostView
import com.eligo.server.recommendation.service.RecommendedFeedService
import com.eligo.server.security.UserPrincipal
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class PostController(
    private val commands: PostCommandService,
    private val reads: PostReadService,
    private val recommendedFeed: RecommendedFeedService
) {

    @PostMapping("/api/v1/users/me/posts")
    fun createPersonal(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @RequestBody request: PostDraftCreateRequest
    ): ResponseEntity<Result<ManagedPostDetailView>> =
        created(commands.createPersonal(principal, request, idempotencyKey))

    @PostMapping("/api/v1/organizations/{organizationId}/posts")
    fun createOrganization(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable organizationId: Long,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @RequestBody request: PostDraftCreateRequest
    ): ResponseEntity<Result<ManagedPostDetailView>> =
        created(commands.createOrganization(principal, organizationId, request, idempotencyKey))

    @PutMapping("/api/v1/users/me/posts/{postId}")
    fun replacePersonal(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable postId: Long,
        @RequestBody request: PostDraftReplaceRequest
    ): Result<ManagedPostDetailView> =
        Result.success(commands.replacePersonal(principal, postId, request))

    @PutMapping("/api/v1/organizations/{organizationId}/posts/{postId}")
    fun replaceOrganization(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable organizationId: Long,
        @PathVariable postId: Long,
        @RequestBody request: PostDraftReplaceRequest
    ): Result<ManagedPostDetailView> =
        Result.success(commands.replaceOrganization(principal, organizationId, postId, request))

    @PutMapping("/api/v1/posts/{postId}/publication")
    fun publish(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable postId: Long
    ): Result<ManagedPostDetailView> =
        Result.success(commands.publish(principal, postId))

    @DeleteMapping("/api/v1/posts/{postId}")
    fun delete(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable postId: Long
    ): Result<Void> {
        commands.delete(principal, postId)
        return Result.success()
    }

    @GetMapping("/api/v1/users/me/managed-posts")
    fun listManaged(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) authorType: String?
    ): Result<CursorPage<ManagedPostSummaryView>> =
        Result.success(reads.listManagedPosts(principal, cursor, limit, status, authorType))

    @GetMapping("/api/v1/users/me/managed-posts/{postId}")
    fun getManaged(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable postId: Long
    ): Result<ManagedPostDetailView> =
        Result.success(reads.getManagedPost(principal, postId))

    @GetMapping("/api/v1/posts/{postId}")
    fun get(
        @AuthenticationPrincipal principal: UserPrincipal?,
        @PathVariable postId: Long
    ): Result<PublicPostView> =
        Result.success(reads.getPost(principal, postId))

    @GetMapping("/api/v1/posts")
    fun listPublic(
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int
    ): Result<CursorPage<PublicPostView>> =
        Result.success(reads.listPublicPosts(cursor, limit))

    @GetMapping("/api/v1/users/{userId}/posts")
    fun listUser(
        @AuthenticationPrincipal principal: UserPrincipal?,
        @PathVariable userId: Long,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int
    ): Result<CursorPage<PublicPostView>> =
        Result.success(reads.listUserPosts(principal, userId, cursor, limit))

    @GetMapping("/api/v1/organizations/{organizationId}/posts")
    fun listOrganization(
        @AuthenticationPrincipal principal: UserPrincipal?,
        @PathVariable organizationId: Long,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int
    ): Result<CursorPage<PublicPostView>> =
        Result.success(reads.listOrganizationPosts(principal, organizationId, cursor, limit))

    @GetMapping("/api/v1/feed/following")
    fun followingFeed(
        @AuthenticationPrincipal principal: UserPrincipal?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int
    ): Result<CursorPage<PublicPostView>> =
        Result.success(reads.listFollowingFeed(principal, cursor, limit))

    @GetMapping("/api/v1/feed/recommended")
    fun recommendedFeed(
        @AuthenticationPrincipal principal: UserPrincipal?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int
    ): Result<CursorPage<PublicPostView>> =
        Result.success(recommendedFeed.list(principal, cursor, limit))

    private fun created(outcome: PostCreateOutcome): ResponseEntity<Result<ManagedPostDetailView>> =
        ResponseEntity
            .status(if (outcome.replayed) HttpStatus.OK else HttpStatus.CREATED)
            .body(Result.success(outcome.view))
}
