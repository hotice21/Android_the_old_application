package com.eligo.server.participation.controller

import com.eligo.server.common.api.CursorPage
import com.eligo.server.common.api.Result
import com.eligo.server.participation.service.ParticipationCommandService
import com.eligo.server.participation.service.ParticipationReadService
import com.eligo.server.participation.vo.ActivityParticipantSummaryView
import com.eligo.server.participation.vo.ActivityParticipationView
import com.eligo.server.participation.vo.MyParticipationView
import com.eligo.server.security.UserPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class ParticipationController(
    private val commandService: ParticipationCommandService,
    private val readService: ParticipationReadService
) {

    @PutMapping("/api/v1/activities/{activityId}/participation")
    fun join(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable activityId: Long
    ): Result<ActivityParticipationView> =
        Result.success(commandService.join(principal, activityId))

    @DeleteMapping("/api/v1/activities/{activityId}/participation")
    fun cancel(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable activityId: Long
    ): Result<ActivityParticipationView> =
        Result.success(commandService.cancel(principal, activityId))

    @GetMapping("/api/v1/activities/{activityId}/participants")
    fun listParticipants(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable activityId: Long,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int
    ): Result<CursorPage<ActivityParticipantSummaryView>> =
        Result.success(readService.listParticipants(principal, activityId, cursor, limit))

    @DeleteMapping("/api/v1/activities/{activityId}/participants/{userId}")
    fun remove(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable activityId: Long,
        @PathVariable userId: Long
    ): Result<ActivityParticipationView> =
        Result.success(commandService.remove(principal, activityId, userId))

    @GetMapping("/api/v1/users/me/participations")
    fun listMine(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) keyword: String?
    ): Result<CursorPage<MyParticipationView>> =
        Result.success(readService.listMyParticipations(principal, cursor, limit, status, keyword))
}
