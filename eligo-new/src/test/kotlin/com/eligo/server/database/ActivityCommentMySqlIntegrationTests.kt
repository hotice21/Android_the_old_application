package com.eligo.server.database

import java.util.function.Function

import com.eligo.server.comment.entity.ActivityCommentEntity
import com.eligo.server.comment.mapper.ActivityCommentMapper
import com.eligo.server.comment.mapper.ActivityCommentRow
import org.apache.ibatis.session.SqlSession
import org.apache.ibatis.session.SqlSessionFactory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.mybatis.spring.SqlSessionFactoryBean
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.sql.Connection
import java.sql.SQLException
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

@EnabledIfSystemProperty(named = "eligo.database.tests", matches = "true")
class ActivityCommentMySqlIntegrationTests {

    private companion object {
        const val DATABASE_URL = """
            jdbc:mysql://127.0.0.1:13306/eligo_stage2_atomic_test\
            ?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC\
            &allowPublicKeyRetrieval=true&useSSL=false
            """
    }

    private val dataSource =
        DriverManagerDataSource(DATABASE_URL, "root", "")
    private val jdbcTemplate = JdbcTemplate(dataSource)
    private lateinit var sqlSessionFactory: SqlSessionFactory

    @BeforeEach
    fun prepareDatabase() {
        Stage2TestDatabaseCleaner.cleanAtomicMigrationDatabase(
            jdbcTemplate
        ) {
            Flyway.configure()
                .dataSource(dataSource)
                .cleanDisabled(false)
                .load()
                .clean()
        }
        Flyway.configure().dataSource(dataSource).load().migrate()
        jdbcTemplate.update(
            """
            INSERT INTO users (id, status, version, created_at, updated_at)
            VALUES (9970001, 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
                   (9970002, 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """
        )
        jdbcTemplate.update(
            """
            INSERT INTO activities (
                id, owner_user_id, operator_user_id, status, title,
                category_code, description, registration_starts_at,
                registration_ends_at, starts_at, ends_at, region_code,
                address_detail, capacity, participant_count, published_at,
                version, created_at, updated_at
            ) VALUES (
                9971001, 9970002, 9970002, 1, '评论测试活动', 'OTHER',
                '评论测试', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) + INTERVAL 1 HOUR,
                UTC_TIMESTAMP(3) + INTERVAL 2 HOUR,
                UTC_TIMESTAMP(3) + INTERVAL 3 HOUR, '440100', '测试地址',
                20, 0, NULL, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """
        )
        jdbcTemplate.update(
            """
            INSERT INTO activity_comments (
                id,activity_id,author_user_id,parent_comment_id,status,content,
                idempotency_key,request_fingerprint,created_at,deleted_at)
            VALUES (
                9972001,9971001,9970001,NULL,1,'顶层留言','comment-key-1',
                REPEAT('a',64),'2026-08-22 08:00:00.000',NULL),
                (9972002,9971001,9970002,9972001,1,'领队回复','comment-key-2',
                REPEAT('b',64),'2026-08-22 08:01:00.000',NULL)
            """
        )
        val factoryBean = SqlSessionFactoryBean()
        factoryBean.setDataSource(dataSource)
        sqlSessionFactory = factoryBean.`object`!!
        sqlSessionFactory.configuration.addMapper(ActivityCommentMapper::class.java)
    }

