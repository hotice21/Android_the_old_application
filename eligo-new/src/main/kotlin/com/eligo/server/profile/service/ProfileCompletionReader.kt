package com.eligo.server.profile.service

interface ProfileCompletionReader {

    fun isCompleted(userId: Long): Boolean
}
