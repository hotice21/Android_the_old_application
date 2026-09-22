package com.eligo.server.post

import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.file.entity.FileObjectEntity
import com.eligo.server.file.mapper.FileObjectMapper
import com.eligo.server.organization.mapper.OrganizationMapper
import com.eligo.server.post.dto.PostDraftCreateRequest
import com.eligo.server.post.dto.PostDraftReplaceRequest
import com.eligo.server.post.entity.PostEntity
import com.eligo.server.post.entity.PostMediaEntity
import com.eligo.server.post.entity.PostStatusEventEntity
import com.eligo.server.post.error.PostErrorCode
import com.eligo.server.post.mapper.PostMapper
import com.eligo.server.post.mapper.PostMediaMapper
import com.eligo.server.post.mapper.PostStatusEventMapper
import com.eligo.server.post.service.DefaultPostCommandService
import com.eligo.server.post.service.PostReadService
import com.eligo.server.post.vo.ManagedPostDetailView
import com.eligo.server.profile.service.ProfileCompletionReader
import com.eligo.server.recommendation.service.RecommendationIndexTaskWriter
import com.eligo.server.security.UserPrincipal
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DuplicateKeyException

class PostCommandServiceTests {

    private val posts = mock<PostMapper>()
    private val media = mock<PostMediaMapper>()
    private val events = mock<PostStatusEventMapper>()
    private val files = mock<FileObjectMapper>()
    private val organizations = mock<OrganizationMapper>()
    private val completion = mock<ProfileCompletionReader>()
    private val reads = mock<PostReadService>()
    private val recommendationTasks = mock<RecommendationIndexTaskWriter>()
    private lateinit var service: DefaultPostCommandService

    @BeforeEach
    fun setUp() {
        whenever(completion.isCompleted(USER_ID)).thenReturn(true)
        whenever(reads.getManagedPost(PRINCIPAL, POST_ID)).thenReturn(view("DRAFT", 0))
        service = DefaultPostCommandService(
            posts,
            media,
            events,
            files,
            organizations,
            completion,
            reads,
            recommendationTasks,
            Clock.fixed(NOW, ZoneOffset.UTC))
    }

    @Test
    fun personalCreateChecksProfileBeforeIdempotencyAndRejectsEmptyDraft() {
        whenever(completion.isCompleted(USER_ID)).thenReturn(false)

        assertError(
            { service.createPersonal(
                PRINCIPAL,
                PostDraftCreateRequest(null, null, null, null, emptyList()),
                "post-create-001") },
            AccountUserFileErrorCode.PROFILE_INCOMPLETE)
        verify(posts, never()).findByCreateIdempotency(any(), any())

        whenever(completion.isCompleted(USER_ID)).thenReturn(true)
        assertError(
            { service.createPersonal(
                PRINCIPAL,
                PostDraftCreateRequest("  ", "\n", "PUBLIC", null, emptyList()),
                "post-create-001") },
            CommonErrorCode.VALIDATION_FAILED)

        val invalidMedia = arrayListOf<Long?>()
        invalidMedia.add(null)
        assertError(
            { service.createPersonal(
                PRINCIPAL,
                PostDraftCreateRequest("标题", null, "PUBLIC", null, invalidMedia),
                "post-create-001") },
            CommonErrorCode.VALIDATION_FAILED)
    }

    @Test
    fun personalCreatePersistsDraftBindsAndActivatesPostImages() {
        val file = temporaryPostFile()
        whenever(posts.findByCreateIdempotency(any(), any())).thenReturn(Optional.empty())
        whenever(files.lockById(FILE_ID)).thenReturn(Optional.of(file))
        whenever(media.findPostIdByFileId(FILE_ID)).thenReturn(Optional.empty())
        whenever(files.activate(FILE_ID, LOCAL_NOW, 0)).thenReturn(1)
        whenever(posts.insert(any<PostEntity>())).thenAnswer { invocation ->
            invocation.getArgument<PostEntity>(0).id = POST_ID
            1
        }

        val outcome = service.createPersonal(
            PRINCIPAL,
            PostDraftCreateRequest(
                "  标题  ", null, null, null, listOf(FILE_ID)),
            "post-create-001")

        assertThat(outcome.replayed).isFalse()
        assertThat(outcome.view.postId).isEqualTo(POST_ID.toString())
        verify(posts).insert(any<PostEntity>())
        verify(media).insert(any<PostMediaEntity>())
        verify(files).activate(FILE_ID, LOCAL_NOW, 0)
        verify(events, never()).insert(any<PostStatusEventEntity>())
    }

