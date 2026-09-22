package com.eligo.server.account.service

import com.eligo.server.account.vo.SessionView
import com.eligo.server.security.UserPrincipal

interface SessionService {
    fun listActive(principal: UserPrincipal): List<SessionView>
    fun revokeOther(principal: UserPrincipal, sessionId: Long)
}
