package com.eligo.server.account.controller

import com.eligo.server.account.service.AccountDataService
import com.eligo.server.account.vo.SecurityEventView
import com.eligo.server.common.api.CursorPage
import com.eligo.server.common.api.Result
import com.eligo.server.security.UserPrincipal
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.beans.factory.ObjectProvider
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping("/api/v1/account/security-events")
class SecurityEventController(private val service: ObjectProvider<AccountDataService>) {

    @GetMapping
    fun list(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) limit: Int
    ): Result<CursorPage<SecurityEventView>?> =
        Result.success(service.getObject().securityEvents(principal, cursor, limit))
}