    @Test
    fun createReplaysMatchingDraftAndRejectsConflictOrDeletedResult() {
        val draft = post(1)
        draft.createRequestFingerprint = "same"
        draft.createIdempotencyExpiresAt = LOCAL_NOW.plusHours(1)
        whenever(posts.findByCreateIdempotency("USER:202:202", "post-create-001"))
            .thenReturn(Optional.of(draft))
        whenever(posts.lockByCreateIdempotency("USER:202:202", "post-create-001"))
            .thenReturn(Optional.of(draft))
        whenever(reads.getManagedPostForReplay(PRINCIPAL, POST_ID))
            .thenReturn(view("DRAFT", 0))
        service = serviceWithFingerprint("same")

        val replay = service.createPersonal(
            PRINCIPAL,
            PostDraftCreateRequest("标题", null, "PUBLIC", null, emptyList()),
            "post-create-001")
        assertThat(replay.replayed).isTrue()
        verify(reads).getManagedPostForReplay(PRINCIPAL, POST_ID)

        draft.createRequestFingerprint = "different"
        assertError(
            { service.createPersonal(
                PRINCIPAL,
                PostDraftCreateRequest("标题", null, "PUBLIC", null, emptyList()),
                "post-create-001") },
            PostErrorCode.IDEMPOTENCY_KEY_CONFLICT)

        draft.createRequestFingerprint = "same"
        draft.status = 3
        assertError(
            { service.createPersonal(
                PRINCIPAL,
                PostDraftCreateRequest("标题", null, "PUBLIC", null, emptyList()),
                "post-create-001") },
            PostErrorCode.IDEMPOTENCY_RESULT_DELETED)
    }

    @Test
    fun existingCreateKeyTakesPriorityOverInvalidPersonalAndOrganizationContent() {
        val personal = post(1)
        personal.createRequestFingerprint = "personal"
        personal.createIdempotencyExpiresAt = LOCAL_NOW.plusHours(1)
        whenever(posts.findByCreateIdempotency("USER:202:202", "post-create-001"))
            .thenReturn(Optional.of(personal))
        whenever(posts.lockByCreateIdempotency("USER:202:202", "post-create-001"))
            .thenReturn(Optional.of(personal))

        assertError(
            { service.createPersonal(
                PRINCIPAL,
                PostDraftCreateRequest(null, null, null, null, emptyList()),
                "post-create-001") },
            PostErrorCode.IDEMPOTENCY_KEY_CONFLICT)

        val organization = organizationPost(1)
        organization.createRequestFingerprint = "organization"
        organization.createIdempotencyExpiresAt = LOCAL_NOW.plusHours(1)
        whenever(organizations.isActiveSoleOwner(USER_ID, ORGANIZATION_ID))
            .thenReturn(true)
        whenever(posts.findByCreateIdempotency(
            "ORGANIZATION:401:202", "post-create-002"))
            .thenReturn(Optional.of(organization))
        whenever(posts.lockByCreateIdempotency(
            "ORGANIZATION:401:202", "post-create-002"))
            .thenReturn(Optional.of(organization))

        assertError(
            { service.createOrganization(
                PRINCIPAL,
                ORGANIZATION_ID,
                PostDraftCreateRequest("标题", null, "INVALID", null, emptyList()),
                "post-create-002") },
            PostErrorCode.IDEMPOTENCY_KEY_CONFLICT)
    }

