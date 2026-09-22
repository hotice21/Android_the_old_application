package com.eligo.server.database

import java.util.function.Function

import com.eligo.server.activity.entity.ActivityTopicEntity
import com.eligo.server.activity.mapper.ActivityMapRow
import com.eligo.server.activity.mapper.ActivityPublicRow
import com.eligo.server.activity.mapper.ActivityReadMapper
import com.eligo.server.activity.mapper.ActivityTopicMapper
import com.eligo.server.activity.mapper.ActivityTopicRelationMapper
import com.eligo.server.activity.mapper.ActivityTopicRow
import org.apache.ibatis.session.SqlSession
import org.apache.ibatis.session.SqlSessionFactory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.mybatis.spring.SqlSessionFactoryBean
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.math.BigDecimal
import java.sql.Connection
import java.sql.SQLException
import java.time.LocalDateTime
import java.time.Clock
import java.util.List

@EnabledIfSystemProperty(named = "eligo.database.tests", matches = "true")
class ActivityDistanceMySqlIntegrationTests {

    private companion object {
        const val DATABASE_URL = """
            jdbc:mysql://127.0.0.1:13306/eligo_stage2_atomic_test\
            ?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC\
            &allowPublicKeyRetrieval=true&useSSL=false
            """
        val LATITUDE = BigDecimal("22.5430960")
        val LONGITUDE = BigDecimal("114.0578650")
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
            VALUES (9950001, 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """
        )
        jdbcTemplate.update(
            """
            INSERT INTO file_objects (
                id, uploader_type, uploader_id, purpose, storage_provider,
                bucket_name, object_key, original_filename, content_type,
                file_extension, size_bytes, sha256, access_level, scan_status,
                lifecycle_status, version, created_at, updated_at
            ) VALUES (9952001, 1, 9950001, 'ACTIVITY', 'local', 'eligo',
                'activity/distance/cover.jpg', 'cover.jpg', 'image/jpeg',
                'jpg', 1, UNHEX(SHA2('distance-cover', 256)), 1, 2, 2, 0,
                UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """
        )
        insertActivity(9951001L, "中心活动", LATITUDE)
        insertActivity(9951002L, "一公里外活动", BigDecimal("22.5520960"))
        jdbcTemplate.update(
            """
            INSERT INTO activity_topics (id, normalized_name, display_name, created_at)
            VALUES (9953001, 'hiking', 'Hiking', UTC_TIMESTAMP(3)),
                   (9953002, '夜跑', '夜跑', UTC_TIMESTAMP(3))
            """
        )
        jdbcTemplate.update(
            """
            INSERT INTO activity_topic_relations (
                id, activity_id, topic_id, sort_order, created_at)
            VALUES (9954001, 9951001, 9953001, 1, UTC_TIMESTAMP(3)),
                   (9954002, 9951002, 9953002, 1, UTC_TIMESTAMP(3))
            """
        )

        val factoryBean = SqlSessionFactoryBean()
        factoryBean.setDataSource(dataSource)
        sqlSessionFactory = factoryBean.`object`!!
        sqlSessionFactory.configuration.addMapper(ActivityReadMapper::class.java)
        sqlSessionFactory.configuration.addMapper(ActivityTopicMapper::class.java)
        sqlSessionFactory.configuration.addMapper(ActivityTopicRelationMapper::class.java)
    }

    @Test
    fun mapperCalculatesKnownDistanceFiltersRadiusAndPaginates() {
        val now = LocalDateTime.now(Clock.systemUTC())
        sqlSessionFactory.openSession().use { session ->
            val mapper = session.getMapper(ActivityReadMapper::class.java)
            val all = mapper.findPublicPageByDistance(
                2, "HIKING", null, null,
                LATITUDE, LONGITUDE,
                BigDecimal("22.5250000"), BigDecimal("22.5620000"),
                BigDecimal("114.0380000"), BigDecimal("114.0780000"),
                2000,
                null, null, now, 10
            )

            assertThat(all).extracting(Function { it.activityId })
                .containsExactly(9951001L, 9951002L)
            assertThat(all[0].distanceMeters).isZero()
            assertThat(all[1].distanceMeters).isBetween(990L, 1015L)

            assertThat(
                mapper.findPublicPageByDistance(
                    2, "HIKING", null, null,
                    LATITUDE, LONGITUDE, 500,
                    null, null, now, 10
                )
            )
                .extracting(Function { it.activityId })
                .containsExactly(9951001L)

            assertThat(
                mapper.findPublicPageByDistance(
                    2, "HIKING", null, null,
                    LATITUDE, LONGITUDE, 2000,
                    all[0].distanceMeters, all[0].activityId, now, 10
                )
            )
                .extracting(Function { it.activityId })
                .containsExactly(9951002L)
        }
    }

    @Test
    fun mapMapperSupportsViewportDistanceSortAndNearbyRadius() {
        val now = LocalDateTime.now(Clock.systemUTC())
        sqlSessionFactory.openSession().use { session ->
            val mapper = session.getMapper(ActivityReadMapper::class.java)
            val viewport = mapper.findMapItemsByDistance(
                BigDecimal("22.5000000"), BigDecimal("22.6000000"),
                BigDecimal("114.0000000"), BigDecimal("114.1000000"),
                LATITUDE, LONGITUDE, null, "HIKING", now, 10
            )

            assertThat(viewport).extracting(Function { it.activityId })
                .containsExactly(9951001L, 9951002L)
            assertThat(viewport[0].distanceMeters).isZero()
            assertThat(viewport[1].distanceMeters).isBetween(990L, 1015L)

            assertThat(
                mapper.findMapItemsByDistance(
                    BigDecimal("22.5380000"), BigDecimal("22.5480000"),
                    BigDecimal("114.0520000"), BigDecimal("114.0630000"),
                    LATITUDE, LONGITUDE, 500, null, now, 10
                )
            )
                .extracting(Function { it.activityId })
                .containsExactly(9951001L)
        }
    }

    @Test
    fun publicMapperFiltersOrdinaryAndNearbyQueriesByExactNormalizedTopic() {
        val now = LocalDateTime.now(Clock.systemUTC())
        sqlSessionFactory.openSession().use { session ->
            val mapper = session.getMapper(ActivityReadMapper::class.java)

            assertThat(
                mapper.findPublicPage(
                    2, null, null, null, "hiking",
                    null, null, now, 10
                )
            )
                .extracting(Function { it.activityId })
                .containsExactly(9951001L)
            assertThat(
                mapper.findPublicPageByDistance(
                    2, null, null, null, "夜跑",
                    LATITUDE, LONGITUDE, 2000,
                    null, null, now, 10
                )
            )
                .extracting(Function { it.activityId })
                .containsExactly(9951002L)
        }
    }

    @Test
    fun topicRelationMapperBatchLoadsDisplayNamesInActivityOrder() {
        sqlSessionFactory.openSession().use { session ->
            val mapper =
                session.getMapper(ActivityTopicRelationMapper::class.java)

            assertThat(mapper.findByActivityIds(listOf(9951002L, 9951001L)))
                .extracting(
                    ActivityTopicRow::activityId,
                    ActivityTopicRow::displayName,
                    ActivityTopicRow::sortOrder
                )
                .containsExactly(
                    org.assertj.core.groups.Tuple.tuple(9951001L, "Hiking", 1),
                    org.assertj.core.groups.Tuple.tuple(9951002L, "夜跑", 1)
                )
        }
    }

    @Test
    fun publicSummaryMapperBatchLoadsVisibleActivitiesWithoutDetailFields() {
        sqlSessionFactory.openSession().use { session ->
            val mapper = session.getMapper(ActivityReadMapper::class.java)

            val rows = mapper.findPublicSummariesByIds(
                listOf(9951002L, 9999999L, 9951001L)
            )

            assertThat(rows).extracting(Function { it.activityId })
                .containsExactlyInAnyOrder(9951001L, 9951002L)
            assertThat(rows).allSatisfy { row ->
                assertThat(row.description).isNull()
                assertThat(row.organizerPhoneCiphertext).isNull()
                assertThat(row.distanceMeters).isNull()
            }
        }
    }

    @Test
    fun topicLockingReadSeesConcurrentWinnerOutsideRepeatableReadSnapshot() {
        sqlSessionFactory.openSession(false).use { loser ->
            loser.connection.autoCommit = false
            loser.connection.transactionIsolation =
                Connection.TRANSACTION_REPEATABLE_READ
            val mapper = loser.getMapper(ActivityTopicMapper::class.java)
            loser.clearCache()
            assertThat(mapper.findByNormalizedName("并发话题")).isEmpty()

            jdbcTemplate.update(
                """
                INSERT INTO activity_topics (
                    id, normalized_name, display_name, created_at)
                VALUES (9953003, '并发话题', '并发话题', UTC_TIMESTAMP(3))
                """
            )

            assertThatThrownBy {
                loser.connection.prepareStatement(
                    """
                    INSERT INTO activity_topics (
                        id, normalized_name, display_name, created_at)
                    VALUES (9953004, '并发话题', '并发话题', UTC_TIMESTAMP(3))
                    """
                ).use { statement ->
                    statement.executeUpdate()
                }
            }
                .isInstanceOf(SQLException::class.java)
                .extracting(Function {  exception -> (exception as SQLException).errorCode  })
                .isEqualTo(1062)

            assertThat(mapper.findByNormalizedName("并发话题")).isEmpty()
            assertThat(mapper.lockByNormalizedName("并发话题"))
                .get()
                .extracting(Function { it.id })
                .isEqualTo(9953003L)
            loser.connection.rollback()
        }
    }

    private fun insertActivity(id: Long, title: String, latitude: BigDecimal) {
        jdbcTemplate.update(
            """
            INSERT INTO activities (
                id, owner_user_id, operator_user_id, status, title,
                category_code, description, cover_file_id,
                registration_starts_at, registration_ends_at,
                starts_at, ends_at, region_code, address_detail, place_name,
                latitude, longitude, capacity, participant_count,
                published_at, version, created_at, updated_at
            ) VALUES (?, 9950001, 9950001, 2, ?, 'HIKING', '距离测试', 9952001,
                UTC_TIMESTAMP(3) - INTERVAL 1 HOUR,
                UTC_TIMESTAMP(3) + INTERVAL 1 HOUR,
                UTC_TIMESTAMP(3) + INTERVAL 2 HOUR,
                UTC_TIMESTAMP(3) + INTERVAL 3 HOUR,
                '440305', '深圳市南山区', '市民中心东门', ?, ?,
                20, 0, UTC_TIMESTAMP(3), 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """, id, title, latitude, LONGITUDE
        )
    }
}
