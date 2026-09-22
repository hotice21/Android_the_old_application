package com.eligo.server.recommendation

import java.time.Duration
import java.time.Instant

class RecommendationRanker {

    fun rank(
        candidates: List<Candidate>,
        snapshotAt: Instant,
        viewerCityCode: String?,
        followedUserIds: Set<Long>,
        followedOrganizationIds: Set<Long>
    ): List<ScoredCandidate> {
        return candidates
            .map { candidate ->
                score(
                    candidate,
                    snapshotAt,
                    viewerCityCode,
                    followedUserIds,
                    followedOrganizationIds)
            }
            .sortedWith(
                compareByDescending<ScoredCandidate> { it.score }
                    .thenByDescending { it.publishedAt }
                    .thenByDescending { it.postId })
    }

    fun freshness(publishedAt: Instant?, snapshotAt: Instant?): Double {
        if (publishedAt == null || snapshotAt == null) {
            return 0.1
        }
        val age = Duration.between(publishedAt, snapshotAt)
        if (age.isNegative || age <= Duration.ofHours(24)) {
            return 1.0
        }
        if (age <= Duration.ofDays(3)) {
            return 0.8
        }
        if (age <= Duration.ofDays(7)) {
            return 0.6
        }
        if (age <= Duration.ofDays(30)) {
            return 0.3
        }
        return 0.1
    }

    private fun score(
        candidate: Candidate,
        snapshotAt: Instant,
        viewerCityCode: String?,
        followedUserIds: Set<Long>,
        followedOrganizationIds: Set<Long>
    ): ScoredCandidate {
        val semantic = clamp((candidate.similarity + 1.0) / 2.0)
        val fresh = freshness(candidate.publishedAt, snapshotAt)
        val followed = if (candidate.authorUserId != null)
            followedUserIds.contains(candidate.authorUserId)
        else
            followedOrganizationIds.contains(candidate.authorOrganizationId)
        val sameCity = viewerCityCode != null
            && candidate.activityRegionCode != null
            && candidate.activityRegionCode.startsWith(viewerCityCode)
        val finalScore = semantic * 0.70 +
            (fresh * 0.15) +
            (if (followed) 0.10 else 0.0) +
            (if (sameCity) 0.05 else 0.0)
        return ScoredCandidate(
            candidate.postId,
            finalScore,
            candidate.publishedAt)
    }

    private fun clamp(value: Double): Double {
        return value.coerceIn(0.0, 1.0)
    }

    data class Candidate(
        val postId: Long,
        val authorUserId: Long?,
        val authorOrganizationId: Long?,
        val publishedAt: Instant?,
        val activityRegionCode: String?,
        val similarity: Double
    )

    data class ScoredCandidate(
        val postId: Long,
        val score: Double,
        val publishedAt: Instant?
    )
}
