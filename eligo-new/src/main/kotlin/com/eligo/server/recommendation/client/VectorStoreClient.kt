package com.eligo.server.recommendation.client

import java.time.Instant

interface VectorStoreClient {

    fun ensureCollection()

    fun upsert(
        postId: Long,
        generation: Long,
        vector: FloatArray,
        payload: Map<String, Any>)

    fun delete(postId: Long, throughGeneration: Long)

    fun search(
        vector: FloatArray,
        minimumPublishedAt: Instant,
        limit: Int,
        excludedAuthorUserId: Long?
    ): List<VectorHit>

    data class VectorHit(val postId: Long, val generation: Long, val score: Double)
}
