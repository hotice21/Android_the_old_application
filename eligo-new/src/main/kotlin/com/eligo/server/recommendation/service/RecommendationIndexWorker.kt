package com.eligo.server.recommendation.service

import com.eligo.server.recommendation.RecommendationProperties
import com.eligo.server.recommendation.RecommendationTelemetry
import com.eligo.server.recommendation.RecommendationTextBuilder
import com.eligo.server.recommendation.client.EmbeddingClient
import com.eligo.server.recommendation.client.VectorStoreClient
import com.eligo.server.recommendation.mapper.RecommendationIndexJobEntity
import com.eligo.server.recommendation.mapper.RecommendationIndexJobMapper
import com.eligo.server.recommendation.mapper.RecommendationPostSource
import com.eligo.server.recommendation.mapper.RecommendationQueryMapper
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@Profile("!test")
class RecommendationIndexWorker(
    private val claims: RecommendationIndexClaimService,
    private val jobs: RecommendationIndexJobMapper,
    private val queries: RecommendationQueryMapper,
    private val embeddings: EmbeddingClient,
    private val vectors: VectorStoreClient,
    private val properties: RecommendationProperties,
    private val telemetry: RecommendationTelemetry,
    private val clock: Clock = Clock.systemUTC()
) {

    private val textBuilder = RecommendationTextBuilder()

    @Scheduled(
        initialDelayString = "\${eligo.recommendation.worker-initial-delay-ms:60000}",
        fixedDelayString = "\${eligo.recommendation.worker-interval-ms:10000}")
    fun processDueTasks() {
        if (!properties.workerEnabled) {
            return
        }
        val batch = claims.claimBatch()
        for (job in batch) {
            processOne(job)
        }
    }

    private fun processOne(job: RecommendationIndexJobEntity) {
        val now = now()
        try {
            val source = queries.findSourceById(job.postId!!)
            if (job.desiredAction == RecommendationIndexJobMapper.ACTION_UPSERT
                && isPublic(source)) {
                val text = textBuilder.postText(
                    source.title,
                    source.content,
                    source.activityTitle,
                    source.activityCategoryCode)
                val fingerprint = textBuilder.fingerprint(text)
                val vector = embeddings.embed(text)
                vectors.upsert(
                    source.postId!!,
                    job.generation!!,
                    vector,
                    payload(source))
                if (job.generation!! > 1) {
                    vectors.delete(source.postId!!, job.generation!! - 1)
                }
                succeed(job, fingerprint, now)
            } else {
                vectors.delete(job.postId!!, job.generation!!)
                succeed(job, null, now)
            }
        } catch (exception: RuntimeException) {
            retry(job, now, exception)
        }
    }

    private fun succeed(
        job: RecommendationIndexJobEntity,
        fingerprint: String?,
        now: LocalDateTime
    ) {
        val updated = jobs.markSucceeded(
            job.postId!!,
            job.generation!!,
            fingerprint,
            properties.indexIdentity(),
            now)
        if (updated == 0) {
            recordRequeue(job, now)
            return
        }
        telemetry.recordIndex(RecommendationTelemetry.IndexOutcome.SUCCEEDED)
    }

    private fun retry(
        job: RecommendationIndexJobEntity,
        now: LocalDateTime,
        exception: RuntimeException
    ) {
        val attempt = (job.attemptCount ?: 0) + 1
        val terminal = attempt >= properties.maxAttempts
        val status = if (terminal)
            RecommendationIndexJobMapper.STATUS_FAILED
        else
            RecommendationIndexJobMapper.STATUS_PENDING
        val updated = jobs.markFailedOrRetry(
            job.postId!!,
            job.generation!!,
            status,
            now.plus(retryDelay(attempt)),
            exception.javaClass.simpleName,
            exception.javaClass.simpleName,
            now)
        if (updated == 0) {
            recordRequeue(job, now)
            return
        }
        if (terminal) {
            telemetry.recordIndex(RecommendationTelemetry.IndexOutcome.FAILED)
            log.error("推荐索引任务最终失败 postId={} generation={}",
                job.postId, job.generation)
        } else {
            telemetry.recordIndex(RecommendationTelemetry.IndexOutcome.RETRY)
            log.warn("推荐索引任务等待重试 postId={} generation={} attempt={}",
                job.postId, job.generation, attempt)
        }
    }

    private fun retryDelay(attempt: Int): Duration {
        return when (minOf(attempt, 5)) {
            1 -> Duration.ofMinutes(1)
            2 -> Duration.ofMinutes(5)
            3 -> Duration.ofMinutes(30)
            4 -> Duration.ofHours(2)
            else -> Duration.ofHours(6)
        }
    }

    private fun isPublic(source: RecommendationPostSource?): Boolean {
        return source != null
            && source.status != null
            && source.status == 2
            && source.visibility != null
            && source.visibility == 1
            && source.authorPubliclyAvailable == true
            && source.publishedAt != null
    }

    private fun payload(source: RecommendationPostSource): Map<String, Any> {
        val payload = LinkedHashMap<String, Any>()
        payload["status"] = "PUBLISHED"
        payload["visibility"] = "PUBLIC"
        payload["authorType"] = if (source.authorUserId == null) "ORGANIZATION" else "USER"
        payload["authorId"] = source.authorUserId ?: source.authorOrganizationId!!
        payload["publishedAtEpochMs"] = source.publishedAt!!
            .toInstant(ZoneOffset.UTC).toEpochMilli()
        payload["indexIdentity"] = properties.indexIdentity()
        return payload
    }

    private fun recordRequeue(
        job: RecommendationIndexJobEntity,
        now: LocalDateTime
    ) {
        val requeued = jobs.requeueCurrentDesired(
            job.postId!!, job.generation!!, now)
        if (requeued > 0) {
            telemetry.recordIndex(RecommendationTelemetry.IndexOutcome.STALE_REQUEUED)
            return
        }
        log.warn("推荐索引旧任务无需补偿 postId={} generation={}",
            job.postId, job.generation)
    }

    private fun now(): LocalDateTime {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
    }

    companion object {
        private val log = LoggerFactory.getLogger(RecommendationIndexWorker::class.java)
    }
}
