package com.eligo.server.recommendation.client

interface EmbeddingClient {

    fun embed(text: String): FloatArray
}
