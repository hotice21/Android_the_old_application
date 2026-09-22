package com.eligo.server.security

fun interface SessionAccessReader {
    fun requireActive(sessionKey: String, userId: Long): SessionAccessState
}
