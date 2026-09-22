package com.eligo.server.recommendation.service

import com.eligo.server.common.api.CursorPage
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.follow.mapper.OrganizationFollowMapper
import com.eligo.server.follow.mapper.UserFollowMapper
import com.eligo.server.post.service.PostReadService
import com.eligo.server.post.vo.PublicPostView
import com.eligo.server.profile.entity.InterestTagEntity
import com.eligo.server.profile.entity.UserProfileEntity
import com.eligo.server.profile.mapper.InterestTagMapper
import com.eligo.server.profile.mapper.UserProfileMapper
import com.eligo.server.recommendation.RecommendationCursorCodec
import com.eligo.server.recommendation.RecommendationDependencyException
import com.eligo.server.recommendation.RecommendationProperties
import com.eligo.server.recommendation.RecommendationTelemetry
import com.eligo.server.recommendation.client.EmbeddingClient
import com.eligo.server.recommendation.client.VectorStoreClient
import com.eligo.server.recommendation.mapper.RecommendationCandidateRow
import com.eligo.server.recommendation.mapper.RecommendationIndexedGeneration
import com.eligo.server.recommendation.mapper.RecommendationQueryMapper
import com.eligo.server.security.UserPrincipal
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Arrays
import java.util.HashMap
import java.util.Optional

class DefaultRecommendedFeedServiceTests {

