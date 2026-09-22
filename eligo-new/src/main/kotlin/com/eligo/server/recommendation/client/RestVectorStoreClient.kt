package com.eligo.server.recommendation.client

import com.eligo.server.recommendation.RecommendationDependencyException
import com.eligo.server.recommendation.RecommendationProperties
import com.eligo.server.recommendation.mapper.RecommendationIndexJobMapper
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException

@Component
@Profile("!test")
class RestVectorStoreClient : VectorStoreClient {

    private val properties: RecommendationProperties
    private val client: RestClient
    private val collectionCreatedCallback: Runnable
    @Volatile private var collectionReady: Boolean = false
    @Volatile private var reindexRequired: Boolean = false

    @Autowired
    constructor(
        properties: RecommendationProperties,
        jobs: RecommendationIndexJobMapper
    ) : this(
        properties,
        buildClient(properties),
        Runnable { jobs.requeueCurrentPublicForCollectionRecovery() })

    constructor(
        properties: RecommendationProperties,
        client: RestClient
    ) : this(properties, client, Runnable {})

    constructor(
        properties: RecommendationProperties,
        client: RestClient,
        collectionCreatedCallback: Runnable
    ) {
        this.properties = properties
        this.client = client
        this.collectionCreatedCallback = collectionCreatedCallback
    }

    @Synchronized
    override fun ensureCollection() {
        if (collectionReady) {
            return
        }
        try {
            prepareCollection()
        } catch (exception: RestClientResponseException) {
            if (!isMissingCollection(exception)) {
                throw unavailable("Qdrant 集合不可用", exception)
            }
            try {
                prepareCollection()
            } catch (retryException: RestClientException) {
                throw unavailable("Qdrant 集合不可用", retryException)
            }
        } catch (exception: RestClientException) {
            throw unavailable("Qdrant 集合不可用", exception)
        }
        if (reindexRequired) {
            collectionCreatedCallback.run()
            reindexRequired = false
        }
        collectionReady = true
    }

    private fun prepareCollection() {
        if (createOrValidateCollection()) {
            reindexRequired = true
        }
        ensurePayloadIndexes()
    }

    private fun createOrValidateCollection(): Boolean {
        return try {
            client.put()
                .uri("/collections/{collection}", properties.collection)
                .body(mapOf(
                    "vectors" to mapOf(
                        "size" to properties.vectorSize,
                        "distance" to "Cosine")))
                .retrieve()
                .toBodilessEntity()
            true
        } catch (exception: RestClientException) {
            validateExistingCollection(exception)
        }
    }

    override fun upsert(
        postId: Long,
        generation: Long,
        vector: FloatArray,
        payload: Map<String, Any>
    ) {
        val storedPayload = LinkedHashMap(payload)
        storedPayload["postId"] = postId
        storedPayload["generation"] = generation
        executeWithCollectionRecovery({
            client.put()
                .uri { uriBuilder -> uriBuilder
                    .path("/collections/{collection}/points")
                    .queryParam("wait", true)
                    .build(properties.collection) }
                .body(mapOf(
                    "points" to listOf(mapOf(
                        "id" to pointId(postId, generation),
                        "vector" to toList(vector),
                        "payload" to storedPayload))))
                .retrieve()
                .toBodilessEntity()
            null
        }, "Qdrant 写入失败")
    }

    override fun delete(postId: Long, throughGeneration: Long) {
        executeWithCollectionRecovery({
            client.post()
                .uri { uriBuilder -> uriBuilder
                    .path("/collections/{collection}/points/delete")
                    .queryParam("wait", true)
                    .build(properties.collection) }
                .body(mapOf("filter" to mapOf("must" to listOf(
                    mapOf("key" to "postId", "match" to mapOf("value" to postId)),
                    mapOf("key" to "generation", "range" to
                        mapOf("lte" to throughGeneration))))))
                .retrieve()
                .toBodilessEntity()
            null
        }, "Qdrant 删除失败")
    }

    override fun search(
        vector: FloatArray,
        minimumPublishedAt: Instant,
        limit: Int,
        excludedAuthorUserId: Long?
    ): List<VectorStoreClient.VectorHit> {
        val filter = LinkedHashMap<String, Any>()
        filter["must"] = listOf(
            mapOf("key" to "status", "match" to mapOf("value" to "PUBLISHED")),
            mapOf("key" to "visibility", "match" to mapOf("value" to "PUBLIC")),
            mapOf("key" to "indexIdentity", "match" to
                mapOf("value" to properties.indexIdentity())),
            mapOf("key" to "publishedAtEpochMs", "range" to
                mapOf("gte" to minimumPublishedAt.toEpochMilli())))
        if (excludedAuthorUserId != null) {
            filter["must_not"] = listOf(mapOf(
                "must" to listOf(
                    mapOf("key" to "authorType", "match" to
                        mapOf("value" to "USER")),
                    mapOf("key" to "authorId", "match" to
                        mapOf("value" to excludedAuthorUserId)))))
        }
        return try {
            val response = executeWithCollectionRecovery(
                {
                    client.post()
                        .uri("/collections/{collection}/points/query",
                            properties.collection)
                        .body(mapOf(
                            "query" to toList(vector),
                            "limit" to limit,
                            "with_payload" to listOf("postId", "generation"),
                            "filter" to filter))
                        .retrieve()
                        .body(Map::class.java)
                },
                "Qdrant 查询失败")
            readHits(response)
        } catch (exception: ClassCastException) {
            throw unavailable("Qdrant 查询失败", exception)
        } catch (exception: NullPointerException) {
            throw unavailable("Qdrant 查询失败", exception)
        }
    }

