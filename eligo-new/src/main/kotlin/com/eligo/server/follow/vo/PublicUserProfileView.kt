package com.eligo.server.follow.vo

import com.eligo.server.activity.vo.PublicImageView
import com.eligo.server.profile.vo.InterestTagView

data class PublicUserProfileView(
    val userId: String,
    val nickname: String,
    val avatar: PublicImageView?,
    val bio: String?,
    val interestTags: List<InterestTagView>,
    val followingCount: Long,
    val followerCount: Long
)
