package com.eligo.server.organization.vo

data class UserCapabilitiesView(
    val profileCompleted: Boolean,
    val capabilities: List<String>
)
