package com.eligo.server.recommendation

import java.util.function.Function

import jakarta.validation.Validation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

class RecommendationPropertiesTests {

    private val validator = Validation.buildDefaultValidatorFactory()
        .validator

    @Test
    fun indexIdentityIsStableAndChangesWithEveryIndexBoundary() {
        val properties = RecommendationProperties()
        val original = properties.indexIdentity()

        assertThat(properties.indexIdentity()).isEqualTo(original).hasSize(64)

        properties.collection = "eligo_post_recommendation_bge_m3_v2"
        val collectionChanged = properties.indexIdentity()
        assertThat(collectionChanged).isNotEqualTo(original)

        properties.vectorSize = 1536
        val dimensionChanged = properties.indexIdentity()
        assertThat(dimensionChanged).isNotEqualTo(collectionChanged)

        properties.modelVersion = "bge-m3-v2"
        val versionChanged = properties.indexIdentity()
        assertThat(versionChanged).isNotEqualTo(dimensionChanged)

        properties.model = "bge-m3:latest"
        assertThat(properties.indexIdentity()).isNotEqualTo(versionChanged)
    }

    @Test
    fun rejectsBlankEndpointsAndOutOfRangeNumericConfiguration() {
        val properties = RecommendationProperties()
        properties.qdrantUrl = " "
        properties.ollamaUrl = ""
        properties.candidateLimit = 0
        properties.lookbackDays = -1
        properties.workerBatchSize = 0
        properties.maxAttempts = 0

        assertThat(validator.validate(properties))
            .extracting(Function {  violation -> violation.propertyPath.toString()  })
            .contains(
                "qdrantUrl",
                "ollamaUrl",
                "candidateLimit",
                "lookbackDays",
                "workerBatchSize",
                "maxAttempts"
            )
    }

    @Test
    fun rejectsNonPositiveDurationsAndBackfillWithoutWorker() {
        val properties = RecommendationProperties()
        properties.snapshotTtl = Duration.ZERO
        properties.embeddingTimeout = Duration.ofSeconds(-1)
        properties.qdrantTimeout = Duration.ZERO
        properties.processingLease = Duration.ZERO
        properties.failedRetryCooldown = Duration.ZERO
        properties.backfillEnabled = true
        properties.workerEnabled = false

        assertThat(validator.validate(properties))
            .extracting(Function {  violation -> violation.propertyPath.toString()  })
            .contains("positiveDurations", "backfillWorkerConfiguration")
    }
}
