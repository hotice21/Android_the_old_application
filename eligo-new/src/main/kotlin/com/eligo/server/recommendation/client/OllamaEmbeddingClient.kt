package com.eligo.server.recommendation.client

import com.eligo.server.recommendation.RecommendationDependencyException
import com.eligo.server.recommendation.RecommendationProperties
import java.time.Duration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

@Component
@Profile("!test")
class OllamaEmbeddingClient : EmbeddingClient {

    private val properties: RecommendationProperties
    private val client: RestClient

    @Autowired
    constructor(properties: RecommendationProperties) : this(properties, RestClient.builder())

    constructor(
        properties: RecommendationProperties,
        builder: RestClient.Builder
    ) {
        this.properties = properties
        this.client = builder
            .baseUrl(java.net.URI.create(properties.ollamaUrl).toString())
            .requestFactory(requestFactory(properties.embeddingTimeout))
            .build()
    }

    override fun embed(text: String): FloatArray {
        if (text.isBlank()) {
            throw RecommendationDependencyException("嵌入文本为空")
        }
        try {
            val response = client.post()
                .uri("/api/embed")
                .body(mapOf(
                    "model" to properties.model,
                    "input" to text))
                .retrieve()
                .body(Map::class.java)
            return readVector(response)
        } catch (exception: RecommendationDependencyException) {
            throw exception
        } catch (exception: RestClientException) {
            throw RecommendationDependencyException("Ollama 嵌入服务不可用", exception)
        } catch (exception: ClassCastException) {
            throw RecommendationDependencyException("Ollama 嵌入服务不可用", exception)
        } catch (exception: NullPointerException) {
            throw RecommendationDependencyException("Ollama 嵌入服务不可用", exception)
        }
    }

    private fun readVector(response: Map<*, *>?): FloatArray {
        val embeddings = response?.get("embeddings")
        if (embeddings !is List<*> || embeddings.size != 1
            || embeddings[0] !is List<*>
            || (embeddings[0] as List<*>).size != properties.vectorSize) {
            throw RecommendationDependencyException("Ollama 向量维度异常")
        }
        val values = embeddings[0] as List<*>
        val result = FloatArray(values.size)
        for (i in values.indices) {
            val value = values[i]
            if (value !is Number) {
                throw RecommendationDependencyException("Ollama 向量值异常")
            }
            val numeric = value.toDouble()
            if (!numeric.isFinite()) {
                throw RecommendationDependencyException("Ollama 向量值异常")
            }
            result[i] = numeric.toFloat()
        }
        return result
    }

    private fun requestFactory(timeout: Duration): SimpleClientHttpRequestFactory {
        val factory = SimpleClientHttpRequestFactory()
        factory.setConnectTimeout(timeout)
        factory.setReadTimeout(timeout)
        return factory
    }
}
