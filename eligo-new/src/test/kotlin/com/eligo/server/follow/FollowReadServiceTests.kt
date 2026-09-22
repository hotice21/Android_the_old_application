package com.eligo.server.follow

import java.util.function.Consumer

import com.eligo.server.common.api.CursorPage
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.common.error.ErrorCode
import com.eligo.server.follow.mapper.FollowReadMapper
import com.eligo.server.follow.mapper.FollowTargetRow
import com.eligo.server.follow.mapper.FollowerRow
import com.eligo.server.follow.mapper.PublicUserProfileRow
import com.eligo.server.follow.service.DefaultFollowReadService
import com.eligo.server.follow.vo.FollowTargetSummaryView
import com.eligo.server.follow.vo.FollowerSummaryView
import com.eligo.server.follow.vo.PublicUserProfileView
import com.eligo.server.profile.entity.InterestTagEntity
import com.eligo.server.profile.mapper.InterestTagMapper
import com.eligo.server.security.UserPrincipal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FollowReadServiceTests {

    private val userId = 202L
    private val targetUserId = 303L
    private val followedInstant = Instant.parse("2026-08-16T01:02:03Z")
    private val followedAt = LocalDateTime.ofInstant(followedInstant, ZoneOffset.UTC)
    private val principal = UserPrincipal(userId, "session-m4")

    private val reads = mock<FollowReadMapper>()
    private val tags = mock<InterestTagMapper>()
    private lateinit var service: DefaultFollowReadService

    @BeforeEach
    fun setup() {
        service = DefaultFollowReadService(reads, tags)
    }

    @Test
    fun followingPageMapsMixedTargetsAndBindsCursorToNormalizedFilters() {
        val first = FollowTargetRow(
            9002L, "USER", targetUserId, "徒步者", 501L, followedAt
        )
        val second = FollowTargetRow(
            9001L, "ORGANIZATION", 401L, "徒步协会", null,
            followedAt.minusSeconds(1)
        )
        whenever(
            reads.findFollowingPage(
                userId, "USER", "徒步", null, null, true, 2
            )
        ).thenReturn(listOf(first, second))

        val page = service.listFollowing(
            principal, null, 1, " user ", " 徒步 ", null
        )

        assertThat(page.items).singleElement().satisfies(Consumer {  item ->
            assertThat(item.followId).isEqualTo("9002")
            assertThat(item.targetType).isEqualTo("USER")
            assertThat(item.targetId).isEqualTo(targetUserId.toString())
            assertThat(item.avatar!!.url).isEqualTo("/api/v1/files/501/content")
            assertThat(item.followedAt).isEqualTo(followedInstant)
         })
        assertThat(page.hasMore).isTrue()
        assertThat(page.nextCursor).isNotBlank()

        assertError(
            {
                service.listFollowing(
                    principal,
                    page.nextCursor,
                    1,
                    "ORGANIZATION",
                    "徒步",
                    "RECENT"
                )
            },
            CommonErrorCode.VALIDATION_FAILED
        )

        whenever(
            reads.findFollowingPage(
                userId, "USER", "徒步", followedAt, 9002L, true, 2
            )
        ).thenReturn(emptyList())
        val next = service.listFollowing(
            principal, page.nextCursor, 1, "USER", "徒步", "RECENT"
        )
        assertThat(next.items).isEmpty()
    }

    @Test
    fun earliestFollowingPassesAscendingOrderAndLiteralKeyword() {
        whenever(
            reads.findFollowingPage(
                userId, null, "%_", null, null, false, 21
            )
        ).thenReturn(emptyList())

        val page = service.listFollowing(
            principal, null, 20, null, " %_ ", "earliest"
        )

        assertThat(page.items).isEmpty()
        verify(reads).findFollowingPage(
            userId, null, "%_", null, null, false, 21
        )
    }

    @Test
    fun followerPageUsesStableCursorAndPublicFollowerRows() {
        val first = FollowerRow(
            8002L, targetUserId, "山友", 502L, followedAt
        )
        val second = FollowerRow(
            8001L, 304L, "露营者", null, followedAt.minusSeconds(1)
        )
        whenever(
            reads.findFollowerPage(
                userId, "山", null, null, true, 2
            )
        ).thenReturn(listOf(first, second))

        val page = service.listFollowers(
            principal, null, 1, " 山 ", "RECENT"
        )

        assertThat(page.items).singleElement().satisfies(Consumer {  item ->
            assertThat(item.followId).isEqualTo("8002")
            assertThat(item.userId).isEqualTo(targetUserId.toString())
            assertThat(item.nickname).isEqualTo("山友")
            assertThat(item.avatar!!.fileId).isEqualTo("502")
         })
        assertThat(page.nextCursor).isNotBlank()

        assertError(
            { service.listFollowers(principal, page.nextCursor, 1, "山", "EARLIEST") },
            CommonErrorCode.VALIDATION_FAILED
        )
    }

    @Test
    fun publicProfileReturnsOnlyPublicFieldsTagsAndFilteredCounts() {
        val profile = PublicUserProfileRow(
            targetUserId, "徒步者", 501L, "周末登山"
        )
        val tag = InterestTagEntity()
        tag.id = 701L
        tag.tagCode = "OUTDOOR"
        tag.tagName = "户外"
        tag.sortOrder = 1
        whenever(reads.findPublicUserProfile(targetUserId))
            .thenReturn(Optional.of(profile))
        whenever(reads.countPublicFollowing(targetUserId)).thenReturn(3L)
        whenever(reads.countPublicFollowers(targetUserId)).thenReturn(2L)
        whenever(tags.findSelectedByUserId(targetUserId)).thenReturn(listOf(tag))

        val result = service.getPublicUserProfile(targetUserId)

        assertThat(result.userId).isEqualTo(targetUserId.toString())
        assertThat(result.avatar!!.url).isEqualTo("/api/v1/files/501/content")
        assertThat(result.interestTags).singleElement().satisfies(Consumer {  item ->
            assertThat(item.interestTagId).isEqualTo("701")
            assertThat(item.code).isEqualTo("OUTDOOR")
         })
        assertThat(result.followingCount).isEqualTo(3L)
        assertThat(result.followerCount).isEqualTo(2L)
    }

    @Test
    fun unavailablePublicProfileReturns404WithoutReadingPrivateRelations() {
        whenever(reads.findPublicUserProfile(targetUserId))
            .thenReturn(Optional.empty())

        assertError(
            { service.getPublicUserProfile(targetUserId) },
            CommonErrorCode.RESOURCE_NOT_FOUND
        )

        verify(reads, never()).countPublicFollowing(targetUserId)
        verify(reads, never()).countPublicFollowers(targetUserId)
        verify(tags, never()).findSelectedByUserId(targetUserId)
    }

    @Test
    fun rejectsUnauthenticatedInvalidFiltersAndMalformedCursors() {
        assertError(
            { service.listFollowing(null, null, 20, null, null, null) },
            CommonErrorCode.AUTHENTICATION_REQUIRED
        )
        assertError(
            { service.listFollowers(principal, null, 0, null, null) },
            CommonErrorCode.VALIDATION_FAILED
        )
        assertError(
            { service.listFollowing(principal, null, 20, "TEAM", null, null) },
            CommonErrorCode.VALIDATION_FAILED
        )
        assertError(
            { service.listFollowers(principal, null, 20, " ", "RECENT") },
            CommonErrorCode.VALIDATION_FAILED
        )
        assertError(
            { service.listFollowing(principal, "not-base64", 20, null, null, null) },
            CommonErrorCode.VALIDATION_FAILED
        )
        assertError(
            { service.listFollowers(principal, "a".repeat(513), 20, null, null) },
            CommonErrorCode.VALIDATION_FAILED
        )
        assertError(
            { service.getPublicUserProfile(0) },
            CommonErrorCode.VALIDATION_FAILED
        )
    }

    private fun assertError(action: () -> Unit, expected: ErrorCode) {
        assertThatThrownBy(action)
            .isInstanceOfSatisfying(BusinessException::class.java) { exception ->
                assertThat(exception.errorCode).isSameAs(expected)
            }
    }
}
