package com.eligo.server.activity.controller

import com.eligo.server.activity.dto.OrganizationActivityCreateRequest
import com.eligo.server.activity.dto.OrganizationActivityUpdateRequest
import com.eligo.server.activity.dto.PersonalActivityCreateRequest
import com.eligo.server.activity.dto.PersonalActivityUpdateRequest
import com.eligo.server.activity.service.ActivityCommandService
import com.eligo.server.activity.service.ActivityCreateOutcome
import com.eligo.server.activity.service.ActivityReadService
import com.eligo.server.activity.vo.ActivityMapView
import com.eligo.server.activity.vo.ManagedActivityDetailView
import com.eligo.server.activity.vo.ManagedActivitySummaryView
import com.eligo.server.activity.vo.PublicActivityDetailView
import com.eligo.server.activity.vo.PublicActivitySummaryView
import com.eligo.server.common.api.CursorPage
import com.eligo.server.common.api.Result
import com.eligo.server.security.UserPrincipal
import jakarta.validation.Valid
import java.math.BigDecimal
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
class ActivityController(
    private val service: ActivityReadService,
    private val commandService: ActivityCommandService
) {

    @GetMapping("/api/v1/activities")
    fun listPublicActivities(
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) categoryCode: String?,
        @RequestParam(required = false) regionCode: String?,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) topic: String?,
        @RequestParam(required = false) userLatitude: BigDecimal?,
        @RequestParam(required = false) userLongitude: BigDecimal?,
        @RequestParam(required = false) radiusMeters: Int?
    ): Result<CursorPage<PublicActivitySummaryView>> {
        if (topic == null &&
            userLatitude == null && userLongitude == null && radiusMeters == null
        ) {
            return Result.success(
                service.listPublicActivities(cursor, limit, status, categoryCode, regionCode, keyword)
            )
        }
        if (topic == null) {
            return Result.success(
                service.listPublicActivities(
                    cursor, limit, status, categoryCode, regionCode, keyword,
                    userLatitude, userLongitude, radiusMeters
                )
            )
        }
        return Result.success(
            service.listPublicActivities(
                cursor, limit, status, categoryCode, regionCode, keyword,
                topic, userLatitude, userLongitude, radiusMeters
            )
        )
    }

    @GetMapping("/api/v1/activities/map")
    fun listActivitiesOnMap(
        @RequestParam(required = false) minLatitude: BigDecimal?,
        @RequestParam(required = false) maxLatitude: BigDecimal?,
        @RequestParam(required = false) minLongitude: BigDecimal?,
        @RequestParam(required = false) maxLongitude: BigDecimal?,
        @RequestParam(required = false) userLatitude: BigDecimal?,
        @RequestParam(required = false) userLongitude: BigDecimal?,
        @RequestParam(required = false) radiusMeters: Int?,
        @RequestParam(required = false) categoryCode: String?,
        @RequestParam(defaultValue = "100") limit: Int
    ): Result<ActivityMapView> {
        if (userLatitude == null && userLongitude == null && radiusMeters == null) {
            return Result.success(
                service.listActivitiesOnMap(
                    minLatitude, maxLatitude, minLongitude, maxLongitude, categoryCode, limit
                )
            )
        }
        return Result.success(
            service.listActivitiesOnMap(
                minLatitude, maxLatitude, minLongitude, maxLongitude,
                userLatitude, userLongitude, radiusMeters, categoryCode, limit
            )
        )
    }

    @GetMapping("/api/v1/activities/{activityId}")
    fun getPublicActivity(
        @AuthenticationPrincipal principal: UserPrincipal?,
        @PathVariable activityId: Long
    ): Result<PublicActivityDetailView> {
        return Result.success(service.getPublicActivity(principal, activityId))
    }

    @GetMapping("/api/v1/users/me/managed-activities")
    fun listManagedActivities(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) ownerType: String?
    ): Result<CursorPage<ManagedActivitySummaryView>> {
        return Result.success(
            service.listManagedActivities(principal, cursor, limit, status, ownerType)
        )
    }

    @GetMapping("/api/v1/users/me/managed-activities/{activityId}")
    fun getManagedActivity(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable activityId: Long
    ): Result<ManagedActivityDetailView> {
        return Result.success(service.getManagedActivity(principal, activityId))
    }

    @PostMapping("/api/v1/users/me/activities")
    fun createPersonalActivity(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody request: PersonalActivityCreateRequest
    ): ResponseEntity<Result<ManagedActivityDetailView>> {
        val outcome: ActivityCreateOutcome = commandService.createPersonal(
            principal, request, idempotencyKey
        )
        return ResponseEntity
            .status(if (outcome.replayed) HttpStatus.OK else HttpStatus.CREATED)
            .body(Result.success(outcome.view))
    }

    @PostMapping("/api/v1/organizations/{organizationId}/activities")
    fun createOrganizationActivity(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable organizationId: Long,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody request: OrganizationActivityCreateRequest
    ): ResponseEntity<Result<ManagedActivityDetailView>> {
        val outcome: ActivityCreateOutcome = commandService.createOrganization(
            principal, organizationId, request, idempotencyKey
        )
        return ResponseEntity
            .status(if (outcome.replayed) HttpStatus.OK else HttpStatus.CREATED)
            .body(Result.success(outcome.view))
    }

    @PutMapping("/api/v1/users/me/activities/{activityId}")
    fun updatePersonalActivity(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable activityId: Long,
        @Valid @RequestBody request: PersonalActivityUpdateRequest
    ): Result<ManagedActivityDetailView> {
        return Result.success(commandService.updatePersonal(principal, activityId, request))
    }

    @PutMapping("/api/v1/organizations/{organizationId}/activities/{activityId}")
    fun updateOrganizationActivity(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable organizationId: Long,
        @PathVariable activityId: Long,
        @Valid @RequestBody request: OrganizationActivityUpdateRequest
    ): Result<ManagedActivityDetailView> {
        return Result.success(
            commandService.updateOrganization(principal, organizationId, activityId, request)
        )
    }

    @PutMapping("/api/v1/activities/{activityId}/publication")
    fun publishActivity(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable activityId: Long
    ): Result<ManagedActivityDetailView> {
        return Result.success(commandService.publish(principal, activityId))
    }

    @PutMapping("/api/v1/activities/{activityId}/cancellation")
    fun cancelActivity(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable activityId: Long
    ): Result<ManagedActivityDetailView> {
        return Result.success(commandService.cancel(principal, activityId))
    }

    @DeleteMapping("/api/v1/activities/{activityId}")
    fun deleteActivity(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable activityId: Long
    ): Result<Void> {
        commandService.deleteDraft(principal, activityId)
        return Result.success()
    }
}
