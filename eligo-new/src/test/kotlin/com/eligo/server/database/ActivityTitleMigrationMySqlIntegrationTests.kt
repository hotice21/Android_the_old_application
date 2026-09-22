package com.eligo.server.database

import java.util.function.Consumer

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.core.NestedExceptionUtils
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.sql.SQLException

@EnabledIfSystemProperty(named = "eligo.database.tests", matches = "true")
class ActivityTitleMigrationMySqlIntegrationTests {

    private companion object {
        const val DATABASE_URL_PROPERTY = "eligo.activity.migration.url"
        const val DEFAULT_DATABASE_URL = """
            jdbc:mysql://127.0.0.1:13306/eligo_stage2_atomic_test\
            ?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC\
            &allowPublicKeyRetrieval=true&useSSL=false
            """
    }

    private val databaseUrl = System.getProperty(
        DATABASE_URL_PROPERTY, DEFAULT_DATABASE_URL
    )
    private val dataSource = DriverManagerDataSource(
        databaseUrl, "root", ""
    )
    private val jdbcTemplate = JdbcTemplate(dataSource)

    @BeforeEach
    fun migrateToV8() {
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
            .target("8")
            .load()
            .migrate()
    }

    @Test
    fun v9RejectsRawTitleLongerThanTwentyWithoutChangingV8Schema() {
        jdbcTemplate.update(
            """
            INSERT INTO users (id, status, version, created_at, updated_at)
            VALUES (920001, 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """
        )
        jdbcTemplate.update(
            """
            INSERT INTO activities (
                id, owner_user_id, operator_user_id, status, title,
                participant_count, version, created_at, updated_at
            ) VALUES (920001, 920001, 920001, 1, ?, 0, 0,
                UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """, "题".repeat(20) + " "
        )

        assertThatThrownBy {
            Flyway.configure()
                .dataSource(dataSource)
                .target("9")
                .load()
                .migrate()
        }
            .satisfies(Consumer {  throwable ->
                val rootCause = NestedExceptionUtils.getMostSpecificCause(throwable)
                assertThat(rootCause).isInstanceOf(SQLException::class.java)
                val sqlException = rootCause as SQLException
                assertThat(sqlException.errorCode).isEqualTo(3819)
                assertThat(sqlException.sqlState).isEqualTo("HY000")
             })

        assertThat(titleColumnLength()).isEqualTo(100)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT CHAR_LENGTH(title) FROM activities WHERE id=920001",
                Int::class.java
            )
        ).isEqualTo(21)
    }

    @Test
    fun v9UpgradesCleanV8SchemaAndCreatesQueryIndexes() {
        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .target("9")
            .load()

        flyway.migrate()

        assertThat(flyway.info().current().version.toString()).isEqualTo("9")
        assertThat(titleColumnLength()).isEqualTo(20)
        assertThat(
            jdbcTemplate.queryForList(
                """
                SELECT DISTINCT INDEX_NAME
                FROM INFORMATION_SCHEMA.STATISTICS
                WHERE TABLE_SCHEMA=DATABASE()
                  AND TABLE_NAME='activities'
                  AND INDEX_NAME IN (
                      'idx_activity_public_region_start',
                      'idx_activity_owner_user_updated',
                      'idx_activity_owner_organization_updated'
                  )
                """, String::class.java
            )
        ).containsExactlyInAnyOrder(
            "idx_activity_public_region_start",
            "idx_activity_owner_user_updated",
            "idx_activity_owner_organization_updated"
        )
    }

    private fun titleColumnLength(): Int? {
        return jdbcTemplate.queryForObject(
            """
            SELECT CHARACTER_MAXIMUM_LENGTH
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA=DATABASE()
              AND TABLE_NAME='activities'
              AND COLUMN_NAME='title'
            """, Int::class.java
        )
    }
}
