package com.eligo.server.organization.vo

data class PublicOrganizationDetailView(
    val organizationId: String,
    val name: String,
    val avatar: AvatarView?,
    val summary: String?,
    val region: RegionView,
    val addressDetail: String?,
    val followerCount: Long
) {
    data class AvatarView(val fileId: String, val url: String)

    data class RegionView(
        val provinceCode: String?,
        val provinceName: String?,
        val cityCode: String?,
        val cityName: String?,
        val districtCode: String?,
        val districtName: String?
    )
}
