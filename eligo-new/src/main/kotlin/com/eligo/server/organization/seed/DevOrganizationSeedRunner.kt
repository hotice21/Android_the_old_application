package com.eligo.server.organization.seed

import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("local")
@ConditionalOnProperty(
    prefix = "eligo.organization.dev-seed",
    name = ["enabled"],
    havingValue = "true"
)
class DevOrganizationSeedRunner(private val seedService: DevOrganizationSeedService) : CommandLineRunner {

    override fun run(vararg args: String) {
        seedService.seedConfigured()
    }
}
