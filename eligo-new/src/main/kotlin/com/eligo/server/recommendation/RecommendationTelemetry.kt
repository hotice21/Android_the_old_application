package com.eligo.server.recommendation

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

@Component
class RecommendationTelemetry(
    private val registry: MeterRegistry
) {

    fun recordFeed(outcome: FeedOutcome) {
        increment("eligo.recommendation.feed", outcome.tag)
    }

    fun recordIndex(outcome: IndexOutcome) {
        increment("eligo.recommendation.index", outcome.tag)
    }

    private fun increment(name: String, outcome: String) {
        Counter.builder(name)
            .tag("outcome", outcome)
            .register(registry)
            .increment()
    }

    enum class FeedOutcome(val tag: String) {
        VECTOR("vector"),
        RULE_FALLBACK("rule_fallback"),
        DEPENDENCY_FALLBACK("dependency_fallback"),
        REDIS_FALLBACK("redis_fallback");
    }

    enum class IndexOutcome(val tag: String) {
        SUCCEEDED("succeeded"),
        RETRY("retry"),
        FAILED("failed"),
        STALE_REQUEUED("stale_requeued");
    }
}
