package com.eligo.server.recommendation.mapper

import java.time.LocalDateTime

data class RecommendationPostSource(
    val postId: Long?,
    val authorUserId: Long?,
    val authorOrganizationId: Long?,
    val operatorUserId: Long?,
    val status: Int?,
    val visibility: Int?,
    val authorPubliclyAvailable: Boolean?,
    val title: String?,
    val content: String?,
    val activityId: Long?,
    val activityTitle: String?,
    val activityCategoryCode: String?,
    val publishedAt: LocalDateTime?
)
