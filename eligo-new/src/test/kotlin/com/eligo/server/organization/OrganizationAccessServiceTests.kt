package com.eligo.server.organization

import java.util.function.Function

import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.follow.mapper.OrganizationFollowMapper
import com.eligo.server.organization.entity.OrganizationEntity
import com.eligo.server.organization.mapper.OrganizationMapper
import com.eligo.server.organization.service.DefaultOrganizationAccessService
import com.eligo.server.organization.vo.MyOrganizationItemsView
import com.eligo.server.organization.vo.PublicOrganizationDetailView
import com.eligo.server.profile.service.ProfileCompletionReader
import com.eligo.server.security.UserPrincipal
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class OrganizationAccessServiceTests {

    private val principal = UserPrincipal(202L, "session-a")

    private val completion = mock<ProfileCompletionReader>()
    private val organizations = mock<OrganizationMapper>()
    private val organizationFollows = mock<OrganizationFollowMapper>()
    private lateinit var service: DefaultOrganizationAccessService

    @BeforeEach
    fun setUp() {
        service = DefaultOrganizationAccessService(
            completion, organizations, organizationFollows)
    }

    @Test
    fun incompleteProfileHasPublicReadOnlyAndNoOperableOrganization() {
        whenever(completion.isCompleted(202L)).thenReturn(false)

        assertThat(service.getMyCapabilities(principal).capabilities)
            .containsExactly("PUBLIC_READ")
        assertThat(service.listMyOrganizations(principal).items).isEmpty()
        verifyNoInteractions(organizations)
    }

    @Test
    fun completedProfileWithoutOrganizationHasPersonalCapabilities() {
        whenever(completion.isCompleted(202L)).thenReturn(true)
        whenever(organizations.findActiveOwnedByUserId(202L)).thenReturn(emptyList())

        assertThat(service.getMyCapabilities(principal).capabilities)
            .containsExactly(
                "PUBLIC_READ",
                "PUBLISH_PERSONAL_ACTIVITY",
                "PUBLISH_PERSONAL_POST",
                "PARTICIPATE",
                "FOLLOW")
        assertThat(service.listMyOrganizations(principal).items).isEmpty()
    }

    @Test
    fun activeOwnerGetsOrderedEnterpriseCapabilitiesAndSafeSummary() {
        val organization = organization(401L, 901L)
        whenever(completion.isCompleted(202L)).thenReturn(true)
        whenever(organizations.findActiveOwnedByUserId(202L))
            .thenReturn(listOf(organization))

        assertThat(service.getMyCapabilities(principal).capabilities)
            .containsExactly(
                "PUBLIC_READ",
                "PUBLISH_PERSONAL_ACTIVITY",
                "PUBLISH_PERSONAL_POST",
                "PARTICIPATE",
                "FOLLOW",
                "PUBLISH_ORGANIZATION_ACTIVITY",
                "PUBLISH_ORGANIZATION_POST",
                "MANAGE_ORGANIZATION")

        val result = service.listMyOrganizations(principal)
        assertThat(result.items).hasSize(1)
        assertThat(result.items[0].organizationId).isEqualTo("401")
        assertThat(result.items[0].name).isEqualTo("山海户外")
        assertThat(result.items[0].role).isEqualTo("OWNER")
        assertThat(result.items[0].avatar!!.fileId).isEqualTo("901")
        assertThat(result.items[0].avatar!!.url)
            .isEqualTo("/api/v1/files/901/content")
    }

    @Test
    fun returnsOnlySafeFieldsForPublicOrganizationDetail() {
        val organization = organization(401L, 901L)
        organization.summary = "公开企业简介"
        organization.provinceCode = "44"
        organization.provinceName = "广东省"
        organization.cityCode = "4403"
        organization.cityName = "深圳市"
        organization.districtCode = "440305"
        organization.districtName = "南山区"
        organization.addressDetail = "公开地址"
        whenever(organizations.findActivePublicById(401L))
            .thenReturn(Optional.of(organization))
        whenever(organizationFollows.countPublicFollowers(401L)).thenReturn(4L)

        val result = service.getPublicOrganization(401L)

        assertThat(result.organizationId).isEqualTo("401")
        assertThat(result.name).isEqualTo("山海户外")
        assertThat(result.avatar!!.fileId).isEqualTo("901")
        assertThat(result.avatar!!.url).isEqualTo("/api/v1/files/901/content")
        assertThat(result.summary).isEqualTo("公开企业简介")
        assertThat(result.region.districtCode).isEqualTo("440305")
        assertThat(result.addressDetail).isEqualTo("公开地址")
        assertThat(result.followerCount).isEqualTo(4L)
    }

    @Test
    fun inactiveOrMissingPublicOrganizationIsNotFound() {
        whenever(organizations.findActivePublicById(401L))
            .thenReturn(Optional.empty())

        assertThatThrownBy { service.getPublicOrganization(401L) }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  exception -> (exception as BusinessException).errorCode  })
            .isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND)

        assertThatThrownBy { service.getPublicOrganization(0L) }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  exception -> (exception as BusinessException).errorCode  })
            .isEqualTo(CommonErrorCode.VALIDATION_FAILED)
    }

    private fun organization(id: Long, avatarFileId: Long?): OrganizationEntity {
        val organization = OrganizationEntity()
        organization.id = id
        organization.name = "山海户外"
        organization.avatarFileId = avatarFileId
        organization.status = 1
        return organization
    }
}
