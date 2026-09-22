package com.eligo.server.organization.vo

data class MyOrganizationSummaryView(
    val organizationId: String,
    val name: String,
    val avatar: AvatarView?,
    val role: String
) {
    data class AvatarView(val fileId: String, val url: String)
}
