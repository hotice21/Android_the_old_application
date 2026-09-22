package com.eligo.server.recommendation

import com.eligo.server.database.Stage2TestDatabaseCleaner
import com.eligo.server.integration.wechat.WechatRestClientFactory
import com.eligo.server.recommendation.client.EmbeddingClient
import com.eligo.server.recommendation.client.VectorStoreClient
import com.eligo.server.recommendation.mapper.RecommendationBackfillCandidate
import com.eligo.server.recommendation.mapper.RecommendationIndexJobMapper
import com.eligo.server.recommendation.service.RecommendationIndexWorker
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

@SpringBootTest(
    properties = [
        "eligo.security.jwt.signing-key-base64=IB8eHRwbGhkYFxYVFBMSERAPDg0MCwoJCAcGBQQDAgE=",
        "eligo.security.sensitive-data.encryption-key-base64=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
        "eligo.security.sensitive-data.lookup-key-base64=Hx4dHBsaGRgXFhUUExIREA8ODQwLCgkIBwYFBAMCAQA=",
        "spring.flyway.ignore-migration-patterns=*:missing",
        "eligo.recommendation.worker-enabled=true",
        "eligo.recommendation.worker-initial-delay-ms=86400000"
    ]
)
@ActiveProfiles("it")
@EnabledIfSystemProperty(named = "eligo.database.tests", matches = "true")
class RecommendationIndexConsistencyMySqlIntegrationTests {

    private val USER_ID = 61_001L
    private val POST_ID = 61_002L

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var jobs: RecommendationIndexJobMapper

    @Autowired
    private lateinit var worker: RecommendationIndexWorker

    @Autowired
    private lateinit var properties: RecommendationProperties

    @MockitoBean
    private lateinit var embeddings: EmbeddingClient

    @MockitoBean
    private lateinit var vectors: VectorStoreClient

    @MockitoBean
    private lateinit var redis: StringRedisTemplate

    @MockitoBean
    private lateinit var wechatRestClientFactory: WechatRestClientFactory

    @BeforeEach
    fun prepareDatabase() {
        Stage2TestDatabaseCleaner.cleanAllStage2Tables(jdbcTemplate)
        jdbcTemplate.update(
            """
            INSERT INTO users (id,status,version,created_at,updated_at)
            VALUES (?,1,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))
            """.trimIndent(),
            USER_ID
        )
        jdbcTemplate.update(
            """
            INSERT INTO user_profiles (
                user_id,nickname,completed_at,version,created_at,updated_at
            ) VALUES (?,'推荐一致性作者',UTC_TIMESTAMP(3),0,
                UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))
            """.trimIndent(),
            USER_ID
        )
        jdbcTemplate.update(
            """
            INSERT INTO posts (
                id,author_user_id,operator_user_id,status,visibility,title,content,
                published_at,version,created_at,updated_at
            ) VALUES (?,?,?,2,1,'推荐一致性','正文',UTC_TIMESTAMP(3),0,
                UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))
            """.trimIndent(),
            POST_ID, USER_ID, USER_ID
        )
        `when`(embeddings.embed(any())).thenReturn(floatArrayOf(0.1f, 0.2f))
    }

    @AfterEach
    fun cleanDatabase() {
        Stage2TestDatabaseCleaner.cleanAllStage2Tables(jdbcTemplate)
    }

    @Test
    fun staleUpsertIsCompensatedUntilLatestDeleteSucceeds() {
        val effect = blockUpsert()
        jobs.enqueue(
            POST_ID,
            RecommendationIndexJobMapper.ACTION_UPSERT,
            now().minusSeconds(1)
        )

        runFirstAttemptAndChangeDesiredState(
            effect,
            RecommendationIndexJobMapper.ACTION_DELETE
        )

        assertTask(
            2L, RecommendationIndexJobMapper.ACTION_DELETE,
            RecommendationIndexJobMapper.STATUS_PENDING
        )
        worker.processDueTasks()

        verify(vectors).delete(POST_ID, 2L)
        assertTask(
            2L, RecommendationIndexJobMapper.ACTION_DELETE,
            RecommendationIndexJobMapper.STATUS_SUCCEEDED
        )
    }

