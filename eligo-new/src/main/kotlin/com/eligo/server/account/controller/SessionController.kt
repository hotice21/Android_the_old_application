package com.eligo.server.account.controller

import com.eligo.server.account.service.SessionService
import com.eligo.server.account.vo.SessionView
import com.eligo.server.common.api.Result
import com.eligo.server.security.UserPrincipal
import jakarta.validation.constraints.Positive
import org.springframework.beans.factory.ObjectProvider
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping("/api/v1/account/sessions")
class SessionController(private val sessionService: ObjectProvider<SessionService>) {

    @GetMapping
    fun list(@AuthenticationPrincipal principal: UserPrincipal): Result<Map<String, List<SessionView>>?> =
        Result.success(mapOf("items" to sessionService.getObject().listActive(principal)))

    @DeleteMapping("/{sessionId}")
    fun revoke(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable @Positive sessionId: Long
    ): Result<Void> {
        sessionService.getObject().revokeOther(principal, sessionId)
        return Result.success()
    }
}
