package com.eligo.server.database

import com.eligo.server.favorite.mapper.ActivityFavoriteMapper
import com.eligo.server.favorite.mapper.ActivityFavoriteRow
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
import java.sql.PreparedStatement
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

@EnabledIfSystemProperty(named = "eligo.database.tests", matches = "true")
class ActivityFavoriteMySqlIntegrationTests {

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
            VALUES (9960001, 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
                   (9960002, 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
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
                9961001, 9960002, 9960002, 1, '收藏测试活动', 'OTHER',
                '收藏测试', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) + INTERVAL 1 HOUR,
                UTC_TIMESTAMP(3) + INTERVAL 2 HOUR,
                UTC_TIMESTAMP(3) + INTERVAL 3 HOUR, '440100', '测试地址',
                20, 0, NULL, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """
        )
        jdbcTemplate.update(
            """
            INSERT INTO activity_favorites (id, user_id, activity_id, favorited_at)
            VALUES (9962001, 9960001, 9961001, '2026-08-22 08:00:00.000'),
                   (9962002, 9960002, 9961001, '2026-08-22 09:00:00.000')
            """
        )
        val factoryBean = SqlSessionFactoryBean()
        factoryBean.setDataSource(dataSource)
        sqlSessionFactory = factoryBean.`object`!!
        sqlSessionFactory.configuration.addMapper(ActivityFavoriteMapper::class.java)
    }

    @Test
    fun mapperPagesByFavoriteTimeAndDeletesOnlyOwnersRelationships() {
        sqlSessionFactory.openSession(true).use { session ->
            val mapper = session.getMapper(ActivityFavoriteMapper::class.java)

            assertThat(mapper.findPage(9960001L, null, null, 10))
                .extracting(
                    ActivityFavoriteRow::favoriteId,
                    ActivityFavoriteRow::activityId,
                    ActivityFavoriteRow::favoritedAt
                )
                .containsExactly(
                    org.assertj.core.groups.Tuple.tuple(
                        9962001L,
                        9961001L,
                        LocalDateTime.parse("2026-08-22T08:00:00")
                    )
                )
            assertThat(mapper.deleteByUserId(9960001L)).isEqualTo(1)
            assertThat(
                jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM activity_favorites WHERE user_id=9960001",
                    Int::class.java
                )
            ).isZero()
            assertThat(
                jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM activity_favorites WHERE user_id=9960002",
                    Int::class.java
                )
            ).isEqualTo(1)
        }
    }

    @Test
    fun databaseRejectsDuplicateUserActivityPair() {
        assertThatThrownBy {
            jdbcTemplate.update(
                """
                INSERT INTO activity_favorites (id, user_id, activity_id, favorited_at)
                VALUES (9962003, 9960001, 9961001, UTC_TIMESTAMP(3))
                """
            )
        }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun userRowLockSerializesFavoriteInsertBeforeDeactivationCleanup() {
        jdbcTemplate.update(
            "DELETE FROM activity_favorites WHERE user_id=9960001"
        )
        sqlSessionFactory.openSession(false).use { creator ->
            sqlSessionFactory.openSession(false).use { deactivation ->
                creator.connection.autoCommit = false
                deactivation.connection.autoCommit = false
                assertThat(creator.connection.autoCommit).isFalse()
                assertThat(deactivation.connection.autoCommit).isFalse()
                creator.connection
                    .prepareStatement("SELECT id FROM users WHERE id=9960001 FOR UPDATE")
                    .use { statement ->
                        statement.executeQuery().use { result ->
                            assertThat(result.next()).isTrue()
                        }
                    }

                val executor: ExecutorService = Executors.newSingleThreadExecutor()
                try {
                    val started = CountDownLatch(1)
                    val cleanup: Future<Int> = executor.submit<Int> {
                        started.countDown()
                        deactivation.connection
                            .prepareStatement(
                                """
                                SELECT id FROM users
                                 WHERE id=9960001 FOR UPDATE
                                """
                            )
                            .use { statement ->
                                statement.executeQuery().use { result ->
                                    if (!result.next()) {
                                        throw IllegalStateException("注销用户不存在")
                                    }
                                }
                            }
                        val deleted = deactivation.getMapper(ActivityFavoriteMapper::class.java)
                            .deleteByUserId(9960001L)
                        deactivation.connection
                            .prepareStatement(
                                """
                                UPDATE users
                                   SET status=3,version=version+1,
                                       deactivated_at=UTC_TIMESTAMP(3),
                                       updated_at=UTC_TIMESTAMP(3)
                                 WHERE id=9960001
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
                            INSERT INTO activity_favorites (
                                id,user_id,activity_id,favorited_at)
                            VALUES (9962003,9960001,9961001,UTC_TIMESTAMP(3))
                            """
                        )
                        .use { statement ->
                            statement.executeUpdate()
                        }
                    creator.connection.commit()

                    assertThat(cleanup[5, TimeUnit.SECONDS]).isEqualTo(1)
                } finally {
                    executor.shutdownNow()
                }
            }
        }
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM users WHERE id=9960001",
                Int::class.java
            )
        ).isEqualTo(3)
        assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM activity_favorites WHERE user_id=9960001
                """, Int::class.java
            )
        ).isZero()
    }
}
