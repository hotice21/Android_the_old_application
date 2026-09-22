package com.eligo.server.security

import com.eligo.server.account.service.PhoneBindingService
import com.eligo.server.account.vo.PhoneBindingView
import com.eligo.server.activity.dto.OrganizationActivityCreateRequest
import com.eligo.server.activity.dto.OrganizationActivityUpdateRequest
import com.eligo.server.activity.dto.PersonalActivityCreateRequest
import com.eligo.server.activity.dto.PersonalActivityUpdateRequest
import com.eligo.server.activity.mapper.ActivityMapper
import com.eligo.server.activity.service.ActivityCommandService
import com.eligo.server.activity.service.ActivityContactAccessService
import com.eligo.server.activity.service.ActivityCreateOutcome
import com.eligo.server.activity.service.ActivityReadService
import com.eligo.server.activity.vo.ActivityMapView
import com.eligo.server.activity.vo.ManagedActivityDetailView
import com.eligo.server.activity.vo.ManagedActivitySummaryView
import com.eligo.server.activity.vo.PublicActivityDetailView
import com.eligo.server.activity.vo.PublicActivitySummaryView
import com.eligo.server.agreement.entity.AgreementType
import com.eligo.server.agreement.service.AgreementService
import com.eligo.server.agreement.vo.AgreementConsentView
import com.eligo.server.agreement.vo.AgreementDetail
import com.eligo.server.agreement.vo.AgreementSummary
import com.eligo.server.comment.service.ActivityCommentService
import com.eligo.server.common.api.CursorPage
import com.eligo.server.favorite.service.ActivityFavoriteService
import com.eligo.server.file.entity.FileObjectEntity
import com.eligo.server.file.service.FileService
import com.eligo.server.file.vo.FileView
import com.eligo.server.follow.service.FollowCommandService
import com.eligo.server.follow.service.FollowReadService
import com.eligo.server.follow.vo.FollowerSummaryView
import com.eligo.server.follow.vo.FollowStateView
import com.eligo.server.follow.vo.FollowTargetSummaryView
import com.eligo.server.follow.vo.PublicUserProfileView
import com.eligo.server.organization.service.OrganizationAccessService
import com.eligo.server.organization.vo.MyOrganizationItemsView
import com.eligo.server.organization.vo.UserCapabilitiesView
import com.eligo.server.participation.service.ParticipationCommandService
import com.eligo.server.participation.service.ParticipationReadService
import com.eligo.server.participation.vo.ActivityParticipantSummaryView
import com.eligo.server.participation.vo.ActivityParticipationView
import com.eligo.server.participation.vo.MyParticipationView
import com.eligo.server.post.dto.PostDraftCreateRequest
import com.eligo.server.post.dto.PostDraftReplaceRequest
import com.eligo.server.post.service.PostCommandService
import com.eligo.server.post.service.PostCreateOutcome
import com.eligo.server.post.service.PostReadService
import com.eligo.server.post.vo.ManagedPostDetailView
import com.eligo.server.post.vo.ManagedPostSummaryView
import com.eligo.server.post.vo.PublicPostView
import com.eligo.server.profile.dto.UpdateProfileRequest
import com.eligo.server.profile.service.ProfileService
import com.eligo.server.profile.vo.InterestTagView
import com.eligo.server.profile.vo.MyProfileView
import com.eligo.server.profile.vo.NicknameChangeView
import com.eligo.server.recommendation.service.RecommendedFeedService
import org.mockito.Mockito
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.web.multipart.MultipartFile
import java.io.InputStream
import java.math.BigDecimal
import java.util.Optional
import java.util.OptionalLong
import javax.sql.DataSource

@Configuration(proxyBeanMethods = false)
@Profile("test")
open class TestSessionAccessConfiguration {
    @Bean
    @ConditionalOnMissingBean(DataSource::class)
    open fun testSessionAccessReader(): SessionAccessReader {
        return SessionAccessReader { _, _ -> SessionAccessState(1L, 1L, 1) }
    }

