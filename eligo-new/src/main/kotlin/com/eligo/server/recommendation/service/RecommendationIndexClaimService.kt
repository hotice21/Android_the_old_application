package com.eligo.server.recommendation.service

import com.eligo.server.recommendation.RecommendationProperties
import com.eligo.server.recommendation.mapper.RecommendationIndexJobEntity
import com.eligo.server.recommendation.mapper.RecommendationIndexJobMapper
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test")
class RecommendationIndexClaimService(
    private val jobs: RecommendationIndexJobMapper,
    private val properties: RecommendationProperties,
    private val clock: Clock = Clock.systemUTC()
) {

    @Transactional
    fun claimBatch(): List<RecommendationIndexJobEntity> {
        val now = now()
        jobs.recoverExpiredProcessing(
            now.minus(properties.processingLease), now)
        val candidates = jobs.findDueForUpdate(
            now, properties.workerBatchSize)
        for (job in candidates) {
            jobs.markProcessing(job.postId!!, job.generation!!, now)
        }
        return candidates
    }

    private fun now(): LocalDateTime {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
    }
}
