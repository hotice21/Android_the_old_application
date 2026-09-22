package com.eligo.server.security

data class SessionAccessState(val sessionId: Long, val deviceId: Long, val accountStatus: Int)