    @Bean
    @ConditionalOnMissingBean(DataSource::class)
    open fun testAccountRestrictionReader(): AccountRestrictionReader {
        return AccountRestrictionReader { AccountRestrictionReader.State(1, true, listOf()) }
    }

    @Bean
    @ConditionalOnMissingBean(AgreementService::class)
    open fun testAgreementService(): AgreementService {
        return object : AgreementService {
            override fun current(userId: OptionalLong, type: Optional<AgreementType>): List<AgreementSummary> {
                return listOf()
            }

            override fun detail(userId: OptionalLong, agreementId: Long): AgreementDetail {
                throw UnsupportedOperationException("测试上下文未配置协议详情")
            }

            override fun consent(principal: UserPrincipal, agreementId: Long): AgreementConsentView {
                throw UnsupportedOperationException("测试上下文未配置协议同意")
            }

            override fun pendingRequiredAgreementIds(userId: Long): List<Long> {
                return listOf()
            }
        }
    }

    @Bean
    @ConditionalOnMissingBean(ProfileService::class)
    open fun testProfileService(): ProfileService {
        return object : ProfileService {
            override fun getMyProfile(principal: UserPrincipal): MyProfileView = throw unsupported()
            override fun updateProfile(principal: UserPrincipal, request: UpdateProfileRequest): MyProfileView = throw unsupported()
            override fun updateNickname(principal: UserPrincipal, nickname: String?): NicknameChangeView = throw unsupported()
            override fun listEnabledInterests(): List<InterestTagView> = listOf()
            override fun replaceInterests(principal: UserPrincipal, interestTagIds: List<Long>?): MyProfileView = throw unsupported()
            override fun recalculateCompletion(userId: Long): Boolean = false
            override fun isCompleted(userId: Long): Boolean = false
            override fun genderCode(userId: Long): Int? = null
            private fun unsupported(): UnsupportedOperationException {
                return UnsupportedOperationException("测试上下文未配置资料操作")
            }
        }
    }

    @Bean
    @ConditionalOnMissingBean(PhoneBindingService::class)
    open fun testPhoneBindingService(): PhoneBindingService {
        return object : PhoneBindingService {
            override fun current(principal: UserPrincipal): PhoneBindingView {
                return PhoneBindingView.unbound()
            }

            override fun current(userId: Long): PhoneBindingView {
                return PhoneBindingView.unbound()
            }

            override fun bindOrReplace(principal: UserPrincipal, phoneCode: String): PhoneBindingView {
                throw UnsupportedOperationException("测试上下文未配置手机号绑定操作")
            }

            override fun unbind(principal: UserPrincipal) {
                throw UnsupportedOperationException("测试上下文未配置手机号解绑操作")
            }
        }
    }

    @Bean
    @ConditionalOnMissingBean(FileService::class)
    open fun testFileService(): FileService {
        return object : FileService {
            override fun uploadImage(principal: UserPrincipal, file: MultipartFile?, purpose: String): FileView = throw unsupported()
            override fun uploadAvatarImage(principal: UserPrincipal, file: MultipartFile): FileView = throw unsupported()
            override fun getOwned(principal: UserPrincipal, fileId: Long): FileView = throw unsupported()
            override fun deleteTemporary(principal: UserPrincipal, fileId: Long) = throw unsupported()
            override fun requireUsableAvatar(userId: Long, fileId: Long): FileObjectEntity = throw unsupported()
            override fun activateAvatar(file: FileObjectEntity) = throw unsupported()
            override fun openOwnedContent(principal: UserPrincipal, fileId: Long): InputStream = throw unsupported()
            override fun openContent(principal: UserPrincipal?, fileId: Long): FileService.FileContent = throw unsupported()
            private fun unsupported(): UnsupportedOperationException {
                return UnsupportedOperationException("test context has no file service")
            }
        }
    }