    @Test
    fun concurrentCreateReplaysWinnerThroughCurrentRead() {
        val winner = post(1)
        winner.createRequestFingerprint = "same"
        whenever(posts.findByCreateIdempotency("USER:202:202", "post-create-001"))
            .thenReturn(Optional.empty())
        whenever(posts.insert(any<PostEntity>()))
            .thenThrow(DuplicateKeyException("duplicate"))
        whenever(posts.lockByCreateIdempotency("USER:202:202", "post-create-001"))
            .thenReturn(Optional.of(winner))
        whenever(reads.getManagedPostForReplay(PRINCIPAL, POST_ID))
            .thenReturn(view("DRAFT", 0))
        service = serviceWithFingerprint("same")

        val replay = service.createPersonal(
            PRINCIPAL,
            PostDraftCreateRequest("标题", null, "PUBLIC", null, emptyList()),
            "post-create-001")

        assertThat(replay.replayed).isTrue()
        verify(reads).getManagedPostForReplay(PRINCIPAL, POST_ID)
    }

    @Test
    fun replaceChecksOwnershipThenVersionThenDraftStatus() {
        val post = post(1)
        whenever(posts.lockById(POST_ID)).thenReturn(Optional.of(post))

        assertError(
            { service.replacePersonal(
                UserPrincipal(999L, "other"), POST_ID,
                PostDraftReplaceRequest(0, "标题", null, "PUBLIC", null, emptyList())) },
            CommonErrorCode.RESOURCE_NOT_FOUND)

        assertError(
            { service.replacePersonal(
                PRINCIPAL, POST_ID,
                PostDraftReplaceRequest(1, "标题", null, "PUBLIC", null, emptyList())) },
            PostErrorCode.VERSION_CONFLICT)

        post.version = 1
        post.status = 2
        assertError(
            { service.replacePersonal(
                PRINCIPAL, POST_ID,
                PostDraftReplaceRequest(1, "标题", null, "PUBLIC", null, emptyList())) },
            PostErrorCode.STATUS_CONFLICT)
    }

    @Test
    fun publishReturnsPublishedIdempotentlyBeforeCapabilityCheck() {
        val published = post(2)
        published.publishedAt = LOCAL_NOW.minusMinutes(1)
        whenever(posts.lockById(POST_ID)).thenReturn(Optional.of(published))
        whenever(completion.isCompleted(USER_ID)).thenReturn(false)
        whenever(reads.getManagedPost(PRINCIPAL, POST_ID)).thenReturn(view("PUBLISHED", 1))

        assertThat(service.publish(PRINCIPAL, POST_ID).status).isEqualTo("PUBLISHED")
        verify(posts, never()).publishById(any<Long>(), any())
    }

    @Test
    fun publishAndDeleteWriteStatusEventsAndPersonalDeleteNeedsNoCapability() {
        val draft = post(1)
        draft.title = "标题"
        draft.content = "正文"
        whenever(posts.lockById(POST_ID)).thenReturn(Optional.of(draft))
        whenever(posts.publishById(POST_ID, LOCAL_NOW)).thenReturn(1)
        whenever(reads.getManagedPost(PRINCIPAL, POST_ID)).thenReturn(view("PUBLISHED", 1))

        assertThat(service.publish(PRINCIPAL, POST_ID).status).isEqualTo("PUBLISHED")
        verify(events).insert(any<PostStatusEventEntity>())
        verify(recommendationTasks).enqueueUpsert(POST_ID)
        clearInvocations(completion)

        val published = post(2)
        published.publishedAt = LOCAL_NOW
        whenever(posts.lockById(POST_ID)).thenReturn(Optional.of(published))
        whenever(posts.softDeleteById(POST_ID, 2, LOCAL_NOW)).thenReturn(1)
        whenever(completion.isCompleted(USER_ID)).thenReturn(false)

        service.delete(PRINCIPAL, POST_ID)

        verify(posts).softDeleteById(POST_ID, 2, LOCAL_NOW)
        verify(recommendationTasks).enqueueDelete(POST_ID)
        verify(completion, never()).isCompleted(USER_ID)
    }

