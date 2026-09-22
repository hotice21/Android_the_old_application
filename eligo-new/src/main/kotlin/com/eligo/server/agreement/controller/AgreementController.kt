package com.eligo.server.agreement.controller

import com.eligo.server.agreement.entity.AgreementType
import com.eligo.server.agreement.service.AgreementService
import com.eligo.server.agreement.vo.AgreementConsentView
import com.eligo.server.agreement.vo.AgreementDetail
import com.eligo.server.agreement.vo.AgreementSummary
import com.eligo.server.common.api.Result
import com.eligo.server.security.UserPrincipal
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.Optional
import java.util.OptionalLong

@RestController
@RequestMapping("/api/v1/agreements")
class AgreementController(private val agreementService: AgreementService) {

    @GetMapping("/current")
    fun current(
        @AuthenticationPrincipal principal: UserPrincipal?,
        @RequestParam(required = false) type: AgreementType?
    ): Result<AgreementItems> =
        Result.success(AgreementItems(agreementService.current(userId(principal), Optional.ofNullable(type))))

    @GetMapping("/{agreementId}")
    fun detail(
        @AuthenticationPrincipal principal: UserPrincipal?,
        @PathVariable agreementId: Long
    ): Result<AgreementDetail> =
        Result.success(agreementService.detail(userId(principal), agreementId))

    @PostMapping("/{agreementId}/consents")
    fun consent(
        @AuthenticationPrincipal principal: UserPrincipal?,
        @PathVariable agreementId: Long
    ): ResponseEntity<Result<AgreementConsentView>> {
        val view = agreementService.consent(principal!!, agreementId)
        val status = if (view.created) HttpStatus.CREATED else HttpStatus.OK
        return ResponseEntity.status(status).body(Result.success(view))
    }

    private fun userId(principal: UserPrincipal?): OptionalLong =
        if (principal == null) OptionalLong.empty() else OptionalLong.of(principal.userId)

    data class AgreementItems(val items: List<AgreementSummary>)
}