    @Bean
    @ConditionalOnMissingBean(ActivityContactAccessService::class)
    open fun testActivityContactAccessService(): ActivityContactAccessService {
        return ActivityContactAccessService(
            Mockito.mock(ActivityMapper::class.java),
            Mockito.mock(com.eligo.server.organization.mapper.OrganizationMapper::class.java),
            Mockito.mock(ParticipationReadService::class.java),
            Mockito.mock(FileService::class.java)
        )
    }

    @Bean
    @ConditionalOnMissingBean(OrganizationAccessService::class)
    open fun testOrganizationAccessService(): OrganizationAccessService {
        return object : OrganizationAccessService {
            override fun getMyCapabilities(principal: UserPrincipal): UserCapabilitiesView {
                return UserCapabilitiesView(false, listOf("PUBLIC_READ"))
            }

            override fun listMyOrganizations(principal: UserPrincipal): MyOrganizationItemsView {
                return MyOrganizationItemsView(listOf())
            }

            override fun getPublicOrganization(organizationId: Long): com.eligo.server.organization.vo.PublicOrganizationDetailView {
                throw UnsupportedOperationException("测试上下文未配置公开企业详情")
            }
        }
    }

    @Bean
    @ConditionalOnMissingBean(ActivityReadService::class)
    open fun testActivityReadService(): ActivityReadService {
        return object : ActivityReadService {
            override fun listPublicActivities(
                cursor: String?,
                limit: Int,
                status: String?,
                categoryCode: String?,
                regionCode: String?
            ): CursorPage<PublicActivitySummaryView> {
                throw UnsupportedOperationException("测试上下文未配置活动读取")
            }

            override fun listActivitiesOnMap(
                minLatitude: BigDecimal?,
                maxLatitude: BigDecimal?,
                minLongitude: BigDecimal?,
                maxLongitude: BigDecimal?,
                categoryCode: String?,
                limit: Int
            ): ActivityMapView {
                throw UnsupportedOperationException("测试上下文未配置活动地图读取")
            }

            override fun getPublicActivity(activityId: Long): PublicActivityDetailView {
                throw UnsupportedOperationException("测试上下文未配置活动读取")
            }

            override fun findPublicSummaries(
                activityIds: List<Long>?
            ): Map<Long, PublicActivitySummaryView> {
                throw UnsupportedOperationException("测试上下文未配置活动批量读取")
            }

            override fun getManagedActivity(
                principal: UserPrincipal?,
                activityId: Long
            ): ManagedActivityDetailView {
                throw UnsupportedOperationException("测试上下文未配置活动读取")
            }

            override fun listManagedActivities(
                principal: UserPrincipal?,
                cursor: String?,
                limit: Int,
                status: String?,
                ownerType: String?
            ): CursorPage<ManagedActivitySummaryView> {
                throw UnsupportedOperationException("测试上下文未配置活动读取")
            }
        }
    }

    @Bean
    @ConditionalOnMissingBean(ActivityCommandService::class)
    open fun testActivityCommandService(): ActivityCommandService {
        return object : ActivityCommandService {
            override fun createPersonal(
                principal: UserPrincipal?,
                request: PersonalActivityCreateRequest?,
                idempotencyKey: String
            ): ActivityCreateOutcome = throw unsupported()

            override fun createOrganization(
                principal: UserPrincipal?,
                organizationId: Long,
                request: OrganizationActivityCreateRequest?,
                idempotencyKey: String
            ): ActivityCreateOutcome = throw unsupported()

            override fun updatePersonal(
                principal: UserPrincipal?,
                activityId: Long,
                request: PersonalActivityUpdateRequest?
            ): ManagedActivityDetailView = throw unsupported()

            override fun updateOrganization(
                principal: UserPrincipal?,
                organizationId: Long,
                activityId: Long,
                request: OrganizationActivityUpdateRequest?
            ): ManagedActivityDetailView = throw unsupported()

            override fun publish(
                principal: UserPrincipal?,
                activityId: Long
            ): ManagedActivityDetailView = throw unsupported()

            override fun cancel(
                principal: UserPrincipal?,
                activityId: Long
            ): ManagedActivityDetailView = throw unsupported()

            override fun deleteDraft(principal: UserPrincipal?, activityId: Long) = throw unsupported()

            private fun unsupported(): UnsupportedOperationException {
                return UnsupportedOperationException("测试上下文未配置活动发布")
            }
        }
    }

