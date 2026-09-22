package com.eligo.server.follow.mapper

data class PublicUserProfileRow(
    val userId: Long? = null,
    val nickname: String? = null,
    val avatarFileId: Long? = null,
    val bio: String? = null
)
