package com.eligo.server.organization.service

import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.follow.mapper.OrganizationFollowMapper
import com.eligo.server.organization.entity.OrganizationEntity
import com.eligo.server.organization.mapper.OrganizationMapper
import com.eligo.server.organization.vo.MyOrganizationItemsView
import com.eligo.server.organization.vo.MyOrganizationSummaryView
import com.eligo.server.organization.vo.PublicOrganizationDetailView
import com.eligo.server.organization.vo.UserCapabilitiesView
import com.eligo.server.profile.service.ProfileCompletionReader
import com.eligo.server.security.UserPrincipal
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test")
class DefaultOrganizationAccessService(
    private val completion: ProfileCompletionReader,
    private val organizations: OrganizationMapper,
    private val organizationFollows: OrganizationFollowMapper
) : OrganizationAccessService {

    @Transactional(readOnly = true)
    override fun getMyCapabilities(principal: UserPrincipal): UserCapabilitiesView {
        val profileCompleted = completion.isCompleted(principal.userId)
        val capabilities = mutableListOf<String>()
        capabilities.add(PUBLIC_READ)
        if (profileCompleted) {
            capabilities.addAll(PERSONAL_CAPABILITIES)
            if (organizations.findActiveOwnedByUserId(principal.userId).isNotEmpty()) {
                capabilities.addAll(ORGANIZATION_CAPABILITIES)
            }
        }
        return UserCapabilitiesView(profileCompleted, capabilities.toList())
    }

    @Transactional(readOnly = true)
    override fun listMyOrganizations(principal: UserPrincipal): MyOrganizationItemsView {
        if (!completion.isCompleted(principal.userId)) {
            return MyOrganizationItemsView(emptyList())
        }
        val items = organizations.findActiveOwnedByUserId(principal.userId).map { summary(it) }
        return MyOrganizationItemsView(items)
    }

    @Transactional(readOnly = true)
    override fun getPublicOrganization(organizationId: Long): PublicOrganizationDetailView {
        if (organizationId <= 0) {
            throw BusinessException(CommonErrorCode.VALIDATION_FAILED)
        }
        val organization = organizations.findActivePublicById(organizationId)
            .orElseThrow { BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND) }
        val avatar = if (organization.avatarFileId == null) null
        else PublicOrganizationDetailView.AvatarView(
            organization.avatarFileId.toString(),
            "/api/v1/files/" + organization.avatarFileId + "/content"
        )
        val region = PublicOrganizationDetailView.RegionView(
            organization.provinceCode,
            organization.provinceName,
            organization.cityCode,
            organization.cityName,
            organization.districtCode,
            organization.districtName
        )
        return PublicOrganizationDetailView(
            organization.id.toString(),
            organization.name!!,
            avatar,
            organization.summary,
            region,
            organization.addressDetail,
            organizationFollows.countPublicFollowers(organizationId)
        )
    }

    private fun summary(organization: OrganizationEntity): MyOrganizationSummaryView {
        val avatar = if (organization.avatarFileId == null) null
        else MyOrganizationSummaryView.AvatarView(
            organization.avatarFileId.toString(),
            "/api/v1/files/" + organization.avatarFileId + "/content"
        )
        return MyOrganizationSummaryView(
            organization.id.toString(),
            organization.name!!,
            avatar,
            "OWNER"
        )
    }

    companion object {
        private const val PUBLIC_READ = "PUBLIC_READ"
        private val PERSONAL_CAPABILITIES = listOf(
            "PUBLISH_PERSONAL_ACTIVITY",
            "PUBLISH_PERSONAL_POST",
            "PARTICIPATE",
            "FOLLOW"
        )
        private val ORGANIZATION_CAPABILITIES = listOf(
            "PUBLISH_ORGANIZATION_ACTIVITY",
            "PUBLISH_ORGANIZATION_POST",
            "MANAGE_ORGANIZATION"
        )
    }
}
