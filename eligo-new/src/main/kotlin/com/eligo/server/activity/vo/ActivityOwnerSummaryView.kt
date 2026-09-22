package com.eligo.server.activity.vo

data class ActivityOwnerSummaryView(
    val ownerType: String,
    val ownerId: String,
    val displayName: String,
    val avatar: PublicImageView?
)
