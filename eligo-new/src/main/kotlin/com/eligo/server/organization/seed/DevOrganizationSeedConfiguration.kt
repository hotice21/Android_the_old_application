package com.eligo.server.organization.seed

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration(proxyBeanMethods = false)
@Profile("local", "docker", "it")
@EnableConfigurationProperties(DevOrganizationSeedProperties::class)
class DevOrganizationSeedConfiguration
