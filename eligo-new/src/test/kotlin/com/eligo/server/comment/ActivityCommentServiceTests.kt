package com.eligo.server.comment

import java.util.function.Function

import com.eligo.server.account.service.AccountStateLockService
import com.eligo.server.activity.service.ActivityEngagementAccessService
import com.eligo.server.activity.service.ActivityEngagementSnapshot
import com.eligo.server.comment.dto.ActivityCommentCreateRequest
import com.eligo.server.comment.entity.ActivityCommentEntity
import com.eligo.server.comment.error.ActivityCommentErrorCode
import com.eligo.server.comment.mapper.ActivityCommentMapper
import com.eligo.server.comment.mapper.ActivityCommentRow
import com.eligo.server.comment.service.DefaultActivityCommentService
import com.eligo.server.comment.vo.ActivityCommentView
import com.eligo.server.common.api.CursorPage
import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.profile.service.ProfileCompletionReader
import com.eligo.server.security.UserPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DuplicateKeyException
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Optional

class ActivityCommentServiceTests {

    private val userId = 202L
    private val ownerId = 203L
    private val activityId = 301L
    private val now = Instant.parse("2026-08-22T08:00:00Z")
    private val localNow = LocalDateTime.ofInstant(now, ZoneOffset.UTC)
    private val user = UserPrincipal(userId, "comment-user")
    private val owner = UserPrincipal(ownerId, "comment-owner")

    private val comments = mock(ActivityCommentMapper::class.java)
    private val activities = mock(ActivityEngagementAccessService::class.java)
    private val completion = mock(ProfileCompletionReader::class.java)
    private val accountStates = mock(AccountStateLockService::class.java)
    private lateinit var service: DefaultActivityCommentService

    @BeforeEach
    fun setUp() {
        whenever(completion.isCompleted(userId)).thenReturn(true)
        whenever(completion.isCompleted(ownerId)).thenReturn(true)
        whenever(activities.findPublicById(activityId))
            .thenReturn(
                Optional.of(
                    ActivityEngagementSnapshot(
                        activityId,
                        2,
                        localNow.plusHours(2),
                        ownerId,
                        null
                    )
                )
            )
        service = DefaultActivityCommentService(
            comments,
            activities,
            completion,
            accountStates,
            Clock.fixed(now, ZoneOffset.UTC)
        )
    }

    @Test
    fun createsNormalizedTopLevelCommentOnActivePublishedActivity() {
        whenever(comments.findByIdempotency(userId, "comment-key-1"))
            .thenReturn(Optional.empty())
        whenever(comments.insert(any<ActivityCommentEntity>())).thenAnswer { invocation ->
            val entity = invocation.getArgument<ActivityCommentEntity>(0)
            entity.id = 501L
            1
        }
        whenever(comments.findRowById(501L)).thenReturn(
            row(501L, userId, null, 1, "想问集合点在哪？", null)
        )

        val outcome = service.create(
            user,
            activityId,
            ActivityCommentCreateRequest("  想问集合点在哪？  ", null),
            "comment-key-1"
        )

        assertThat(outcome.replayed).isFalse()
        assertThat(outcome.view.content).isEqualTo("想问集合点在哪？")
        val created = ArgumentCaptor.forClass(ActivityCommentEntity::class.java)
        verify(comments).insert(created.capture())
        verify(accountStates).lockActive(userId)
        assertThat(created.value.content).isEqualTo("想问集合点在哪？")
        assertThat(created.value.parentCommentId).isNull()
        assertThat(created.value.requestFingerprint).hasSize(64)
    }

    @Test
    fun sameIdempotencyRequestReplaysAndDifferentRequestReturns11711() {
        val existing = entity(501L, userId, null, 1, "原内容", null)
        existing.requestFingerprint = service.fingerprint(activityId, null, "原内容")
        existing.idempotencyKey = "comment-key-2"
        whenever(comments.findByIdempotency(userId, "comment-key-2"))
            .thenReturn(Optional.of(existing))
        whenever(comments.findRowById(501L)).thenReturn(
            row(501L, userId, null, 1, "原内容", null)
        )

        assertThat(
            service.create(
                user,
                activityId,
                ActivityCommentCreateRequest(" 原内容 ", null),
                "comment-key-2"
            ).replayed
        ).isTrue()
        verify(comments, never()).insert(any<ActivityCommentEntity>())

        assertError(
            {
                service.create(
                    user,
                    activityId,
                    ActivityCommentCreateRequest("其他内容", null),
                    "comment-key-2"
                )
            },
            ActivityCommentErrorCode.IDEMPOTENCY_KEY_CONFLICT
        )
    }

    @Test
    fun duplicateInsertReplaysWinnerUsingCurrentReadRow() {
        val key = "comment-key-concurrent"
        val winner = entity(503L, userId, null, 1, "并发留言", null)
        winner.requestFingerprint = service.fingerprint(activityId, null, "并发留言")
        winner.idempotencyKey = key
        whenever(comments.findByIdempotency(userId, key)).thenReturn(Optional.empty())
        whenever(comments.insert(any<ActivityCommentEntity>()))
            .thenThrow(DuplicateKeyException("并发同键评论"))
        whenever(comments.lockByIdempotency(userId, key))
            .thenReturn(Optional.of(winner))
        whenever(comments.findRowByIdForUpdate(503L)).thenReturn(
            row(503L, userId, null, 1, "并发留言", null)
        )

        val outcome = service.create(
            user,
            activityId,
            ActivityCommentCreateRequest("并发留言", null),
            key
        )

        assertThat(outcome.replayed).isTrue()
        assertThat(outcome.view.content).isEqualTo("并发留言")
        verify(comments).findRowByIdForUpdate(503L)
        verify(comments, never()).findRowById(503L)
    }

