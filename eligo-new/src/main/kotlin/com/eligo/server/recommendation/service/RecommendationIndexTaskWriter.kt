package com.eligo.server.recommendation.service

interface RecommendationIndexTaskWriter {

    fun enqueueUpsert(postId: Long)

    fun enqueueDelete(postId: Long)

    companion object {
        @JvmStatic
        fun noop(): RecommendationIndexTaskWriter = object : RecommendationIndexTaskWriter {
            override fun enqueueUpsert(postId: Long) {}
            override fun enqueueDelete(postId: Long) {}
        }
    }
}
