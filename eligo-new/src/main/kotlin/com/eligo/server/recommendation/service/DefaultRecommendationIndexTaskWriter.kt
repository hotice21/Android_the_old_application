package com.eligo.server.recommendation.service

import com.eligo.server.recommendation.RecommendationProperties
import com.eligo.server.recommendation.mapper.RecommendationIndexJobMapper
import java.time.Clock
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Service
@Profile("!test")
class DefaultRecommendationIndexTaskWriter(
    private val jobs: RecommendationIndexJobMapper,
    private val clock: Clock = Clock.systemUTC()
) : RecommendationIndexTaskWriter {

    override fun enqueueUpsert(postId: Long) {
        enqueue(postId, RecommendationIndexJobMapper.ACTION_UPSERT)
    }

    override fun enqueueDelete(postId: Long) {
        enqueue(postId, RecommendationIndexJobMapper.ACTION_DELETE)
    }

    private fun enqueue(postId: Long, action: Int) {
        if (postId <= 0) {
            throw IllegalArgumentException("动态编号无效")
        }
        jobs.enqueue(
            postId,
            action,
            java.time.LocalDateTime.ofInstant(clock.instant(), java.time.ZoneOffset.UTC))
    }
}