    @Test
    fun staleDeleteIsCompensatedUntilLatestUpsertSucceeds() {
        val effect = blockDelete()
        jobs.enqueue(
            POST_ID,
            RecommendationIndexJobMapper.ACTION_DELETE,
            now().minusSeconds(1)
        )

        runFirstAttemptAndChangeDesiredState(
            effect,
            RecommendationIndexJobMapper.ACTION_UPSERT
        )

        assertTask(
            2L, RecommendationIndexJobMapper.ACTION_UPSERT,
            RecommendationIndexJobMapper.STATUS_PENDING
        )
        worker.processDueTasks()

        verify(vectors).upsert(eq(POST_ID), eq(2L), any(), any())
        assertTask(
            2L, RecommendationIndexJobMapper.ACTION_UPSERT,
            RecommendationIndexJobMapper.STATUS_SUCCEEDED
        )
    }

    @Test
    fun backfillSelectsCooledDownFailuresAndChangedIndexIdentity() {
        jobs.enqueue(
            POST_ID,
            RecommendationIndexJobMapper.ACTION_UPSERT,
            now().minusHours(8)
        )
        jdbcTemplate.update(
            """
            UPDATE post_recommendation_index_jobs
               SET task_status=4,updated_at=?
             WHERE post_id=?
            """.trimIndent(),
            now().minusHours(7), POST_ID
        )

        assertThat(
            jobs.findBackfillCandidates(
                properties.indexIdentity(), now().minusHours(6), 10
            )
        ).containsExactly(
            RecommendationBackfillCandidate(
                POST_ID, RecommendationIndexJobMapper.ACTION_UPSERT
            )
        )

        jdbcTemplate.update(
            """
            UPDATE posts SET status=4,hidden_at=UTC_TIMESTAMP(3) WHERE id=?
            """.trimIndent(),
            POST_ID
        )
        jdbcTemplate.update(
            """
            UPDATE post_recommendation_index_jobs
               SET desired_action=2,task_status=4,updated_at=?
             WHERE post_id=?
            """.trimIndent(),
            now().minusHours(7), POST_ID
        )

        assertThat(
            jobs.findBackfillCandidates(
                properties.indexIdentity(), now().minusHours(6), 10
            )
        ).containsExactly(
            RecommendationBackfillCandidate(
                POST_ID, RecommendationIndexJobMapper.ACTION_DELETE
            )
        )

        jdbcTemplate.update(
            """
            UPDATE posts SET status=2,hidden_at=NULL WHERE id=?
            """.trimIndent(),
            POST_ID
        )
        jdbcTemplate.update(
            """
            UPDATE post_recommendation_index_jobs
               SET desired_action=1,task_status=3,
                   indexed_model_version='old-index',updated_at=?
             WHERE post_id=?
            """.trimIndent(),
            now(), POST_ID
        )

        assertThat(
            jobs.findBackfillCandidates(
                properties.indexIdentity(), now().minusHours(6), 10
            )
        ).containsExactly(
            RecommendationBackfillCandidate(
                POST_ID, RecommendationIndexJobMapper.ACTION_UPSERT
            )
        )
    }

    @Test
    fun collectionRecoveryRequeuesSucceededPublicPosts() {
        jobs.enqueue(
            POST_ID,
            RecommendationIndexJobMapper.ACTION_UPSERT,
            now().minusSeconds(1)
        )
        jdbcTemplate.update(
            """
            UPDATE post_recommendation_index_jobs
               SET task_status=3,indexed_model_version=?
             WHERE post_id=?
            """.trimIndent(),
            properties.indexIdentity(), POST_ID
        )

        assertThat(jobs.requeueCurrentPublicForCollectionRecovery()).isEqualTo(1)

        assertTask(
            2L, RecommendationIndexJobMapper.ACTION_UPSERT,
            RecommendationIndexJobMapper.STATUS_PENDING
        )
    }

    @Test
    fun collectionRecoveryAdvancesProcessingPublicPostUntilWorkerCompensates() {
        jobs.enqueue(
            POST_ID,
            RecommendationIndexJobMapper.ACTION_UPSERT,
            now().minusSeconds(1)
        )
        assertThat(jobs.markProcessing(POST_ID, 1L, now())).isEqualTo(1)

        assertThat(jobs.requeueCurrentPublicForCollectionRecovery()).isEqualTo(1)
        assertTask(
            2L, RecommendationIndexJobMapper.ACTION_UPSERT,
            RecommendationIndexJobMapper.STATUS_PROCESSING
        )
        assertThat(jobs.requeueCurrentDesired(POST_ID, 1L, now())).isEqualTo(1)
        assertTask(
            2L, RecommendationIndexJobMapper.ACTION_UPSERT,
            RecommendationIndexJobMapper.STATUS_PENDING
        )
    }

