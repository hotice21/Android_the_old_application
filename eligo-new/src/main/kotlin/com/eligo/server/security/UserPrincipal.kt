package com.eligo.server.security

data class UserPrincipal(val userId: Long, val sessionKey: String)
