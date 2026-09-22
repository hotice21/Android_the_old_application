package com.eligo.server.account.controller

import com.eligo.server.account.dto.DeactivationRequest
import com.eligo.server.account.service.AccountDataService
import com.eligo.server.account.vo.DataExportView
import com.eligo.server.account.vo.DeactivationView
import com.eligo.server.account.vo.DownloadUrlView
import com.eligo.server.common.api.Result
import com.eligo.server.security.UserPrincipal
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping("/api/v1/account")
class AccountDataController(private val service: ObjectProvider<AccountDataService>) {

    @PostMapping("/deactivation")
    fun requestDeactivation(
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody request: DeactivationRequest
    ): ResponseEntity<Result<DeactivationView?>> {
        val outcome = service.getObject().requestDeactivationOutcome(principal, request)
        return ResponseEntity.status(if (outcome.created) HttpStatus.CREATED else HttpStatus.OK)
            .body(Result.success(outcome.view))
    }

    @GetMapping("/deactivation")
    fun currentDeactivation(@AuthenticationPrincipal principal: UserPrincipal): Result<DeactivationView?> =
        Result.success(service.getObject().currentDeactivation(principal).orElse(null))

    @DeleteMapping("/deactivation")
    fun cancelDeactivation(@AuthenticationPrincipal principal: UserPrincipal): Result<Void> {
        service.getObject().cancelDeactivation(principal)
        return Result.success()
    }

    @PostMapping("/data-exports")
    fun requestExport(@AuthenticationPrincipal principal: UserPrincipal): ResponseEntity<Result<DataExportView?>> =
        ResponseEntity.status(HttpStatus.CREATED).body(Result.success(service.getObject().requestExport(principal)))

    @GetMapping("/data-exports/{requestId}")
    fun exportStatus(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable @Positive requestId: Long
    ): Result<DataExportView?> =
        Result.success(service.getObject().exportStatus(principal, requestId))

    @GetMapping("/data-exports/{requestId}/download-url")
    fun exportDownloadUrl(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable @Positive requestId: Long
    ): Result<DownloadUrlView?> =
        Result.success(service.getObject().exportDownloadUrl(principal, requestId))
}
