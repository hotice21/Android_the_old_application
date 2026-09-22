package com.eligo.server.recommendation.service

import com.eligo.server.recommendation.RecommendationProperties
import com.eligo.server.recommendation.mapper.RecommendationBackfillCandidate
import com.eligo.server.recommendation.mapper.RecommendationIndexJobMapper
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@Profile("!test")
class RecommendationIndexBackfillJob(
    private val jobs: RecommendationIndexJobMapper,
    private val taskWriter: RecommendationIndexTaskWriter,
    private val properties: RecommendationProperties,
    private val clock: Clock = Clock.systemUTC()
) {

    @Scheduled(
        initialDelayString = "\${eligo.recommendation.backfill-initial-delay-ms:60000}",
        fixedDelayString = "\${eligo.recommendation.backfill-interval-ms:600000}")
    fun enqueueMissingPublicPosts() {
        if (!properties.backfillEnabled) {
            return
        }
        val failedBefore = LocalDateTime.ofInstant(
            clock.instant().minus(properties.failedRetryCooldown),
            ZoneOffset.UTC)
        val candidates = jobs.findBackfillCandidates(
            properties.indexIdentity(),
            failedBefore,
            properties.workerBatchSize)
        for (candidate in candidates) {
            if (candidate.desiredAction == RecommendationIndexJobMapper.ACTION_DELETE) {
                taskWriter.enqueueDelete(candidate.postId)
            } else {
                taskWriter.enqueueUpsert(candidate.postId)
            }
        }
    }
}
