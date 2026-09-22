package com.eligo.server.security.attack

import com.eligo.server.common.api.Result
import com.eligo.server.security.UserPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 攻击测试专用的受保护端点：需要登录才能访问，并回显当前用户身份。
 */
@RestController
class AttackTestSupportController {

    @GetMapping("/test-support/attack/protected")
    fun protectedEndpoint(
        @AuthenticationPrincipal principal: UserPrincipal
    ): Result<String> = Result.success("${principal.userId}:${principal.sessionKey}")
}
