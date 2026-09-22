package com.eligo.server.organization.service

import com.eligo.server.organization.vo.MyOrganizationItemsView
import com.eligo.server.organization.vo.PublicOrganizationDetailView
import com.eligo.server.organization.vo.UserCapabilitiesView
import com.eligo.server.security.UserPrincipal

interface OrganizationAccessService {

    fun getMyCapabilities(principal: UserPrincipal): UserCapabilitiesView

    fun listMyOrganizations(principal: UserPrincipal): MyOrganizationItemsView

    fun getPublicOrganization(organizationId: Long): PublicOrganizationDetailView
}
