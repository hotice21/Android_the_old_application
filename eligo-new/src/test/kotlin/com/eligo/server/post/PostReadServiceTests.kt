package com.eligo.server.post

import java.util.function.Function

import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.follow.entity.UserFollowEntity
import com.eligo.server.follow.mapper.OrganizationFollowMapper
import com.eligo.server.follow.mapper.UserFollowMapper
import com.eligo.server.organization.mapper.OrganizationMapper
import com.eligo.server.post.mapper.PostDetailRow
import com.eligo.server.post.mapper.PostQueryMapper
import com.eligo.server.post.service.DefaultPostReadService
import com.eligo.server.profile.mapper.UserProfileMapper
import com.eligo.server.security.UserPrincipal
import java.time.LocalDateTime
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PostReadServiceTests {

    private val queries = mock<PostQueryMapper>()
    private val organizations = mock<OrganizationMapper>()
    private val profiles = mock<UserProfileMapper>()
    private val userFollows = mock<UserFollowMapper>()
    private val organizationFollows = mock<OrganizationFollowMapper>()
    private lateinit var service: DefaultPostReadService

    @BeforeEach
    fun setUp() {
        service = DefaultPostReadService(
            queries,
            organizations,
            profiles,
            userFollows,
            organizationFollows)
        whenever(queries.findMediaFileIds(POST_ID)).thenReturn(emptyList())
    }

    @Test
    fun managedDetailRequiresPersonalAuthorOrCurrentSoleOwner() {
        whenever(queries.findById(POST_ID)).thenReturn(Optional.of(personal(1, 1)))

        assertError(
            { service.getManagedPost(PRINCIPAL, POST_ID) },
            CommonErrorCode.RESOURCE_NOT_FOUND)

        var own = personal(1, 1)
        own = PostDetailRow(
            own.postId, USER_ID, null, "本人", null, own.status,
            own.visibility, own.title, own.content, own.activityId,
            own.version, own.publishedAt, own.hiddenAt, own.createdAt,
            own.updatedAt)
        whenever(queries.findById(POST_ID)).thenReturn(Optional.of(own))
        assertThat(service.getManagedPost(PRINCIPAL, POST_ID).status)
            .isEqualTo("DRAFT")

        whenever(queries.findById(POST_ID)).thenReturn(Optional.of(organization(4, 3)))
        whenever(organizations.isSoleOwner(USER_ID, ORGANIZATION_ID)).thenReturn(true)
        assertThat(service.getManagedPost(PRINCIPAL, POST_ID).status)
            .isEqualTo("HIDDEN")
    }

    @Test
    fun replayManagedDetailUsesCurrentReadsForPostAndMedia() {
        var own = personal(1, 1)
        own = PostDetailRow(
            own.postId, USER_ID, null, "本人", null, own.status,
            own.visibility, own.title, own.content, own.activityId,
            own.version, own.publishedAt, own.hiddenAt, own.createdAt,
            own.updatedAt)
        whenever(queries.findByIdForUpdate(POST_ID)).thenReturn(Optional.of(own))
        whenever(queries.findMediaFileIdsForUpdate(POST_ID)).thenReturn(listOf(9001L))

        val detail = service.getManagedPostForReplay(PRINCIPAL, POST_ID)

        assertThat(detail.media).extracting(Function {  it.fileId  })
            .containsExactly("9001")
        verify(queries).findByIdForUpdate(POST_ID)
        verify(queries).findMediaFileIdsForUpdate(POST_ID)
    }

    @Test
    fun ordinaryDetailEnforcesPublishedStatusAndLiveVisibility() {
        whenever(queries.findPublicCandidateById(POST_ID))
            .thenReturn(Optional.of(personal(2, 1)))
        assertThat(service.getPost(null, POST_ID).visibility).isEqualTo("PUBLIC")

        whenever(queries.findPublicCandidateById(POST_ID))
            .thenReturn(Optional.of(personal(2, 2)))
        assertError(
            { service.getPost(null, POST_ID) },
            CommonErrorCode.RESOURCE_NOT_FOUND)

        val relation = UserFollowEntity()
        relation.followerUserId = USER_ID
        relation.followedUserId = AUTHOR_ID
        whenever(userFollows.find(USER_ID, AUTHOR_ID))
            .thenReturn(Optional.of(relation))
        assertThat(service.getPost(PRINCIPAL, POST_ID).visibility)
            .isEqualTo("FOLLOWERS_ONLY")

        whenever(queries.findPublicCandidateById(POST_ID))
            .thenReturn(Optional.of(personal(2, 3)))
        assertError(
            { service.getPost(PRINCIPAL, POST_ID) },
            CommonErrorCode.RESOURCE_NOT_FOUND)
    }

    @Test
    fun managedCursorIsBoundToStatusAndAuthorFilter() {
        val first = personal(1, 1)
        val second = PostDetailRow(
            7000L, USER_ID, null, "本人", null, 1, 1, "第二条", null,
            null, 0, null, null, PUBLISHED_AT.minusHours(2),
            PUBLISHED_AT.minusHours(1))
        whenever(queries.findManagedPage(
            USER_ID, null, null, null, null, 2))
            .thenReturn(listOf(first, second))
        whenever(queries.findMediaFileIds(any<Long>())).thenReturn(emptyList())

        val page = service.listManagedPosts(
            PRINCIPAL, null, 1, null, null)

        assertThat(page.items).hasSize(1)
        assertThat(page.hasMore).isTrue()
        assertThat(page.nextCursor).isNotBlank()

        assertError(
            { service.listManagedPosts(
                PRINCIPAL, page.nextCursor, 1, "PUBLISHED", null) },
            CommonErrorCode.VALIDATION_FAILED)
    }

    @Test
    fun publicAndFollowingListsUseSeparateBoundQueries() {
        whenever(queries.findPublicPage(isNull(), isNull(), any<Int>()))
            .thenReturn(listOf(personal(2, 1)))
        whenever(queries.findFollowingFeedPage(
            USER_ID, null, null, 2))
            .thenReturn(listOf(personal(2, 2)))

        assertThat(service.listPublicPosts(null, 1).items).hasSize(1)
        assertThat(service.listFollowingFeed(PRINCIPAL, null, 1).items)
            .extracting(Function {  it.visibility  })
            .containsExactly("FOLLOWERS_ONLY")
        verify(queries).findFollowingFeedPage(USER_ID, null, null, 2)
    }

    @Test
    fun authorListsRequireVisibleAuthorAndChooseVisibilityLevel() {
        whenever(profiles.existsActiveCompletedUser(AUTHOR_ID)).thenReturn(true)
        whenever(queries.findAuthorPage(
            "USER", AUTHOR_ID, 1, null, null, 2))
            .thenReturn(listOf(personal(2, 1)))

        assertThat(service.listUserPosts(
            null, AUTHOR_ID, null, 1).items).hasSize(1)

        whenever(profiles.existsActiveCompletedUser(AUTHOR_ID)).thenReturn(false)
        assertError(
            { service.listUserPosts(
                PRINCIPAL, AUTHOR_ID, null, 1) },
            CommonErrorCode.RESOURCE_NOT_FOUND)
    }

    @Test
    fun postMediaUsesManagePermissionOrLivePublishedVisibility() {
        whenever(queries.findByMediaFileId(9001L))
            .thenReturn(Optional.of(personal(2, 2)))
        whenever(queries.isPostAuthorPubliclyAvailable(POST_ID)).thenReturn(true)

        assertThat(service.canReadMedia(null, 9001L)).isFalse()

        val relation = UserFollowEntity()
        whenever(userFollows.find(USER_ID, AUTHOR_ID))
            .thenReturn(Optional.of(relation))
        assertThat(service.canReadMedia(PRINCIPAL, 9001L)).isTrue()

        var ownDraft = personal(1, 3)
        ownDraft = PostDetailRow(
            ownDraft.postId, USER_ID, null, "本人", null,
            ownDraft.status, ownDraft.visibility, ownDraft.title,
            ownDraft.content, ownDraft.activityId, ownDraft.version,
            ownDraft.publishedAt, ownDraft.hiddenAt, ownDraft.createdAt,
            ownDraft.updatedAt)
        whenever(queries.findByMediaFileId(9002L)).thenReturn(Optional.of(ownDraft))
        assertThat(service.canReadMedia(PRINCIPAL, 9002L)).isTrue()
    }

    private fun personal(status: Int, visibility: Int): PostDetailRow {
        return PostDetailRow(
            POST_ID,
            AUTHOR_ID,
            null,
            "作者",
            null,
            status,
            visibility,
            "标题",
            "正文",
            null,
            0,
            if (status == 2) PUBLISHED_AT else null,
            if (status == 4) PUBLISHED_AT.plusMinutes(1) else null,
            PUBLISHED_AT.minusHours(1),
            PUBLISHED_AT)
    }

    private fun organization(status: Int, visibility: Int): PostDetailRow {
        return PostDetailRow(
            POST_ID,
            null,
            ORGANIZATION_ID,
            "企业",
            null,
            status,
            visibility,
            "标题",
            "正文",
            null,
            1,
            PUBLISHED_AT.minusHours(1),
            if (status == 4) PUBLISHED_AT else null,
            PUBLISHED_AT.minusHours(2),
            PUBLISHED_AT)
    }

    private fun assertError(action: () -> Unit, expected: Any) {
        assertThatThrownBy(action)
            .isInstanceOfSatisfying(BusinessException::class.java) { exception ->
                assertThat(exception.errorCode).isSameAs(expected)
            }
    }

    companion object {
        private const val USER_ID = 202L
        private const val AUTHOR_ID = 303L
        private const val ORGANIZATION_ID = 401L
        private const val POST_ID = 7001L
        private val PUBLISHED_AT = LocalDateTime.parse("2026-08-16T08:00:00")
        private val PRINCIPAL = UserPrincipal(USER_ID, "session-post-read")
    }
}
