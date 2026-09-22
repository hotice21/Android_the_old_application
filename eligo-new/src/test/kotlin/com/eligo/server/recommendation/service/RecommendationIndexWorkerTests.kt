package com.eligo.server.recommendation.service

import com.eligo.server.recommendation.RecommendationDependencyException
import com.eligo.server.recommendation.RecommendationProperties
import com.eligo.server.recommendation.RecommendationTelemetry
import com.eligo.server.recommendation.client.EmbeddingClient
import com.eligo.server.recommendation.client.VectorStoreClient
import com.eligo.server.recommendation.mapper.RecommendationIndexJobEntity
import com.eligo.server.recommendation.mapper.RecommendationIndexJobMapper
import com.eligo.server.recommendation.mapper.RecommendationPostSource
import com.eligo.server.recommendation.mapper.RecommendationQueryMapper
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.capture
import org.mockito.kotlin.eq
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class RecommendationIndexWorkerTests {

    private val NOW = Instant.parse("2026-08-18T08:00:00Z")
    private val NOW_LOCAL =
        LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)

    private val claims = mock(RecommendationIndexClaimService::class.java)
    private val jobs = mock(RecommendationIndexJobMapper::class.java)
    private val queries = mock(RecommendationQueryMapper::class.java)
    private val embeddings = mock(EmbeddingClient::class.java)
    private val vectors = mock(VectorStoreClient::class.java)
    private val properties = RecommendationProperties()
    private lateinit var worker: RecommendationIndexWorker

    @BeforeEach
    fun setUp() {
        properties.enabled = false
        properties.workerEnabled = true
        properties.vectorSize = 2
        worker = RecommendationIndexWorker(
            claims,
            jobs,
            queries,
            embeddings,
            vectors,
            properties,
            RecommendationTelemetry(SimpleMeterRegistry()),
            Clock.fixed(NOW, ZoneOffset.UTC)
        )
    }

    @Test
    fun workerRunsWhilePersonalizedServingIsDisabled() {
        `when`(claims.claimBatch()).thenReturn(listOf())

        worker.processDueTasks()

        verify(claims).claimBatch()
    }

    @Test
    fun staleSuccessfulUpsertRequeuesLatestDesiredState() {
        val job = job(7001L, 3L, 1, 0)
        `when`(claims.claimBatch()).thenReturn(listOf(job))
        `when`(queries.findSourceById(7001L)).thenReturn(publicSource(7001L))
        `when`(embeddings.embed(any<String>())).thenReturn(floatArrayOf(0.1f, 0.2f))
        `when`(
            jobs.markSucceeded(
                eq(7001L), eq(3L), any<String>(), any<String>(), eq(NOW_LOCAL)
            )
        ).thenReturn(0)
        `when`(jobs.requeueCurrentDesired(7001L, 3L, NOW_LOCAL)).thenReturn(1)

        worker.processDueTasks()

        verify(jobs).requeueCurrentDesired(7001L, 3L, NOW_LOCAL)
        val payload = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, Any>>
        verify(vectors).upsert(eq(7001L), eq(3L), any<FloatArray>(), capture(payload))
        assertThat(payload.value)
            .containsEntry("indexIdentity", properties.indexIdentity())
            .doesNotContainKeys(
                "operatorUserId", "postId", "contentFingerprint", "modelVersion"
            )
    }

    @Test
    fun staleFailureAlsoRequeuesBecauseTimedOutSideEffectMayHaveSucceeded() {
        val job = job(7002L, 8L, 1, 1)
        `when`(claims.claimBatch()).thenReturn(listOf(job))
        `when`(queries.findSourceById(7002L)).thenReturn(publicSource(7002L))
        `when`(embeddings.embed(any<String>())).thenReturn(floatArrayOf(0.1f, 0.2f))
        doThrow(RecommendationDependencyException("Qdrant 超时"))
            .`when`(vectors)
            .upsert(eq(7002L), eq(8L), any<FloatArray>(), any<Map<String, Any>>())
        `when`(
            jobs.markFailedOrRetry(
                eq(7002L), eq(8L), any<Int>(), any<LocalDateTime>(),
                any<String>(), any<String>(), eq(NOW_LOCAL)
            )
        ).thenReturn(0)
        `when`(jobs.requeueCurrentDesired(7002L, 8L, NOW_LOCAL)).thenReturn(1)

        worker.processDueTasks()

        verify(jobs).requeueCurrentDesired(7002L, 8L, NOW_LOCAL)
    }

    @Test
    fun disabledWorkerDoesNotClaimTasksEvenWhenServingIsEnabled() {
        properties.enabled = true
        properties.workerEnabled = false

        worker.processDueTasks()

        verify(claims, never()).claimBatch()
    }

    private fun job(
        postId: Long,
        generation: Long,
        action: Int,
        attemptCount: Int
    ): RecommendationIndexJobEntity {
        val job = RecommendationIndexJobEntity()
        job.postId = postId
        job.generation = generation
        job.desiredAction = action
        job.attemptCount = attemptCount
        return job
    }

    private fun publicSource(postId: Long): RecommendationPostSource {
        return RecommendationPostSource(
            postId,
            303L,
            null,
            404L,
            2,
            1,
            true,
            "测试动态",
            "公开正文",
            null,
            null,
            null,
            NOW_LOCAL.minusHours(1)
        )
    }
}