    @Test
    fun newCommentRequiresCompletedProfileAndActivePublishedActivity() {
        whenever(comments.findByIdempotency(userId, "comment-key-3"))
            .thenReturn(Optional.empty())
        whenever(completion.isCompleted(userId)).thenReturn(false)

        assertError(
            {
                service.create(
                    user,
                    activityId,
                    ActivityCommentCreateRequest("留言", null),
                    "comment-key-3"
                )
            },
            AccountUserFileErrorCode.PROFILE_INCOMPLETE
        )

        whenever(completion.isCompleted(userId)).thenReturn(true)
        whenever(activities.findPublicById(activityId))
            .thenReturn(
                Optional.of(
                    ActivityEngagementSnapshot(activityId, 4, localNow, ownerId, null)
                )
            )
        assertError(
            {
                service.create(
                    user,
                    activityId,
                    ActivityCommentCreateRequest("留言", null),
                    "comment-key-3"
                )
            },
            CommonErrorCode.CONFLICT
        )
    }

    @Test
    fun onlyOwnerMayReplyToActiveTopLevelComment() {
        val parent = entity(501L, userId, null, 1, "顶层留言", null)
        whenever(comments.findByIdempotency(any<Long>(), any()))
            .thenReturn(Optional.empty())
        whenever(comments.findById(501L)).thenReturn(Optional.of(parent))
        whenever(activities.isOwner(eq(ownerId), any())).thenReturn(true)

        assertError(
            {
                service.create(
                    user,
                    activityId,
                    ActivityCommentCreateRequest("普通用户回复", 501L),
                    "comment-key-4"
                )
            },
            CommonErrorCode.ACCESS_DENIED
        )

        whenever(comments.insert(any<ActivityCommentEntity>())).thenAnswer { invocation ->
            val entity = invocation.getArgument<ActivityCommentEntity>(0)
            entity.id = 502L
            1
        }
        whenever(comments.findRowById(502L)).thenReturn(
            row(502L, ownerId, 501L, 1, "领队回复", null)
        )
        assertThat(
            service.create(
                owner,
                activityId,
                ActivityCommentCreateRequest("领队回复", 501L),
                "comment-key-5"
            ).view.parentCommentId
        ).isEqualTo("501")

        parent.parentCommentId = 499L
        assertError(
            {
                service.create(
                    owner,
                    activityId,
                    ActivityCommentCreateRequest("二级回复", 501L),
                    "comment-key-6"
                )
            },
            CommonErrorCode.VALIDATION_FAILED
        )
    }

    @Test
    fun anonymousListKeepsDeletedPlaceholderAndStableAscendingOrder() {
        whenever(comments.findPage(activityId, null, null, 3)).thenReturn(
            listOf(
                row(501L, userId, null, 2, null, localNow.plusMinutes(1)),
                row(502L, ownerId, 501L, 1, "领队回复", null)
            )
        )

        val page = service.list(activityId, null, 2)

        assertThat(page.items).extracting(Function { it.commentId })
            .containsExactly("501", "502")
        assertThat(page.items[0].deleted).isTrue()
        assertThat(page.items[0].content).isNull()
        assertThat(page.items[0].author).isNull()
        assertThat(page.items[1].parentCommentId).isEqualTo("501")
    }

    @Test
    fun authorOrActivityOwnerMaySoftDeleteAndRepeatedDeleteIsIdempotent() {
        val active = entity(501L, userId, null, 1, "待删除", null)
        whenever(comments.findById(501L)).thenReturn(Optional.of(active))
        whenever(comments.softDelete(501L, localNow)).thenReturn(1)
        whenever(comments.findRowById(501L)).thenReturn(
            row(501L, userId, null, 2, null, localNow)
        )

        val deleted = service.delete(user, activityId, 501L)
        assertThat(deleted.deleted).isTrue()
        verify(comments).softDelete(501L, localNow)

        active.status = 2
        active.content = null
        active.deletedAt = localNow
        assertThat(service.delete(user, activityId, 501L).deleted).isTrue()

        active.status = 1
        active.content = "待管理"
        whenever(activities.findById(activityId))
            .thenReturn(
                Optional.of(
                    ActivityEngagementSnapshot(activityId, 5, localNow.plusHours(2), ownerId, null)
                )
            )
        whenever(activities.isOwner(eq(ownerId), any())).thenReturn(true)
        assertThat(service.delete(owner, activityId, 501L).deleted).isTrue()
    }

    private fun entity(
        id: Long,
        authorId: Long,
        parentId: Long?,
        status: Int,
        content: String?,
        deletedAt: LocalDateTime?
    ): ActivityCommentEntity {
        val entity = ActivityCommentEntity()
        entity.id = id
        entity.activityId = activityId
        entity.authorUserId = authorId
        entity.parentCommentId = parentId
        entity.status = status
        entity.content = content
        entity.createdAt = localNow
        entity.deletedAt = deletedAt
        return entity
    }

    private fun row(
        id: Long,
        authorId: Long,
        parentId: Long?,
        status: Int,
        content: String?,
        deletedAt: LocalDateTime?
    ): ActivityCommentRow {
        return ActivityCommentRow(
            id,
            activityId,
            authorId,
            parentId,
            status,
            content,
            "测试用户",
            null,
            localNow,
            deletedAt
        )
    }

    private fun assertError(action: () -> Unit, errorCode: Any) {
        assertThatThrownBy(action)
            .isInstanceOfSatisfying(BusinessException::class.java) { exception ->
                assertThat(exception.errorCode)
                    .isEqualTo(errorCode)
            }
    }
}
