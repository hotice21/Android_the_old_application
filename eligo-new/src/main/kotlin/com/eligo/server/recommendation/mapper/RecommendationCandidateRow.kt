package com.eligo.server.recommendation.mapper

import java.time.LocalDateTime

data class RecommendationCandidateRow(
    val postId: Long?,
    val authorUserId: Long?,
    val authorOrganizationId: Long?,
    val operatorUserId: Long?,
    val publishedAt: LocalDateTime?,
    val activityRegionCode: String?
)
