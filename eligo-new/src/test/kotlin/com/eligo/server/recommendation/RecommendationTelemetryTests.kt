package com.eligo.server.recommendation

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RecommendationTelemetryTests {

    @Test
    fun recordsOnlyStableFeedAndIndexOutcomes() {
        val registry = SimpleMeterRegistry()
        val telemetry = RecommendationTelemetry(registry)

        telemetry.recordFeed(RecommendationTelemetry.FeedOutcome.VECTOR)
        telemetry.recordFeed(RecommendationTelemetry.FeedOutcome.DEPENDENCY_FALLBACK)
        telemetry.recordIndex(RecommendationTelemetry.IndexOutcome.SUCCEEDED)
        telemetry.recordIndex(RecommendationTelemetry.IndexOutcome.STALE_REQUEUED)

        assertThat(counter(registry, "eligo.recommendation.feed", "vector"))
            .isEqualTo(1.0)
        assertThat(counter(registry, "eligo.recommendation.feed", "dependency_fallback"))
            .isEqualTo(1.0)
        assertThat(counter(registry, "eligo.recommendation.index", "succeeded"))
            .isEqualTo(1.0)
        assertThat(counter(registry, "eligo.recommendation.index", "stale_requeued"))
            .isEqualTo(1.0)
    }

    private fun counter(
        registry: SimpleMeterRegistry,
        name: String,
        outcome: String
    ): Double {
        return registry.get(name)
            .tag("outcome", outcome)
            .counter()
            .count()
    }
}