    @Test
    fun mapperPagesAscendingAndPreservesSoftDeletedParentPlaceholder() {
        val deletedAt = LocalDateTime.parse("2026-08-22T09:00:00")
        sqlSessionFactory.openSession(true).use { session ->
            val mapper = session.getMapper(ActivityCommentMapper::class.java)

            assertThat(mapper.findPage(9971001L, null, null, 10))
                .extracting(
                    ActivityCommentRow::commentId,
                    ActivityCommentRow::parentCommentId
                )
                .containsExactly(
                    org.assertj.core.groups.Tuple.tuple(9972001L, null),
                    org.assertj.core.groups.Tuple.tuple(9972002L, 9972001L)
                )

            assertThat(mapper.softDelete(9972001L, deletedAt)).isEqualTo(1)
            val deleted = mapper.findRowById(9972001L)
            assertThat(deleted!!.status)
                .isEqualTo(ActivityCommentEntity.STATUS_DELETED)
            assertThat(deleted!!.content).isNull()
            assertThat(deleted!!.deletedAt).isEqualTo(deletedAt)
            assertThat(
                mapper.findPage(
                    9971001L,
                    LocalDateTime.parse("2026-08-22T08:00:00"),
                    9972001L,
                    10
                )
            )
                .extracting(Function { it.commentId })
                .containsExactly(9972002L)
        }
    }

