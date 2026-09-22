package com.eligo.server.security

import jakarta.servlet.http.HttpServletRequest

interface AccountAccessPolicy {
    fun check(principal: UserPrincipal, request: HttpServletRequest)
}