    @Test
    fun expiredOldWorkerCompletionRequeuesAlreadySucceededLatestGeneration() {
        val effect = blockUpsert()
        jobs.enqueue(
            POST_ID,
            RecommendationIndexJobMapper.ACTION_UPSERT,
            now().minusSeconds(1)
        )
        val executor = Executors.newSingleThreadExecutor()
        try {
            val oldAttempt = executor.submit { worker.processDueTasks() }
            assertThat(effect.started.await(5, TimeUnit.SECONDS)).isTrue()
            jobs.enqueue(POST_ID, RecommendationIndexJobMapper.ACTION_DELETE, now())
            jdbcTemplate.update(
                """
                UPDATE post_recommendation_index_jobs
                   SET processing_started_at=?
                 WHERE post_id=?
                """.trimIndent(),
                now().minusMinutes(10), POST_ID
            )

            worker.processDueTasks()
            assertTask(
                2L, RecommendationIndexJobMapper.ACTION_DELETE,
                RecommendationIndexJobMapper.STATUS_SUCCEEDED
            )

            effect.release.countDown()
            oldAttempt.get(10, TimeUnit.SECONDS)
            assertTask(
                2L, RecommendationIndexJobMapper.ACTION_DELETE,
                RecommendationIndexJobMapper.STATUS_PENDING
            )
            worker.processDueTasks()
            assertTask(
                2L, RecommendationIndexJobMapper.ACTION_DELETE,
                RecommendationIndexJobMapper.STATUS_SUCCEEDED
            )
            verify(vectors, times(2)).delete(POST_ID, 2L)
        } finally {
            effect.release.countDown()
            executor.shutdownNow()
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()
        }
    }

    private fun runFirstAttemptAndChangeDesiredState(
        effect: BlockingEffect,
        nextAction: Int
    ) {
        val executor = Executors.newSingleThreadExecutor()
        try {
            assertThat(properties.workerEnabled).isTrue()
            val firstAttempt = executor.submit { worker.processDueTasks() }
            val started = effect.started.await(5, TimeUnit.SECONDS)
            if (!started) {
                firstAttempt.get(1, TimeUnit.SECONDS)
            }
            assertThat(started).isTrue()
            jobs.enqueue(POST_ID, nextAction, now())
            assertTask(2L, nextAction, RecommendationIndexJobMapper.STATUS_PROCESSING)
            worker.processDueTasks()
            assertTask(2L, nextAction, RecommendationIndexJobMapper.STATUS_PROCESSING)
            effect.release.countDown()
            firstAttempt.get(10, TimeUnit.SECONDS)
        } finally {
            effect.release.countDown()
            executor.shutdownNow()
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()
        }
    }

    private fun blockUpsert(): BlockingEffect {
        val effect = BlockingEffect(
            CountDownLatch(1), CountDownLatch(1)
        )
        doAnswer {
            effect.started.countDown()
            assertThat(effect.release.await(5, TimeUnit.SECONDS)).isTrue()
            null
        }.`when`(vectors).upsert(eq(POST_ID), eq(1L), any(), any())
        return effect
    }

    private fun blockDelete(): BlockingEffect {
        val effect = BlockingEffect(
            CountDownLatch(1), CountDownLatch(1)
        )
        doAnswer {
            effect.started.countDown()
            assertThat(effect.release.await(5, TimeUnit.SECONDS)).isTrue()
            null
        }.`when`(vectors).delete(POST_ID, 1L)
        return effect
    }

    private fun assertTask(generation: Long, action: Int, status: Int) {
        val state = jdbcTemplate.queryForObject(
            """
            SELECT generation,desired_action,task_status
              FROM post_recommendation_index_jobs
             WHERE post_id=?
            """.trimIndent(),
            { resultSet, _ ->
                TaskState(
                    resultSet.getLong("generation"),
                    resultSet.getInt("desired_action"),
                    resultSet.getInt("task_status")
                )
            },
            POST_ID
        )
        assertThat(state).isEqualTo(TaskState(generation, action, status))
    }

    private fun now(): LocalDateTime {
        return LocalDateTime.now(ZoneOffset.UTC)
    }

    private data class BlockingEffect(val started: CountDownLatch, val release: CountDownLatch)

    private data class TaskState(val generation: Long, val action: Int, val status: Int)
}