    private val NOW = Instant.parse("2026-08-18T08:00:00Z")
    private val NOW_LOCAL =
        LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)
    private val PRINCIPAL =
        UserPrincipal(202L, "recommendation-session")

    private val queries = mock(RecommendationQueryMapper::class.java)
    private val interestTags = mock(InterestTagMapper::class.java)
    private val profiles = mock(UserProfileMapper::class.java)
    private val userFollows = mock(UserFollowMapper::class.java)
    private val organizationFollows = mock(OrganizationFollowMapper::class.java)
    private val reads = mock(PostReadService::class.java)
    private val embeddings = mock(EmbeddingClient::class.java)
    private val vectors = mock(VectorStoreClient::class.java)
    private val redis = mock(StringRedisTemplate::class.java)
    private val values = mock(ValueOperations::class.java) as ValueOperations<String, String>
    private val redisStore = HashMap<String, String>()
    private val properties = RecommendationProperties()
    private lateinit var service: DefaultRecommendedFeedService

    @BeforeEach
    fun setUp() {
        properties.candidateLimit = 2
        properties.vectorSize = 2
        `when`(redis.opsForValue()).thenReturn(values)
        doAnswer { invocation ->
            redisStore[invocation.getArgument(0)] =
                invocation.getArgument(1)
            null
        }.`when`(values).set(any<String>(), any<String>(), any<Duration>())
        `when`(values.get(any<String>()))
            .thenAnswer { invocation -> redisStore[invocation.getArgument<String>(0)] }
        service = DefaultRecommendedFeedService(
            queries,
            interestTags,
            profiles,
            userFollows,
            organizationFollows,
            reads,
            embeddings,
            vectors,
            redis,
            properties,
            RecommendationCursorCodec(cursorKey()),
            RecommendationTelemetry(SimpleMeterRegistry()),
            Clock.fixed(NOW, ZoneOffset.UTC)
        )
    }

    @Test
    fun anonymousRequestUsesPublicChronologicalSnapshotWithoutVectorDependencies() {
        properties.enabled = false
        val first = row(7001L, NOW_LOCAL.minusHours(1))
        val second = row(7002L, NOW_LOCAL.minusHours(2))
        `when`(
            queries.findLatestPublicPage(
                isNull(),
                isNull(),
                isNull(),
                eq(listOf()),
                eq(2)
            )
        ).thenReturn(listOf(first, second))
        `when`(queries.findCurrentPublicByIds(listOf(7001L, 7002L), null))
            .thenReturn(listOf(first, second))
        `when`(reads.getPost(null, 7001L)).thenReturn(view())
        `when`(reads.getPost(null, 7002L)).thenReturn(view())

        val page = service.list(null, null, 20)

        assertThat(page.items).hasSize(2)
        assertThat(page.nextCursor).isNull()
        verify(embeddings, never()).embed(any<String>())
        verify(vectors, never()).search(any(), any(), any<Int>(), any())
        verify(values).set(any<String>(), any<String>(), eq(properties.snapshotTtl))
    }

    @Test
    fun vectorResultsAreRankedAndPagedFromTheSameRedisSnapshot() {
        properties.enabled = true
        val tag = InterestTagEntity()
        tag.id = 1900000000000001001L
        tag.tagCode = "OUTDOOR"
        tag.tagName = "户外"
        tag.status = 1
        `when`(interestTags.findSelectedByUserId(202L)).thenReturn(listOf(tag))
        `when`(embeddings.embed(any<String>())).thenReturn(floatArrayOf(0.1f, 0.2f))
        `when`(vectors.search(any(), eq(NOW.minus(Duration.ofDays(90))), eq(2), eq(202L)))
            .thenReturn(
                listOf(
                    VectorStoreClient.VectorHit(7001L, 3L, 0.9),
                    VectorStoreClient.VectorHit(7002L, 4L, 0.2)
                )
            )
        `when`(queries.findSucceededIndexGenerations(listOf(7001L, 7002L)))
            .thenReturn(
                listOf(
                    RecommendationIndexedGeneration(7001L, 3L),
                    RecommendationIndexedGeneration(7002L, 4L)
                )
            )

        val first = row(7001L, NOW_LOCAL.minusHours(1))
        val second = row(7002L, NOW_LOCAL.minusDays(2))
        `when`(queries.findCurrentPublicByIds(any<List<Long>>(), eq(202L)))
            .thenAnswer { invocation ->
                val ids = invocation.getArgument<List<Long>>(0)
                listOf(first, second).filter { row -> ids.contains(row.postId) }
            }
        `when`(userFollows.findFollowedUserIds(eq(202L), any<List<Long>>())).thenReturn(listOf())
        `when`(organizationFollows.findFollowedOrganizationIds(eq(202L), any<List<Long>>()))
            .thenReturn(listOf())
        val profile = UserProfileEntity()
        profile.cityCode = "4403"
        profile.completedAt = NOW_LOCAL.minusDays(10)
        `when`(profiles.findByUserId(202L)).thenReturn(Optional.of(profile))
        `when`(reads.getPost(PRINCIPAL, 7001L)).thenReturn(view())
        `when`(reads.getPost(PRINCIPAL, 7002L)).thenReturn(view())

        val firstPage = service.list(PRINCIPAL, null, 1)

        assertThat(firstPage.items).hasSize(1)
        assertThat(firstPage.nextCursor).isNotBlank()
        val secondPage =
            service.list(PRINCIPAL, firstPage.nextCursor, 1)

        assertThat(secondPage.items).hasSize(1)
        assertThat(secondPage.nextCursor).isNull()
        verify(embeddings).embed(any<String>())
        verify(vectors).search(any(), eq(NOW.minus(Duration.ofDays(90))), eq(2), eq(202L))
    }

    @Test
    fun staleVectorGenerationIsIgnoredBeforeMysqlVisibilityLookup() {
        properties.enabled = true
        val tag = InterestTagEntity()
        tag.id = 1900000000000001001L
        tag.tagCode = "OUTDOOR"
        tag.tagName = "户外"
        tag.status = 1
        `when`(interestTags.findSelectedByUserId(202L)).thenReturn(listOf(tag))
        val profile = UserProfileEntity()
        profile.completedAt = NOW_LOCAL.minusDays(10)
        `when`(profiles.findByUserId(202L)).thenReturn(Optional.of(profile))
        `when`(embeddings.embed(any<String>())).thenReturn(floatArrayOf(0.1f, 0.2f))
        `when`(vectors.search(any(), any(), eq(2), eq(202L)))
            .thenReturn(listOf(VectorStoreClient.VectorHit(7001L, 1L, 0.9)))
        `when`(queries.findSucceededIndexGenerations(listOf(7001L)))
            .thenReturn(listOf(RecommendationIndexedGeneration(7001L, 2L)))
        val current = row(7002L, NOW_LOCAL.minusHours(1))
        `when`(
            queries.findLatestPublicPage(
                eq(202L), isNull(), isNull(), eq(listOf()), eq(2)
            )
        ).thenReturn(listOf(current))
        `when`(queries.findCurrentPublicByIds(listOf(7002L), 202L))
            .thenReturn(listOf(current))
        `when`(reads.getPost(PRINCIPAL, 7002L)).thenReturn(view())

        val page = service.list(PRINCIPAL, null, 20)

        assertThat(page.items).hasSize(1)
        verify(reads, never()).getPost(PRINCIPAL, 7001L)
    }

    @Test
    fun dependencyFailureFallsBackToChronologicalFeed() {
        properties.enabled = true
        val tag = InterestTagEntity()
        tag.id = 1900000000000001001L
        tag.tagCode = "OUTDOOR"
        tag.tagName = "户外"
        tag.status = 1
        `when`(interestTags.findSelectedByUserId(202L)).thenReturn(listOf(tag))
        val profile = UserProfileEntity()
        profile.completedAt = NOW_LOCAL.minusDays(10)
        `when`(profiles.findByUserId(202L)).thenReturn(Optional.of(profile))
        `when`(embeddings.embed(any<String>()))
            .thenThrow(RecommendationDependencyException("Ollama 不可用"))
        val first = row(7001L, NOW_LOCAL.minusHours(1))
        `when`(
            queries.findLatestPublicPage(
                eq(202L),
                isNull(),
                isNull(),
                eq(listOf()),
                eq(2)
            )
        ).thenReturn(listOf(first))
        `when`(queries.findCurrentPublicByIds(eq(listOf(7001L)), eq(202L)))
            .thenReturn(listOf(first))
        `when`(reads.getPost(PRINCIPAL, 7001L)).thenReturn(view())

        val page = service.list(PRINCIPAL, null, 20)

        assertThat(page.items).hasSize(1)
        verify(vectors, never()).search(any(), any(), any<Int>(), any())
    }

    @Test
    fun redisSnapshotFailureFallsBackToIndependentTimeCursor() {
        properties.enabled = false
        val first = row(7001L, NOW_LOCAL.minusHours(1))
        val second = row(7002L, NOW_LOCAL.minusHours(2))
        `when`(
            queries.findLatestPublicPage(
                isNull(),
                isNull(),
                isNull(),
                eq(listOf()),
                eq(2)
            )
        ).thenReturn(listOf(first, second))
        doThrow(DataAccessResourceFailureException("Redis 不可用"))
            .`when`(values)
            .set(any<String>(), any<String>(), any<Duration>())
        `when`(
            queries.findLatestPublicPage(
                isNull(),
                isNull(),
                isNull(),
                eq(listOf()),
                eq(21)
            )
        ).thenReturn(listOf(first, second))
        `when`(reads.getPost(null, 7001L)).thenReturn(view())
        `when`(reads.getPost(null, 7002L)).thenReturn(view())

        val page = service.list(null, null, 20)

        assertThat(page.items).hasSize(2)
        assertThat(page.nextCursor).isNull()
        verify(values).set(any<String>(), any<String>(), eq(properties.snapshotTtl))
    }

    @Test
    fun incompleteProfileUsesChronologicalFeedWithoutReadingInterests() {
        properties.enabled = true
        val profile = UserProfileEntity()
        profile.completedAt = null
        `when`(profiles.findByUserId(202L)).thenReturn(Optional.of(profile))
        val first = row(7001L, NOW_LOCAL.minusHours(1))
        `when`(
            queries.findLatestPublicPage(
                eq(202L), isNull(), isNull(), eq(listOf()), eq(2)
            )
        ).thenReturn(listOf(first))
        `when`(queries.findCurrentPublicByIds(listOf(7001L), 202L))
            .thenReturn(listOf(first))
        `when`(reads.getPost(PRINCIPAL, 7001L)).thenReturn(view())

        val page = service.list(PRINCIPAL, null, 20)

        assertThat(page.items).hasSize(1)
        verify(interestTags, never()).findSelectedByUserId(202L)
        verify(embeddings, never()).embed(any<String>())
        verify(vectors, never()).search(any(), any(), any<Int>(), any())
    }

    @Test
    fun firstPageUsesInMemorySnapshotWhenRedisReadFailsAfterWrite() {
        properties.enabled = false
        val first = row(7001L, NOW_LOCAL.minusHours(1))
        `when`(
            queries.findLatestPublicPage(
                isNull(), isNull(), isNull(), eq(listOf()), eq(2)
            )
        ).thenReturn(listOf(first))
        `when`(queries.findCurrentPublicByIds(listOf(7001L), null))
            .thenReturn(listOf(first))
        `when`(reads.getPost(null, 7001L)).thenReturn(view())
        `when`(values.get(any<String>()))
            .thenThrow(DataAccessResourceFailureException("Redis 读失败"))

        val page = service.list(null, null, 20)

        assertThat(page.items).hasSize(1)
        verify(values, never()).get(any<String>())
    }

    @Test
    fun chronologicalFallbackSkipsConcurrentDeletionAndFillsThePage() {
        properties.enabled = false
        val first = row(7001L, NOW_LOCAL.minusHours(1))
        val second = row(7002L, NOW_LOCAL.minusHours(2))
        val third = row(7003L, NOW_LOCAL.minusHours(3))
        `when`(
            queries.findLatestPublicPage(
                isNull(), isNull(), isNull(), eq(listOf()), eq(2)
            )
        ).thenReturn(listOf(first, second))
        doThrow(DataAccessResourceFailureException("Redis 不可用"))
            .`when`(values)
            .set(any<String>(), any<String>(), any<Duration>())
        `when`(
            queries.findLatestPublicPage(
                isNull(), isNull(), isNull(), eq(listOf()), eq(3)
            )
        ).thenReturn(listOf(first, second, third))
        `when`(reads.getPost(null, 7001L))
            .thenThrow(BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND))
        `when`(reads.getPost(null, 7002L)).thenReturn(view())
        `when`(reads.getPost(null, 7003L)).thenReturn(view())

        val page = service.list(null, null, 2)

        assertThat(page.items).hasSize(2)
        assertThat(page.nextCursor).isNull()
    }

    private fun row(postId: Long, publishedAt: LocalDateTime): RecommendationCandidateRow {
        return RecommendationCandidateRow(
            postId, 303L, null, 303L, publishedAt, "440305"
        )
    }

    private fun view(): PublicPostView {
        return mock(PublicPostView::class.java)
    }

    private fun cursorKey(): ByteArray {
        val key = ByteArray(32)
        Arrays.fill(key, 7)
        return key
    }
}
