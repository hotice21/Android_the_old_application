package com.eligo.server.profile.service

interface ProfileCompletionUpdater {

    fun recalculate(userId: Long): Boolean
}
