package com.eligo.server.profile.vo

import java.time.Instant
import java.time.LocalDate

data class MyProfileView(
    val userId: String?,
    val nickname: String?,
    val avatar: AvatarView?,
    val birthDate: LocalDate?,
    val gender: String?,
    val region: RegionView?,
    val email: String?,
    val bio: String?,
    val interestTags: List<InterestTagView>?,
    val phoneBinding: PhoneBindingView?,
    val profileCompleted: Boolean,
    val completedAt: Instant?,
    val nextNicknameChangeAt: Instant?
) {
    data class AvatarView(val fileId: String?, val url: String?)

    data class RegionView(
        val provinceCode: String?,
        val provinceName: String?,
        val cityCode: String?,
        val cityName: String?,
        val districtCode: String?,
        val districtName: String?
    )

    data class PhoneBindingView(val bound: Boolean, val maskedPhone: String?)
}
