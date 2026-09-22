package com.eligo.server.recommendation.client

import com.eligo.server.recommendation.RecommendationProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.POST
import org.springframework.http.HttpMethod.PUT
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount.once
import org.springframework.test.web.client.ExpectedCount.times
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

class RestVectorStoreClientTests {

    @Test
    fun readsQdrantV114NestedQueryResponse() {
        val response = mapOf(
            "result" to mapOf(
                "points" to listOf(
                    mapOf("id" to "point-1", "score" to 0.75,
                        "payload" to mapOf(
                            "postId" to 7001L, "generation" to 3L)),
                    mapOf("id" to "point-2", "score" to 0.25,
                        "payload" to mapOf(
                            "postId" to 7002L, "generation" to 4L))
                )
            )
        )

        assertThat(RestVectorStoreClient.readHits(response))
            .containsExactly(
                VectorStoreClient.VectorHit(7001L, 3L, 0.75),
                VectorStoreClient.VectorHit(7002L, 4L, 0.25)
            )
    }

    @Test
    fun searchExcludesTheCurrentUserOnlyAsAPersonalAuthor() {
        val properties = properties()
        val builder = RestClient.builder()
            .baseUrl(properties.qdrantUrl)
        val server = MockRestServiceServer.bindTo(builder).build()
        val client = RestVectorStoreClient(properties, builder.build())
        expectCollectionCreation(server, properties, 8)
        server.expect(
            once(), requestTo(
                "http://qdrant.test/collections/eligo_test/points/query"
            )
        )
            .andExpect(method(POST))
            .andExpect(
                content().json(
                    """
                    {
                      "filter": {
                        "must_not": [{
                          "must": [
                            {"key":"authorType","match":{"value":"USER"}},
                            {"key":"authorId","match":{"value":202}}
                          ]
                        }]
                      }
                    }
                    """.trimIndent(), false
                )
            )
            .andRespond(
                withSuccess(
                    "{\"result\":{\"points\":[]}}",
                    MediaType.APPLICATION_JSON
                )
            )

        assertThat(
            client.search(
                floatArrayOf(0.1f, 0.2f),
                Instant.parse("2026-08-01T00:00:00Z"),
                20,
                202L
            )
        ).isEmpty()

        server.verify()
    }

    @Test
    fun missingCollectionIsRecreatedAndTheSearchIsRetriedOnce() {
        val properties = properties()
        val builder = RestClient.builder()
            .baseUrl(properties.qdrantUrl)
        val server = MockRestServiceServer.bindTo(builder).build()
        val recreations = AtomicInteger()
        val client = RestVectorStoreClient(
            properties, builder.build(), recreations::incrementAndGet
        )
        expectCollectionCreation(server, properties, 8)
        server.expect(
            once(), requestTo(
                "http://qdrant.test/collections/eligo_test/points/query"
            )
        )
            .andRespond(withResourceNotFound())
        expectCollectionCreation(server, properties, 8)
        server.expect(
            once(), requestTo(
                "http://qdrant.test/collections/eligo_test/points/query"
            )
        )
            .andRespond(
                withSuccess(
                    "{\"result\":{\"points\":[]}}",
                    MediaType.APPLICATION_JSON
                )
            )

        assertThat(
            client.search(
                floatArrayOf(0.1f, 0.2f),
                Instant.parse("2026-08-01T00:00:00Z"),
                20,
                null
            )
        ).isEmpty()
        assertThat(recreations).hasValue(2)

        server.verify()
    }

    @Test
    fun collectionDisappearingDuringPayloadIndexCreationIsRetriedOnce() {
        val properties = properties()
        val builder = RestClient.builder()
            .baseUrl(properties.qdrantUrl)
        val server = MockRestServiceServer.bindTo(builder).build()
        val recreations = AtomicInteger()
        val client = RestVectorStoreClient(
            properties, builder.build(), recreations::incrementAndGet
        )
        server.expect(
            once(), requestTo(
                "http://qdrant.test/collections/" + properties.collection
            )
        )
            .andExpect(method(PUT))
            .andRespond(withSuccess())
        server.expect(
            once(), requestTo(
                "http://qdrant.test/collections/" +
                    properties.collection + "/index"
            )
        )
            .andExpect(method(PUT))
            .andRespond(withResourceNotFound())
        expectCollectionCreation(server, properties, 8)
        server.expect(
            once(), requestTo(
                "http://qdrant.test/collections/eligo_test/points/query"
            )
        )
            .andRespond(
                withSuccess(
                    "{\"result\":{\"points\":[]}}",
                    MediaType.APPLICATION_JSON
                )
            )

        assertThat(
            client.search(
                floatArrayOf(0.1f, 0.2f),
                Instant.parse("2026-08-01T00:00:00Z"),
                20,
                null
            )
        ).isEmpty()
        assertThat(recreations).hasValue(1)

        server.verify()
    }

    private fun properties(): RecommendationProperties {
        val properties = RecommendationProperties()
        properties.qdrantUrl = "http://qdrant.test"
        properties.collection = "eligo_test"
        properties.vectorSize = 2
        return properties
    }

    private fun expectCollectionCreation(
        server: MockRestServiceServer,
        properties: RecommendationProperties,
        indexCount: Int
    ) {
        server.expect(
            once(), requestTo(
                "http://qdrant.test/collections/" + properties.collection
            )
        )
            .andExpect(method(PUT))
            .andRespond(withSuccess())
        server.expect(
            times(indexCount), requestTo(
                "http://qdrant.test/collections/" +
                    properties.collection + "/index"
            )
        )
            .andExpect(method(PUT))
            .andRespond(withSuccess())
    }
}
