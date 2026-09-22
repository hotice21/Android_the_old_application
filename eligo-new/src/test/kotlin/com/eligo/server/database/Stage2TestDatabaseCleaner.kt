package com.eligo.server.database

import java.util.Objects
import org.springframework.jdbc.core.JdbcTemplate

object Stage2TestDatabaseCleaner {

    private const val DATABASE_TESTS_PROPERTY = "eligo.database.tests"
    private const val STAGE2_DATABASE = "eligo_stage2_test"
    private const val ATOMIC_MIGRATION_DATABASE = "eligo_stage2_atomic_test"
    private val STAGE2_TABLES = listOf(
        "post_recommendation_index_jobs", "post_status_events", "post_media", "posts",
        "organization_follows", "user_follows",
        "activity_participations", "activity_media", "activity_lifecycle_events",
        "activity_create_idempotency_tombstones", "organization_members",
        "user_data_request_events", "user_data_requests", "account_security_events",
        "agreement_consents", "user_interest_tags", "user_profile_change_logs",
        "user_login_sessions", "user_devices", "user_phone_bindings",
        "user_wechat_accounts", "activities", "organizations", "user_profiles",
        "file_objects", "agreements", "users"
    )

    @JvmStatic
    fun cleanStage2Database(jdbcTemplate: JdbcTemplate, cleanup: Runnable) {
        runAfterSafetyCheck(jdbcTemplate, STAGE2_DATABASE, cleanup)
    }

    @JvmStatic
    fun cleanAtomicMigrationDatabase(jdbcTemplate: JdbcTemplate, cleanup: Runnable) {
        runAfterSafetyCheck(jdbcTemplate, ATOMIC_MIGRATION_DATABASE, cleanup)
    }

    @JvmStatic
    fun cleanAllStage2Tables(jdbcTemplate: JdbcTemplate) {
        cleanStage2Database(jdbcTemplate) {
            for (table in STAGE2_TABLES) {
                jdbcTemplate.update("DELETE FROM $table")
            }
        }
    }

    private fun runAfterSafetyCheck(
        jdbcTemplate: JdbcTemplate,
        expectedDatabase: String,
        cleanup: Runnable
    ) {
        Objects.requireNonNull(jdbcTemplate, "JdbcTemplate 不能为空")
        Objects.requireNonNull(cleanup, "清理操作不能为空")
        requireExplicitDatabaseTestsSwitch()
        val actualDatabase = jdbcTemplate.queryForObject("SELECT DATABASE()", String::class.java)
        if (expectedDatabase != actualDatabase) {
            throw IllegalStateException(
                "拒绝清理非专用测试数据库，要求 " +
                    expectedDatabase +
                    "，实际为 " +
                    actualDatabase
            )
        }
        cleanup.run()
    }

    private fun requireExplicitDatabaseTestsSwitch() {
        if ("true" != System.getProperty(DATABASE_TESTS_PROPERTY)) {
            throw IllegalStateException("数据库测试必须显式设置 eligo.database.tests=true")
        }
    }
}