    @Test
    fun databaseEnforcesAuthorKeyUniquenessAndLifecycleDeletesOnlyAuthorContent() {
        assertThatThrownBy {
            jdbcTemplate.update(
                """
                INSERT INTO activity_comments (
                    id,activity_id,author_user_id,parent_comment_id,status,content,
                    idempotency_key,request_fingerprint,created_at,deleted_at)
                VALUES (9972003,9971001,9970001,NULL,1,'冲突',
                    'comment-key-1',REPEAT('c',64),UTC_TIMESTAMP(3),NULL)
                """
            )
        }
            .isInstanceOf(DataIntegrityViolationException::class.java)

        sqlSessionFactory.openSession(true).use { session ->
            val mapper = session.getMapper(ActivityCommentMapper::class.java)
            val deletedAt = LocalDateTime.parse("2026-08-22T10:00:00")
            assertThat(mapper.softDeleteByAuthor(9970001L, deletedAt)).isEqualTo(1)
        }
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM activity_comments WHERE id=9972001",
                Int::class.java
            )
        ).isEqualTo(2)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM activity_comments WHERE id=9972002",
                Int::class.java
            )
        ).isEqualTo(1)
    }

    @Test
    fun duplicateInsertReplaysWinnerWithCurrentRowOutsideOldSnapshot() {
        val key = "comment-key-concurrent"
        sqlSessionFactory.openSession(false).use { loser ->
            sqlSessionFactory.openSession(false).use { winnerSession ->
                loser.connection.autoCommit = false
                winnerSession.connection.autoCommit = false
                loser.connection.transactionIsolation =
                    Connection.TRANSACTION_REPEATABLE_READ
                winnerSession.connection.transactionIsolation =
                    Connection.TRANSACTION_REPEATABLE_READ
                val mapper = loser.getMapper(ActivityCommentMapper::class.java)
                assertThat(mapper.findByIdempotency(9970001L, key)).isEmpty()

                winnerSession.connection
                    .prepareStatement(
                        """
                        INSERT INTO activity_comments (
                            id,activity_id,author_user_id,parent_comment_id,
                            status,content,idempotency_key,request_fingerprint,
                            created_at,deleted_at)
                        VALUES (9972003,9971001,9970001,NULL,1,'并发赢家',?,
                            REPEAT('c',64),UTC_TIMESTAMP(3),NULL)
                        """
                    )
                    .use { statement ->
                        statement.setString(1, key)
                        statement.executeUpdate()
                    }

                val executor = Executors.newSingleThreadExecutor()
                try {
                    val started = CountDownLatch(1)
                    val duplicate: Future<SQLException> = executor.submit<SQLException> {
                        started.countDown()
                        try {
                            loser.connection
                                .prepareStatement(
                                    """
                                    INSERT INTO activity_comments (
                                        id,activity_id,author_user_id,
                                        parent_comment_id,status,content,
                                        idempotency_key,request_fingerprint,
                                        created_at,deleted_at)
                                    VALUES (9972004,9971001,9970001,NULL,1,
                                        '并发失败方',?,REPEAT('c',64),
                                        UTC_TIMESTAMP(3),NULL)
                                    """
                                )
                                .use { statement ->
                                    statement.setString(1, key)
                                    statement.executeUpdate()
                                }
                            return@submit null as SQLException?
                        } catch (exception: SQLException) {
                            return@submit exception
                        }
                    }

                    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue()
                    Thread.sleep(200)
                    assertThat(duplicate.isDone).isFalse()
                    winnerSession.connection.commit()

                    val failure = duplicate[5, TimeUnit.SECONDS]
                    assertThat(failure as Any).isNotNull()
                    assertThat(failure!!.errorCode).isEqualTo(1062)
                } finally {
                    executor.shutdownNow()
                }

                val winner = mapper.lockByIdempotency(9970001L, key)
                    .orElseThrow()
                assertThat(winner.id).isEqualTo(9972003L)
                assertThat(mapper.findRowById(9972003L)).isNull()
                assertThat(mapper.findRowByIdForUpdate(9972003L)!!)
                    .extracting(Function { it.commentId })
                    .isEqualTo(9972003L)
                loser.connection.rollback()
            }
        }
    }

    @Test
    fun userRowLockSerializesCommentInsertBeforeDeactivationCleanup() {
        sqlSessionFactory.openSession(false).use { creator ->
            sqlSessionFactory.openSession(false).use { deactivation ->
                creator.connection.autoCommit = false
                deactivation.connection.autoCommit = false
                creator.connection
                    .prepareStatement("SELECT id FROM users WHERE id=9970001 FOR UPDATE")
                    .use { statement ->
                        statement.executeQuery().use { result ->
                            assertThat(result.next()).isTrue()
                        }
                    }

                val executor = Executors.newSingleThreadExecutor()
                try {
                    val started = CountDownLatch(1)
                    val cleanup: Future<Int> = executor.submit<Int> {
                        started.countDown()
                        deactivation.connection
                            .prepareStatement(
                                """
                                SELECT id FROM users
                                 WHERE id=9970001 FOR UPDATE
                                """
                            )
                            .use { statement ->
                                statement.executeQuery().use { result ->
                                    if (!result.next()) {
                                        throw IllegalStateException("注销用户不存在")
                                    }
                                }
                            }
                        val deleted = deactivation.getMapper(ActivityCommentMapper::class.java)
                            .softDeleteByAuthor(
                                9970001L,
                                LocalDateTime.parse("2026-08-22T10:00:00")
                            )
                        deactivation.connection
                            .prepareStatement(
                                """
                                UPDATE users
                                   SET status=3,version=version+1,
                                       deactivated_at=UTC_TIMESTAMP(3),
                                       updated_at=UTC_TIMESTAMP(3)
                                 WHERE id=9970001
                                """
                            )
                            .use { statement ->
                                statement.executeUpdate()
                            }
                        deactivation.connection.commit()
                        deleted
                    }

                    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue()
                    Thread.sleep(200)
                    creator.connection
                        .prepareStatement(
                            """
                            INSERT INTO activity_comments (
                                id,activity_id,author_user_id,parent_comment_id,
                                status,content,idempotency_key,request_fingerprint,
                                created_at,deleted_at)
                            VALUES (9972003,9971001,9970001,NULL,1,
                                '注销前评论','comment-lock-cleanup',
                                REPEAT('d',64),UTC_TIMESTAMP(3),NULL)
                            """
                        )
                        .use { statement ->
                            statement.executeUpdate()
                        }
                    creator.connection.commit()

                    assertThat(cleanup[5, TimeUnit.SECONDS]).isEqualTo(2)
                } finally {
                    executor.shutdownNow()
                }
            }
        }
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM users WHERE id=9970001",
                Int::class.java
            )
        ).isEqualTo(3)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM activity_comments WHERE id=9972003",
                Int::class.java
            )
        ).isEqualTo(2)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT content IS NULL FROM activity_comments WHERE id=9972003",
                Boolean::class.java
            )
        ).isTrue()
    }
}
