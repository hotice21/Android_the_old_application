package com.eligo.server.recommendation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.function.Function

class RecommendationRankerTests {

    private val NOW = Instant.parse("2026-08-18T08:00:00Z")
    private val ranker = RecommendationRanker()

    @Test
    fun ranksWithConfiguredWeightsAndDeterministicTieBreakers() {
        val candidates = listOf(
            RecommendationRanker.Candidate(
                2L, 202L, null, Instant.parse("2026-08-18T07:00:00Z"),
                "440103", 1.0
            ),
            RecommendationRanker.Candidate(
                1L, 303L, null, Instant.parse("2026-08-18T07:00:00Z"),
                "440103", 1.0
            ),
            RecommendationRanker.Candidate(
                3L, 404L, null, Instant.parse("2026-08-10T08:00:00Z"),
                "4402", -1.0
            )
        )

        val result = ranker.rank(
            candidates, NOW, "4401", setOf(303L), emptySet()
        )

        assertThat(result).extracting(Function { it: RecommendationRanker.ScoredCandidate -> it.postId })
            .containsExactly(1L, 2L, 3L)
        assertThat(result[0].score).isEqualTo(1.0)
        assertThat(result[1].score).isEqualTo(0.9)
        assertThat(result[2].score).isEqualTo(0.045)
    }

    @Test
    fun freshnessUsesTheFiveSpecifiedBuckets() {
        assertThat(ranker.freshness(Instant.parse("2026-08-17T08:00:00Z"), NOW))
            .isEqualTo(1.0)
        assertThat(ranker.freshness(Instant.parse("2026-08-15T08:00:00Z"), NOW))
            .isEqualTo(0.8)
        assertThat(ranker.freshness(Instant.parse("2026-08-01T08:00:00Z"), NOW))
            .isEqualTo(0.3)
        assertThat(ranker.freshness(Instant.parse("2026-05-01T08:00:00Z"), NOW))
            .isEqualTo(0.1)
    }
}
