package com.eligo.server.comment.controller

import com.eligo.server.comment.dto.ActivityCommentCreateRequest
import com.eligo.server.comment.service.ActivityCommentCreateOutcome
import com.eligo.server.comment.service.ActivityCommentService
import com.eligo.server.comment.vo.ActivityCommentView
import com.eligo.server.common.api.CursorPage
import com.eligo.server.common.api.Result
import com.eligo.server.security.UserPrincipal
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class ActivityCommentController(private val comments: ActivityCommentService) {

    @PostMapping("/api/v1/activities/{activityId}/comments")
    fun create(
        @AuthenticationPrincipal principal: UserPrincipal?,
        @PathVariable activityId: Long,
        @RequestHeader("Idempotency-Key") idempotencyKey: String?,
        @Valid @RequestBody request: ActivityCommentCreateRequest?
    ): ResponseEntity<Result<ActivityCommentView>> {
        val outcome = comments.create(principal!!, activityId, request!!, idempotencyKey!!)
        return ResponseEntity
            .status(if (outcome.replayed) HttpStatus.OK else HttpStatus.CREATED)
            .body(Result.success(outcome.view))
    }

    @GetMapping("/api/v1/activities/{activityId}/comments")
    fun list(
        @PathVariable activityId: Long,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int
    ): Result<CursorPage<ActivityCommentView>> =
        Result.success(comments.list(activityId, cursor, limit))

    @DeleteMapping("/api/v1/activities/{activityId}/comments/{commentId}")
    fun delete(
        @AuthenticationPrincipal principal: UserPrincipal?,
        @PathVariable activityId: Long,
        @PathVariable commentId: Long
    ): Result<ActivityCommentView> =
        Result.success(comments.delete(principal!!, activityId, commentId))
}
