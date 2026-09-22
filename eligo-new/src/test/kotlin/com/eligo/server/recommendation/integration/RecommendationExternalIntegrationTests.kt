package com.eligo.server.recommendation.integration

import java.util.function.Function

import java.util.function.Consumer

import com.eligo.server.common.api.CursorPage
import com.eligo.server.database.Stage2TestDatabaseCleaner
import com.eligo.server.post.vo.PublicPostView
import com.eligo.server.recommendation.RecommendationProperties
import com.eligo.server.recommendation.client.EmbeddingClient
import com.eligo.server.recommendation.client.VectorStoreClient
import com.eligo.server.recommendation.mapper.RecommendationIndexJobMapper
import com.eligo.server.recommendation.service.RecommendationIndexWorker
import com.eligo.server.recommendation.service.RecommendedFeedService
import com.eligo.server.security.UserPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.RestClient
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

@SpringBootTest(
    properties = [
        "spring.flyway.enabled=true",
        "spring.flyway.ignore-migration-patterns=*:missing",
        "spring.datasource.url=jdbc:mysql://127.0.0.1:13306/eligo_stage2_test?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false",
        "spring.datasource.username=root",
        "spring.datasource.password=",
        "eligo.security.jwt.signing-key-base64=IB8eHRwbGhkYFxYVFBMSERAPDg0MCwoJCAcGBQQDAgE=",
        "eligo.security.sensitive-data.encryption-key-base64=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
        "eligo.security.sensitive-data.lookup-key-base64=Hx4dHBsaGRgXFhUUExIREA8ODQwLCgkIBwYFBAMCAQA=",
        "eligo.recommendation.enabled=true",
        "eligo.recommendation.worker-enabled=true",
        "eligo.recommendation.backfill-enabled=false",
        "eligo.recommendation.worker-initial-delay-ms=3600000",
        "eligo.recommendation.backfill-initial-delay-ms=3600000",
        "eligo.recommendation.collection=eligo_external_it_bge_m3_v1",
        "eligo.recommendation.snapshot-ttl=30s",
        "eligo.recommendation.embedding-timeout=60s",
        "eligo.recommendation.qdrant-timeout=5s"
    ]
)
@ActiveProfiles("local")
@EnabledIfSystemProperty(
    named = "eligo.recommendation.integration.tests",
    matches = "true"
)
@EnabledIfSystemProperty(named = "eligo.database.tests", matches = "true")
class RecommendationExternalIntegrationTests {

    private val VIEWER_ID = 62_001L
    private val AUTHOR_ID = 62_002L
    private val POST_ID = 62_003L

    @Autowired
    private lateinit var embeddings: EmbeddingClient

    @Autowired
    private lateinit var vectors: VectorStoreClient

    @Autowired
    private lateinit var properties: RecommendationProperties

    @Autowired
    private lateinit var redis: StringRedisTemplate

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var jobs: RecommendationIndexJobMapper

    @Autowired
    private lateinit var worker: RecommendationIndexWorker

    @Autowired
    private lateinit var feeds: RecommendedFeedService

    private val pointIds = ArrayList<Long>()
    private var redisKey: String? = null
    private var snapshotKeyPattern: String? = null

    @BeforeEach
    fun cleanDatabase() {
        Stage2TestDatabaseCleaner.cleanAllStage2Tables(jdbcTemplate)
    }

    @AfterEach
    fun cleanupExternalState() {
        if (redisKey != null) {
            redis.delete(redisKey)
        }
        if (snapshotKeyPattern != null) {
            val keys = redis.keys(snapshotKeyPattern)
            if (keys != null && keys.isNotEmpty()) {
                redis.delete(keys)
            }
        }
        for (pointId in pointIds) {
            try {
                vectors.delete(pointId, Long.MAX_VALUE)
            } catch (ignored: RuntimeException) {
            }
        }
        Stage2TestDatabaseCleaner.cleanAllStage2Tables(jdbcTemplate)
    }