    @Bean
    @ConditionalOnMissingBean(ParticipationCommandService::class)
    open fun testParticipationCommandService(): ParticipationCommandService {
        return object : ParticipationCommandService {
            override fun join(
                principal: UserPrincipal?,
                activityId: Long
            ): ActivityParticipationView = throw unsupported()

            override fun cancel(
                principal: UserPrincipal?,
                activityId: Long
            ): ActivityParticipationView = throw unsupported()

            override fun remove(
                principal: UserPrincipal?,
                activityId: Long,
                userId: Long
            ): ActivityParticipationView = throw unsupported()

            private fun unsupported(): UnsupportedOperationException {
                return UnsupportedOperationException("测试上下文未配置活动参与命令")
            }
        }
    }

    @Bean
    @ConditionalOnMissingBean(ParticipationReadService::class)
    open fun testParticipationReadService(): ParticipationReadService {
        return object : ParticipationReadService {
            override fun listParticipants(
                principal: UserPrincipal?,
                activityId: Long,
                cursor: String?,
                limit: Int
            ): CursorPage<ActivityParticipantSummaryView> = throw unsupported()

            override fun listMyParticipations(
                principal: UserPrincipal?,
                cursor: String?,
                limit: Int,
                status: String?,
                keyword: String?
            ): CursorPage<MyParticipationView> = throw unsupported()

            override fun findStatus(activityId: Long, userId: Long): Optional<String> {
                return Optional.empty()
            }

            private fun unsupported(): UnsupportedOperationException {
                return UnsupportedOperationException("测试上下文未配置活动参与读取")
            }
        }
    }

    @Bean
    @ConditionalOnMissingBean(FollowCommandService::class)
    open fun testFollowCommandService(): FollowCommandService {
        return object : FollowCommandService {
            override fun followUser(principal: UserPrincipal, userId: Long): FollowStateView = throw unsupported()
            override fun unfollowUser(principal: UserPrincipal, userId: Long): FollowStateView = throw unsupported()
            override fun followOrganization(principal: UserPrincipal, organizationId: Long): FollowStateView = throw unsupported()
            override fun unfollowOrganization(principal: UserPrincipal, organizationId: Long): FollowStateView = throw unsupported()
            override fun getUserState(principal: UserPrincipal, userId: Long): FollowStateView = throw unsupported()
            override fun getOrganizationState(principal: UserPrincipal, organizationId: Long): FollowStateView = throw unsupported()

            private fun unsupported(): UnsupportedOperationException {
                return UnsupportedOperationException("测试上下文未配置关注命令")
            }
        }
    }

    @Bean
    @ConditionalOnMissingBean(FollowReadService::class)
    open fun testFollowReadService(): FollowReadService {
        return object : FollowReadService {
            override fun listFollowing(
                principal: UserPrincipal?,
                cursor: String?,
                limit: Int,
                type: String?,
                keyword: String?,
                sort: String?
            ): CursorPage<FollowTargetSummaryView> = throw unsupported()

            override fun listFollowers(
                principal: UserPrincipal?,
                cursor: String?,
                limit: Int,
                keyword: String?,
                sort: String?
            ): CursorPage<FollowerSummaryView> = throw unsupported()

            override fun getPublicUserProfile(userId: Long): PublicUserProfileView = throw unsupported()

            private fun unsupported(): UnsupportedOperationException {
                return UnsupportedOperationException("测试上下文未配置关注读取")
            }
        }
    }

