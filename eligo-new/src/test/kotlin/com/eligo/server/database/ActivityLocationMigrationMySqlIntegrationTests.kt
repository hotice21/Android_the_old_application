package com.eligo.server.database

import java.util.function.Consumer

import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.core.NestedExceptionUtils
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.math.BigDecimal
import java.sql.SQLException
import java.util.List

@EnabledIfSystemProperty(named = "eligo.database.tests", matches = "true")
class ActivityLocationMigrationMySqlIntegrationTests {

    private companion object {
        const val DATABASE_URL_PROPERTY = "eligo.activity.migration.url"
        const val DEFAULT_DATABASE_URL = """
            jdbc:mysql://127.0.0.1:13306/eligo_stage2_atomic_test\
            ?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC\
            &allowPublicKeyRetrieval=true&useSSL=false
            """
        const val LEGACY_USER_ID = 921_001L
        const val LEGACY_FILE_ID = 921_101L
        const val LEGACY_ACTIVITY_ID = 921_001L
    }

    private val dataSource = DriverManagerDataSource(
        System.getProperty(DATABASE_URL_PROPERTY, DEFAULT_DATABASE_URL), "root", ""
    )
    private val jdbcTemplate = JdbcTemplate(dataSource)

    @BeforeEach
    fun migrateToV10() {
        Stage2TestDatabaseCleaner.cleanAtomicMigrationDatabase(
            jdbcTemplate
        ) {
            Flyway.configure()
                .dataSource(dataSource)
                .cleanDisabled(false)
                .load()
                .clean()
        }
        Flyway.configure()
            .dataSource(dataSource)
            .target("10")
            .load()
            .migrate()
    }

    @Test
    fun v11KeepsLegacyPublishedActivityWithoutInventingCoordinates() {
        insertLegacyPublishedActivityWithoutCoordinates()

        val flyway = migrateToV11()

        assertThat(flyway.info().current().version.toString()).isEqualTo("11")
        assertThat(
            jdbcTemplate.queryForMap(
                "SELECT latitude, longitude FROM activities WHERE id=?",
                LEGACY_ACTIVITY_ID
            )
        )
            .containsEntry("latitude", null)
            .containsEntry("longitude", null)
    }

    @Test
    fun v11RejectsUnpairedAndOutOfRangeCoordinatesButKeepsExactScale() {
        insertLegacyPublishedActivityWithoutCoordinates()
        migrateToV11()

        assertCheckViolation {
            jdbcTemplate.update(
                "UPDATE activities SET latitude=? WHERE id=?",
                BigDecimal("22.5430960"), LEGACY_ACTIVITY_ID
            )
        }
        assertCheckViolation {
            jdbcTemplate.update(
                "UPDATE activities SET latitude=?, longitude=? WHERE id=?",
                BigDecimal("90.0000001"), BigDecimal.ZERO, LEGACY_ACTIVITY_ID
            )
        }
        assertCheckViolation {
            jdbcTemplate.update(
                "UPDATE activities SET latitude=?, longitude=? WHERE id=?",
                BigDecimal.ZERO, BigDecimal("180.0000001"), LEGACY_ACTIVITY_ID
            )
        }

        assertThat(
            jdbcTemplate.update(
                "UPDATE activities SET latitude=?, longitude=? WHERE id=?",
                BigDecimal("22.5430960"),
                BigDecimal("114.0578650"),
                LEGACY_ACTIVITY_ID
            )
        ).isEqualTo(1)
        val latitude = jdbcTemplate.queryForObject(
            "SELECT latitude FROM activities WHERE id=?",
            BigDecimal::class.java,
            LEGACY_ACTIVITY_ID
        )
        val longitude = jdbcTemplate.queryForObject(
            "SELECT longitude FROM activities WHERE id=?",
            BigDecimal::class.java,
            LEGACY_ACTIVITY_ID
        )
        assertThat(latitude).isEqualByComparingTo("22.5430960")
        assertThat(latitude!!.scale()).isEqualTo(7)
        assertThat(longitude).isEqualByComparingTo("114.0578650")
        assertThat(longitude!!.scale()).isEqualTo(7)
    }

    @Test
    fun v11CreatesPublicMapIndexInQueryOrder() {
        migrateToV11()

        val columns = jdbcTemplate.queryForList(
            """
            SELECT COLUMN_NAME
            FROM INFORMATION_SCHEMA.STATISTICS
            WHERE TABLE_SCHEMA=DATABASE()
              AND TABLE_NAME='activities'
              AND INDEX_NAME='idx_activity_public_map'
            ORDER BY SEQ_IN_INDEX
            """, String::class.java
        )

        assertThat(columns).containsExactly(
            "status", "latitude", "longitude", "starts_at", "id"
        )
    }

    private fun migrateToV11(): Flyway {
        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .target("11")
            .load()
        flyway.migrate()
        return flyway
    }

    private fun insertLegacyPublishedActivityWithoutCoordinates() {
        jdbcTemplate.update(
            """
            INSERT INTO users (id, status, version, created_at, updated_at)
            VALUES (?, 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """, LEGACY_USER_ID
        )
        jdbcTemplate.update(
            """
            INSERT INTO file_objects (
                id, uploader_type, uploader_id, purpose, storage_provider,
                bucket_name, object_key, original_filename, content_type,
                file_extension, size_bytes, sha256, access_level, scan_status,
                lifecycle_status, version, created_at, updated_at
            ) VALUES (?, 1, ?, 'ACTIVITY', 'local', 'eligo',
                'activity/location/legacy.jpg', 'legacy.jpg', 'image/jpeg',
                'jpg', 1, UNHEX(SHA2('location-legacy', 256)), 1, 2, 2, 0,
                UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """, LEGACY_FILE_ID, LEGACY_USER_ID
        )
        jdbcTemplate.update(
            """
            INSERT INTO activities (
                id, owner_user_id, operator_user_id, status, title,
                category_code, description, cover_file_id,
                registration_starts_at, registration_ends_at,
                starts_at, ends_at, region_code, address_detail,
                capacity, participant_count, published_at, version,
                created_at, updated_at
            ) VALUES (?, ?, ?, 2, '历史活动',
                'HIKING', '历史活动介绍', ?,
                UTC_TIMESTAMP(3) + INTERVAL 1 HOUR,
                UTC_TIMESTAMP(3) + INTERVAL 2 HOUR,
                UTC_TIMESTAMP(3) + INTERVAL 3 HOUR,
                UTC_TIMESTAMP(3) + INTERVAL 4 HOUR,
                '440305', '深圳市南山区历史地点', 20, 0,
                UTC_TIMESTAMP(3), 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """, LEGACY_ACTIVITY_ID, LEGACY_USER_ID, LEGACY_USER_ID, LEGACY_FILE_ID
        )
    }

    private fun assertCheckViolation(operation: Runnable) {
        org.assertj.core.api.Assertions.assertThatThrownBy { operation.run() }
            .satisfies(Consumer {  throwable ->
                val rootCause = NestedExceptionUtils.getMostSpecificCause(throwable)
                assertThat(rootCause).isInstanceOf(SQLException::class.java)
                val sqlException = rootCause as SQLException
                assertThat(sqlException.errorCode).isEqualTo(3819)
                assertThat(sqlException.sqlState).isEqualTo("HY000")
             })
    }
}
