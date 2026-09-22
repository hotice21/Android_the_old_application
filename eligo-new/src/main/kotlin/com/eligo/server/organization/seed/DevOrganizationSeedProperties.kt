package com.eligo.server.organization.seed

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "eligo.organization.dev-seed")
data class DevOrganizationSeedProperties(
    var enabled: Boolean = false,
    var organizationId: Long = 1_900_000_000_000_000_401L,
    var organizationMemberId: Long = 1_900_000_000_000_000_402L,
    var ownerUserId: Long = 0L
)