    @Test
    fun verifiesFlywayOllamaQdrantAndRedisTogether() {
        assertThat(
            jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history ORDER BY installed_rank",
                String::class.java
            )
        ).contains("12", "13")
        assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name = 'post_recommendation_index_jobs'
                """.trimIndent(),
                Int::class.java
            )
        ).isEqualTo(1)

        val suffix = java.util.UUID.randomUUID().toString()
        val text = "真实推荐依赖验收 $suffix"
        val vector = embeddings.embed(text)
        assertThat(vector).hasSize(properties.vectorSize)
        for (value in vector) {
            assertThat(value.isFinite()).isTrue()
        }

        val pointId = 9_000_000_000_000L +
            Math.floorMod(System.nanoTime(), 1_000_000_000L)
        pointIds.add(pointId)
        val publishedAt = Instant.now()
        vectors.upsert(
            pointId,
            1L,
            vector,
            mapOf(
                "status" to "PUBLISHED",
                "visibility" to "PUBLIC",
                "indexIdentity" to properties.indexIdentity(),
                "publishedAtEpochMs" to publishedAt.toEpochMilli()
            )
        )

        val hits = vectors.search(
            vector,
            publishedAt.minus(Duration.ofMinutes(1)),
            5,
            null
        )
        assertThat(hits)
            .anyMatch { hit -> hit.postId == pointId }

        redisKey = "eligo:recommendation:external-it:$suffix"
        redis.opsForValue().set(redisKey!!, "ok", Duration.ofSeconds(30))
        assertThat(redis.opsForValue().get(redisKey)).isEqualTo("ok")
        assertThat(redis.getExpire(redisKey, TimeUnit.SECONDS)).isPositive()
    }

    @Test
    fun fencesQdrantGenerationsAndKeepsOnlyMysqlCurrentGenerationEligible() {
        vectors.ensureCollection()
        insertCompletedUser(AUTHOR_ID, "分代栅栏作者")
        jdbcTemplate.update(
            """
            INSERT INTO posts (
                id,author_user_id,operator_user_id,status,visibility,title,content,
                published_at,version,created_at,updated_at
            ) VALUES (?,?,?,2,1,'分代栅栏','验证旧任务晚到',UTC_TIMESTAMP(3),0,
                UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))
            """.trimIndent(),
            POST_ID, AUTHOR_ID, AUTHOR_ID
        )
        jobs.enqueue(
            POST_ID,
            RecommendationIndexJobMapper.ACTION_UPSERT,
            LocalDateTime.now(ZoneOffset.UTC)
        )
        jdbcTemplate.update(
            """
            UPDATE post_recommendation_index_jobs
               SET generation=2,task_status=3,indexed_model_version=?
             WHERE post_id=?
            """.trimIndent(),
            properties.indexIdentity(), POST_ID
        )

        val vector = embeddings.embed("真实 Qdrant 分代栅栏")
        val publishedAt = Instant.now()
        val payload = mapOf(
            "status" to "PUBLISHED",
            "visibility" to "PUBLIC",
            "indexIdentity" to properties.indexIdentity(),
            "publishedAtEpochMs" to publishedAt.toEpochMilli()
        )
        pointIds.add(POST_ID)
        vectors.upsert(POST_ID, 1L, vector, payload)
        vectors.upsert(POST_ID, 2L, vector, payload)

        vectors.delete(POST_ID, 1L)
        val afterOldDelete = vectors.search(
            vector, publishedAt.minus(Duration.ofMinutes(1)), 10, null
        )
        assertThat(afterOldDelete)
            .anyMatch { hit -> hit.postId == POST_ID && hit.generation == 2L }
            .noneMatch { hit -> hit.postId == POST_ID && hit.generation == 1L }

        vectors.upsert(POST_ID, 1L, vector, payload)
        val afterLateOldUpsert = vectors.search(
            vector, publishedAt.minus(Duration.ofMinutes(1)), 10, null
        )
        val mysqlCurrentGeneration = jdbcTemplate.queryForObject(
            """
            SELECT generation
              FROM post_recommendation_index_jobs
             WHERE post_id=? AND task_status=3
            """.trimIndent(),
            Long::class.java, POST_ID
        )
        assertThat(afterLateOldUpsert)
            .anyMatch { hit -> hit.postId == POST_ID && hit.generation == 1L }
            .anyMatch { hit -> hit.postId == POST_ID && hit.generation == 2L }
        assertThat(
            afterLateOldUpsert
                .filter { hit -> hit.postId == POST_ID }
                .filter { hit -> hit.generation == mysqlCurrentGeneration }
        )
            .singleElement()
            .satisfies(Consumer {  hit -> assertThat(hit.generation).isEqualTo(2L)  })
    }

    @Test
    fun servesARealVectorRecommendationThroughWorkerMysqlAndRedis() {
        insertCompletedUser(VIEWER_ID, "推荐浏览者")
        insertCompletedUser(AUTHOR_ID, "推荐作者")
        val interestTagId = jdbcTemplate.queryForObject(
            "SELECT id FROM interest_tags WHERE status=1 ORDER BY sort_order,id LIMIT 1",
            Long::class.java
        )
        jdbcTemplate.update(
            """
            INSERT INTO user_interest_tags(id,user_id,interest_tag_id,selected_at)
            VALUES (?,?,?,UTC_TIMESTAMP(3))
            """.trimIndent(),
            62_004L, VIEWER_ID, interestTagId
        )
        jdbcTemplate.update(
            """
            INSERT INTO posts (
                id,author_user_id,operator_user_id,status,visibility,title,content,
                published_at,version,created_at,updated_at
            ) VALUES (?,?,?,2,1,'户外徒步','周末一起走山路',UTC_TIMESTAMP(3),0,
                UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))
            """.trimIndent(),
            POST_ID, AUTHOR_ID, AUTHOR_ID
        )
        jobs.enqueue(
            POST_ID,
            RecommendationIndexJobMapper.ACTION_UPSERT,
            LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1)
        )

        worker.processDueTasks()
        worker.processDueTasks()

        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT task_status FROM post_recommendation_index_jobs WHERE post_id=?",
                Int::class.java, POST_ID
            )
        ).isEqualTo(RecommendationIndexJobMapper.STATUS_SUCCEEDED)
        pointIds.add(POST_ID)

        val page = feeds.list(
            UserPrincipal(VIEWER_ID, "recommendation-external-it"),
            null,
            20
        )

        assertThat(page.items)
            .extracting(Function { it.postId })
            .containsExactly(POST_ID.toString())
        snapshotKeyPattern = "eligo:recommendation:feed:v1:$VIEWER_ID:*"
        assertThat(redis.keys(snapshotKeyPattern)).isNotEmpty()
    }

    @Test
    fun recreatesDeletedCollectionAndRequeuesSucceededPublicPosts() {
        vectors.ensureCollection()
        insertCompletedUser(AUTHOR_ID, "集合恢复作者")
        jdbcTemplate.update(
            """
            INSERT INTO posts (
                id,author_user_id,operator_user_id,status,visibility,title,content,
                published_at,version,created_at,updated_at
            ) VALUES (?,?,?,2,1,'集合恢复','等待重新索引',UTC_TIMESTAMP(3),0,
                UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))
            """.trimIndent(),
            POST_ID, AUTHOR_ID, AUTHOR_ID
        )
        jobs.enqueue(
            POST_ID,
            RecommendationIndexJobMapper.ACTION_UPSERT,
            LocalDateTime.now(ZoneOffset.UTC)
        )
        jdbcTemplate.update(
            """
            UPDATE post_recommendation_index_jobs
               SET task_status=3,indexed_model_version=?
             WHERE post_id=?
            """.trimIndent(),
            properties.indexIdentity(), POST_ID
        )

        RestClient.create(properties.qdrantUrl)
            .delete()
            .uri("/collections/{collection}", properties.collection)
            .retrieve()
            .toBodilessEntity()

        val query = FloatArray(properties.vectorSize)
        query[0] = 1.0f
        assertThat(
            vectors.search(
                query, Instant.now().minus(Duration.ofDays(1)), 5, null
            )
        ).isEmpty()
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT task_status FROM post_recommendation_index_jobs WHERE post_id=?",
                Int::class.java, POST_ID
            )
        ).isEqualTo(RecommendationIndexJobMapper.STATUS_PENDING)
    }

    private fun insertCompletedUser(userId: Long, nickname: String) {
        jdbcTemplate.update(
            """
            INSERT INTO users (id,status,version,created_at,updated_at)
            VALUES (?,1,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))
            """.trimIndent(),
            userId
        )
        jdbcTemplate.update(
            """
            INSERT INTO user_profiles (
                user_id,nickname,completed_at,version,created_at,updated_at
            ) VALUES (?,?,UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))
            """.trimIndent(),
            userId, nickname
        )
    }
}
