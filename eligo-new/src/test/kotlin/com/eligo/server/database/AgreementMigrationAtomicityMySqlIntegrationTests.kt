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

@EnabledIfSystemProperty(named = "eligo.agreement.migration.atomicity.tests", matches = "true")
class AgreementMigrationAtomicityMySqlIntegrationTests {
    private companion object {
        const val DATABASE_URL_PROPERTY = "eligo.agreement.migration.atomicity.url"
        const val DEFAULT_DATABASE_URL = """
            jdbc:mysql://127.0.0.1:13306/eligo_stage2_atomic_test\
            ?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC\
            &allowPublicKeyRetrieval=true&useSSL=false
            """
    }

    private val databaseUrl = System.getProperty(DATABASE_URL_PROPERTY, DEFAULT_DATABASE_URL)
    private val dataSource = DriverManagerDataSource(databaseUrl, "root", "")
    private val jdbcTemplate = JdbcTemplate(dataSource)

    @BeforeEach
    fun migrateOnlyV1AndCreateLegacyRetiredAgreement() {
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
            .target("1")
            .load()
            .migrate()

        jdbcTemplate.update(
            """
            INSERT INTO agreements (
                id, agreement_type, version_code, title, content, content_hash,
                status, requires_reconsent, retired_at, version, created_at, updated_at
            ) VALUES (910001, 1, 'legacy-retired', '历史协议', '历史正文',
                UNHEX(SHA2('legacy-retired', 256)), 4, 0, UTC_TIMESTAMP(3),
                0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """
        )
    }

    @Test
    fun failedV2KeepsTheOriginalAgreementEffectiveConstraint() {
        val flyway = Flyway.configure().dataSource(dataSource).load()

        assertThatThrownBy { flyway.migrate() }
            .hasRootCauseInstanceOf(SQLException::class.java)
            .rootCause()
            .satisfies(Consumer {  rootCause ->
                val sqlException = rootCause as SQLException
                assertThat(sqlException.errorCode).isEqualTo(3819)
                assertThat(sqlException.sqlState).isEqualTo("HY000")
             })

        val constraintCount = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM information_schema.table_constraints
            WHERE constraint_schema = DATABASE()
              AND table_name = 'agreements'
              AND constraint_name = 'chk_agreement_effective'
              AND constraint_type = 'CHECK'
            """, Int::class.java
        )
        assertThat(constraintCount).isEqualTo(1)

        assertThatThrownBy { insertRetiredAgreementWithEffectiveTime(910002L) }
            .satisfies(Consumer {  throwable ->
                val rootCause = NestedExceptionUtils.getMostSpecificCause(throwable)
                assertThat(rootCause).isInstanceOf(SQLException::class.java)
                val sqlException = rootCause as SQLException
                assertThat(sqlException.errorCode).isEqualTo(3819)
                assertThat(sqlException.sqlState).isEqualTo("HY000")
             })
    }

    private fun insertRetiredAgreementWithEffectiveTime(agreementId: Long) {
        jdbcTemplate.update(
            """
            INSERT INTO agreements (
                id, agreement_type, version_code, title, content, content_hash,
                status, requires_reconsent, effective_at, retired_at,
                version, created_at, updated_at
            ) VALUES (?, 2, 'retired-with-effective', '退役协议', '协议正文',
                UNHEX(SHA2('retired-with-effective', 256)), 4, 0,
                UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """, agreementId
        )
    }
}
