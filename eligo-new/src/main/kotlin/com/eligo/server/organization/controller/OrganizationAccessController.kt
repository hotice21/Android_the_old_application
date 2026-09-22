package com.eligo.server.organization.controller

import com.eligo.server.common.api.Result
import com.eligo.server.organization.service.OrganizationAccessService
import com.eligo.server.organization.vo.MyOrganizationItemsView
import com.eligo.server.organization.vo.PublicOrganizationDetailView
import com.eligo.server.organization.vo.UserCapabilitiesView
import com.eligo.server.security.UserPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class OrganizationAccessController(private val service: OrganizationAccessService) {

    @GetMapping("/api/v1/users/me/capabilities")
    fun capabilities(@AuthenticationPrincipal principal: UserPrincipal): Result<UserCapabilitiesView?> =
        Result.success(service.getMyCapabilities(principal))

    @GetMapping("/api/v1/users/me/organizations")
    fun organizations(@AuthenticationPrincipal principal: UserPrincipal): Result<MyOrganizationItemsView?> =
        Result.success(service.listMyOrganizations(principal))

    @GetMapping("/api/v1/organizations/{organizationId}")
    fun publicOrganization(@PathVariable organizationId: Long): Result<PublicOrganizationDetailView?> =
        Result.success(service.getPublicOrganization(organizationId))
}
