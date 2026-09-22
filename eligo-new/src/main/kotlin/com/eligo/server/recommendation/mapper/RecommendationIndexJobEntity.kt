package com.eligo.server.recommendation.mapper

import java.time.LocalDateTime

class RecommendationIndexJobEntity {
    var postId: Long? = null
    var generation: Long? = null
    var desiredAction: Int? = null
    var contentFingerprint: String? = null
    var taskStatus: Int? = null
    var attemptCount: Int? = null
    var nextAttemptAt: LocalDateTime? = null
    var processingStartedAt: LocalDateTime? = null
    var indexedModelVersion: String? = null
    var lastErrorCode: String? = null
    var lastErrorSummary: String? = null
    var createdAt: LocalDateTime? = null
    var updatedAt: LocalDateTime? = null
}