    @Bean
    @ConditionalOnMissingBean(PostCommandService::class)
    open fun testPostCommandService(): PostCommandService {
        return object : PostCommandService {
            override fun createPersonal(
                principal: UserPrincipal?, request: PostDraftCreateRequest?, idempotencyKey: String
            ): PostCreateOutcome = throw unsupported()

            override fun createOrganization(
                principal: UserPrincipal?, organizationId: Long,
                request: PostDraftCreateRequest?, idempotencyKey: String
            ): PostCreateOutcome = throw unsupported()

            override fun replacePersonal(
                principal: UserPrincipal?, postId: Long, request: PostDraftReplaceRequest?
            ): ManagedPostDetailView = throw unsupported()

            override fun replaceOrganization(
                principal: UserPrincipal?, organizationId: Long, postId: Long,
                request: PostDraftReplaceRequest?
            ): ManagedPostDetailView = throw unsupported()

            override fun publish(principal: UserPrincipal?, postId: Long): ManagedPostDetailView = throw unsupported()

            override fun delete(principal: UserPrincipal?, postId: Long) = throw unsupported()

            private fun unsupported(): UnsupportedOperationException {
                return UnsupportedOperationException("测试上下文未配置动态命令")
            }
        }
    }

    @Bean
    @ConditionalOnMissingBean(PostReadService::class)
    open fun testPostReadService(): PostReadService {
        return object : PostReadService {
            override fun listManagedPosts(
                principal: UserPrincipal?, cursor: String?, limit: Int,
                status: String?, authorType: String?
            ): CursorPage<ManagedPostSummaryView> = throw unsupported()

            override fun getManagedPost(principal: UserPrincipal?, postId: Long): ManagedPostDetailView = throw unsupported()

            override fun getManagedPostForReplay(principal: UserPrincipal?, postId: Long): ManagedPostDetailView = throw unsupported()

            override fun getPost(principal: UserPrincipal?, postId: Long): PublicPostView = throw unsupported()

            override fun listPublicPosts(cursor: String?, limit: Int): CursorPage<PublicPostView> = throw unsupported()

            override fun listUserPosts(
                principal: UserPrincipal?, userId: Long, cursor: String?, limit: Int
            ): CursorPage<PublicPostView> = throw unsupported()

            override fun listOrganizationPosts(
                principal: UserPrincipal?, organizationId: Long, cursor: String?, limit: Int
            ): CursorPage<PublicPostView> = throw unsupported()

            override fun listFollowingFeed(
                principal: UserPrincipal?, cursor: String?, limit: Int
            ): CursorPage<PublicPostView> = throw unsupported()

            override fun canReadMedia(principal: UserPrincipal?, fileId: Long): Boolean = false

            private fun unsupported(): UnsupportedOperationException {
                return UnsupportedOperationException("测试上下文未配置动态读取")
            }
        }
    }

    @Bean
    @ConditionalOnMissingBean(RecommendedFeedService::class)
    open fun testRecommendedFeedService(): RecommendedFeedService {
        return object : RecommendedFeedService {
            override fun list(
                principal: UserPrincipal?,
                cursor: String?,
                limit: Int
            ): CursorPage<PublicPostView> {
                throw UnsupportedOperationException("测试上下文未配置推荐读取")
            }
        }
    }

    @Bean
    @ConditionalOnMissingBean(ActivityFavoriteService::class)
    open fun testActivityFavoriteService(): ActivityFavoriteService {
        return Mockito.mock(ActivityFavoriteService::class.java)
    }

    @Bean
    @ConditionalOnMissingBean(ActivityCommentService::class)
    open fun testActivityCommentService(): ActivityCommentService {
        return Mockito.mock(ActivityCommentService::class.java)
    }
}