    private fun validateExistingCollection(original: RestClientException): Boolean {
        return try {
            val response = client.get()
                .uri("/collections/{collection}", properties.collection)
                .retrieve()
                .body(Map::class.java)
            val result = map(response, "result")
            val config = map(result, "config")
            val params = map(config, "params")
            val vectors = map(params, "vectors")
            if (properties.vectorSize != vectors["size"]
                || "Cosine" != vectors["distance"]) {
                throw RecommendationDependencyException("Qdrant 集合参数不匹配")
            }
            val pointsCount = result["points_count"]
            pointsCount is Number && pointsCount.toLong() == 0L
        } catch (exception: RecommendationDependencyException) {
            throw exception
        } catch (exception: RestClientException) {
            throw unavailable("Qdrant 集合不可用", original)
        } catch (exception: ClassCastException) {
            throw unavailable("Qdrant 集合不可用", original)
        } catch (exception: NullPointerException) {
            throw unavailable("Qdrant 集合不可用", original)
        }
    }

    private fun ensurePayloadIndexes() {
        ensurePayloadIndex("status", "keyword")
        ensurePayloadIndex("visibility", "keyword")
        ensurePayloadIndex("indexIdentity", "keyword")
        ensurePayloadIndex("publishedAtEpochMs", "integer")
        ensurePayloadIndex("authorType", "keyword")
        ensurePayloadIndex("authorId", "integer")
        ensurePayloadIndex("postId", "integer")
        ensurePayloadIndex("generation", "integer")
    }

    private fun ensurePayloadIndex(fieldName: String, fieldSchema: String) {
        try {
            client.put()
                .uri("/collections/{collection}/index", properties.collection)
                .body(mapOf(
                    "field_name" to fieldName,
                    "field_schema" to fieldSchema))
                .retrieve()
                .toBodilessEntity()
        } catch (exception: RestClientResponseException) {
            if (isMissingCollection(exception)) {
                throw exception
            }
            if (exception.statusCode.value() == 409) {
                return
            }
            throw unavailable("Qdrant 元数据索引不可用", exception)
        } catch (exception: RestClientException) {
            throw unavailable("Qdrant 元数据索引不可用", exception)
        }
    }

    private fun map(source: Map<*, *>?, key: String): Map<*, *> {
        val value = source?.get(key)
        if (value !is Map<*, *>) {
            throw RecommendationDependencyException("Qdrant 返回格式异常")
        }
        return value
    }

    private fun toList(vector: FloatArray): List<Double> {
        val values = ArrayList<Double>(vector.size)
        for (value in vector) {
            if (!value.isFinite()) {
                throw RecommendationDependencyException("向量值异常")
            }
            values.add(value.toDouble())
        }
        return values
    }

    private fun unavailable(message: String, cause: Throwable): RecommendationDependencyException {
        return RecommendationDependencyException(message, cause)
    }

    private fun <T> executeWithCollectionRecovery(
        request: () -> T?,
        errorMessage: String
    ): T? {
        ensureCollection()
        return try {
            request()
        } catch (exception: RestClientResponseException) {
            if (!isMissingCollection(exception)) {
                throw unavailable(errorMessage, exception)
            }
            collectionReady = false
            ensureCollection()
            try {
                request()
            } catch (retryException: RestClientException) {
                throw unavailable(errorMessage, retryException)
            }
        } catch (exception: RestClientException) {
            throw unavailable(errorMessage, exception)
        }
    }

    private fun isMissingCollection(exception: RestClientResponseException): Boolean {
        return exception.statusCode.value() == 404
    }

    private fun pointId(postId: Long, generation: Long): String {
        return UUID.nameUUIDFromBytes(("eligo-recommendation-point-v1\n"
            + postId + "\n" + generation).toByteArray(StandardCharsets.UTF_8))
            .toString()
    }

    companion object {
        internal fun readHits(response: Map<*, *>?): List<VectorStoreClient.VectorHit> {
            var resultValue: Any? = response?.get("result")
            if (resultValue is Map<*, *>) {
                resultValue = resultValue["points"]
            }
            if (resultValue !is List<*>) {
                throw RecommendationDependencyException("Qdrant 返回格式异常")
            }
            val hits = mutableListOf<VectorStoreClient.VectorHit>()
            for (item in resultValue) {
                if (item !is Map<*, *>
                    || item["payload"] !is Map<*, *>
                    || (item["payload"] as Map<*, *>)["postId"] !is Number
                    || (item["payload"] as Map<*, *>)["generation"] !is Number
                    || item["score"] !is Number) {
                    throw RecommendationDependencyException("Qdrant 候选格式异常")
                }
                val payload = item["payload"] as Map<*, *>
                val postId = payload["postId"] as Number
                val generation = payload["generation"] as Number
                val score = item["score"] as Number
                if (generation.toLong() < 1 || !score.toDouble().isFinite()) {
                    throw RecommendationDependencyException("Qdrant 候选格式异常")
                }
                hits.add(VectorStoreClient.VectorHit(
                    postId.toLong(), generation.toLong(), score.toDouble()))
            }
            return hits
        }

        private fun buildClient(properties: RecommendationProperties): RestClient {
            var configured = RestClient.builder()
                .baseUrl(URI.create(properties.qdrantUrl).toString())
                .requestFactory(requestFactory(properties.qdrantTimeout))
            val apiKey = properties.qdrantApiKey
            if (apiKey != null && apiKey.isNotBlank()) {
                configured = configured.defaultHeader(
                    "api-key", apiKey)
            }
            return configured.build()
        }

        private fun requestFactory(timeout: Duration): SimpleClientHttpRequestFactory {
            val factory = SimpleClientHttpRequestFactory()
            factory.setConnectTimeout(timeout)
            factory.setReadTimeout(timeout)
            return factory
        }
    }
}
