package com.eligo.server.recommendation.service

import com.eligo.server.recommendation.RecommendationProperties
import com.eligo.server.recommendation.mapper.RecommendationBackfillCandidate
import com.eligo.server.recommendation.mapper.RecommendationIndexJobMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class RecommendationIndexBackfillJobTests {

    private val NOW = Instant.parse("2026-08-18T08:00:00Z")
    private val jobs = mock(RecommendationIndexJobMapper::class.java)
    private val writer = mock(RecommendationIndexTaskWriter::class.java)
    private val properties = RecommendationProperties()
    private lateinit var backfill: RecommendationIndexBackfillJob

    @BeforeEach
    fun setUp() {
        properties.enabled = false
        properties.workerEnabled = true
        properties.backfillEnabled = true
        properties.failedRetryCooldown = Duration.ofHours(6)
        backfill = RecommendationIndexBackfillJob(
            jobs,
            writer,
            properties,
            Clock.fixed(NOW, ZoneOffset.UTC)
        )
    }

    @Test
    fun backfillRunsBeforePersonalizedServingIsEnabled() {
        val failedBefore = LocalDateTime.ofInstant(
            NOW.minus(Duration.ofHours(6)), ZoneOffset.UTC
        )
        `when`(
            jobs.findBackfillCandidates(
                properties.indexIdentity(), failedBefore, 20
            )
        ).thenReturn(
            listOf(
                RecommendationBackfillCandidate(
                    7001L, RecommendationIndexJobMapper.ACTION_UPSERT
                ),
                RecommendationBackfillCandidate(
                    7002L, RecommendationIndexJobMapper.ACTION_DELETE
                )
            )
        )

        backfill.enqueueMissingPublicPosts()

        verify(writer).enqueueUpsert(7001L)
        verify(writer).enqueueDelete(7002L)
    }

    @Test
    fun disabledBackfillDoesNothing() {
        properties.backfillEnabled = false

        backfill.enqueueMissingPublicPosts()

        verify(jobs, never()).findBackfillCandidates(
            any(),
            any(),
            any<Int>()
        )
    }
}