    @Test
    fun organizationPublishAndDeleteRejectIncompleteOwnerAtServiceBoundary() {
        val draft = organizationPost(1)
        draft.title = "标题"
        draft.content = "正文"
        whenever(posts.lockById(POST_ID)).thenReturn(Optional.of(draft))
        whenever(organizations.isSoleOwner(USER_ID, ORGANIZATION_ID)).thenReturn(true)
        whenever(completion.isCompleted(USER_ID)).thenReturn(false)

        assertError(
            { service.publish(PRINCIPAL, POST_ID) },
            AccountUserFileErrorCode.PROFILE_INCOMPLETE)
        verify(posts, never()).publishById(any<Long>(), any())

        val hidden = organizationPost(4)
        hidden.publishedAt = LOCAL_NOW.minusHours(1)
        hidden.hiddenAt = LOCAL_NOW
        whenever(posts.lockById(POST_ID)).thenReturn(Optional.of(hidden))
        whenever(organizations.isActiveSoleOwner(USER_ID, ORGANIZATION_ID))
            .thenReturn(true)

        assertError(
            { service.delete(PRINCIPAL, POST_ID) },
            CommonErrorCode.RESOURCE_NOT_FOUND)
        verify(posts, never()).softDeleteById(any<Long>(), any<Int>(), any())
    }

    @Test
    fun organizationCommandsHideMissingWritePermission() {
        whenever(organizations.isActiveSoleOwner(USER_ID, ORGANIZATION_ID)).thenReturn(false)

        assertError(
            { service.createOrganization(
                PRINCIPAL,
                ORGANIZATION_ID,
                PostDraftCreateRequest("标题", null, "PUBLIC", null, emptyList()),
                "post-create-001") },
            CommonErrorCode.RESOURCE_NOT_FOUND)
    }

    private fun serviceWithFingerprint(fingerprint: String): DefaultPostCommandService {
        return object : DefaultPostCommandService(
            posts,
            media,
            events,
            files,
            organizations,
            completion,
            reads,
            clock = Clock.fixed(NOW, ZoneOffset.UTC)) {
            override fun fingerprint(
                authorType: String,
                authorId: Long,
                title: String?,
                content: String?,
                visibility: Int,
                activityId: Long?,
                mediaFileIds: List<Long>
            ): String = fingerprint
        }
    }

    private fun post(status: Int): PostEntity {
        val post = PostEntity()
        post.id = POST_ID
        post.authorUserId = USER_ID
        post.operatorUserId = USER_ID
        post.status = status
        post.visibility = 1
        post.title = "标题"
        post.version = 0
        post.createdAt = LOCAL_NOW.minusHours(1)
        post.updatedAt = LOCAL_NOW.minusHours(1)
        return post
    }

    private fun organizationPost(status: Int): PostEntity {
        val post = post(status)
        post.authorUserId = null
        post.authorOrganizationId = ORGANIZATION_ID
        return post
    }

    private fun temporaryPostFile(): FileObjectEntity {
        val file = FileObjectEntity()
        file.id = FILE_ID
        file.uploaderType = FileObjectEntity.UPLOADER_USER
        file.uploaderId = USER_ID
        file.purpose = FileObjectEntity.PURPOSE_POST
        file.contentType = "image/png"
        file.sizeBytes = 1024L
        file.accessLevel = FileObjectEntity.ACCESS_PUBLIC
        file.scanStatus = FileObjectEntity.SCAN_PASSED
        file.lifecycleStatus = FileObjectEntity.LIFECYCLE_TEMPORARY
        file.expiresAt = LOCAL_NOW.plusHours(1)
        file.version = 0
        return file
    }

    private fun view(status: String, version: Int): ManagedPostDetailView {
        return ManagedPostDetailView(
            POST_ID.toString(), null, status, "PUBLIC", "标题", null,
            emptyList(), null, version, null, LOCAL_NOW.toInstant(ZoneOffset.UTC),
            LOCAL_NOW.toInstant(ZoneOffset.UTC), null)
    }

    private fun assertError(action: () -> Unit, expected: Any) {
        assertThatThrownBy(action)
            .isInstanceOfSatisfying(BusinessException::class.java) { exception ->
                assertThat(exception.errorCode).isSameAs(expected)
            }
    }

    companion object {
        private const val USER_ID = 202L
        private const val ORGANIZATION_ID = 401L
        private const val POST_ID = 7001L
        private const val FILE_ID = 8001L
        private val NOW = Instant.parse("2026-08-16T08:00:00Z")
        private val LOCAL_NOW = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)
        private val PRINCIPAL = UserPrincipal(USER_ID, "session-post")
    }
}
