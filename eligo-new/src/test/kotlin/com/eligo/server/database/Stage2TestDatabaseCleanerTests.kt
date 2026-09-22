package com.eligo.server.database

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.startsWith
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.jdbc.core.JdbcTemplate
import java.util.concurrent.atomic.AtomicBoolean

class Stage2TestDatabaseCleanerTests {

    private var previousDatabaseTestsProperty: String? = null

    @AfterEach
    fun restoreDatabaseTestsProperty() {
        if (previousDatabaseTestsProperty == null) {
            System.clearProperty("eligo.database.tests")
        } else {
            System.setProperty("eligo.database.tests", previousDatabaseTestsProperty)
        }
    }

    @Test
    fun refusesCleanupWithoutExplicitDatabaseTestsSwitch() {
        rememberAndSetDatabaseTestsProperty(null)
        val jdbcTemplate = mock(JdbcTemplate::class.java)

        assertThatThrownBy {
            Stage2TestDatabaseCleaner.cleanStage2Database(
                jdbcTemplate
            ) { jdbcTemplate.update("DELETE FROM users") }
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("eligo.database.tests=true")

        verifyNoInteractions(jdbcTemplate)
    }

    @Test
    fun refusesStage2CleanupOfWrongDatabaseBeforeAnyDelete() {
        rememberAndSetDatabaseTestsProperty("true")
        val jdbcTemplate = mock(JdbcTemplate::class.java)
        `when`(jdbcTemplate.queryForObject("SELECT DATABASE()", String::class.java)).thenReturn("eligo")

        assertThatThrownBy {
            Stage2TestDatabaseCleaner.cleanStage2Database(
                jdbcTemplate
            ) { jdbcTemplate.update("DELETE FROM users") }
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("eligo_stage2_test")
            .hasMessageContaining("eligo")

        verify(jdbcTemplate, never()).update(startsWith("DELETE FROM"))
    }

    @Test
    fun refusesAtomicMigrationCleanupOfWrongActualDatabaseBeforeOperation() {
        rememberAndSetDatabaseTestsProperty("true")
        val jdbcTemplate = mock(JdbcTemplate::class.java)
        val cleanupInvoked = AtomicBoolean()
        `when`(jdbcTemplate.queryForObject("SELECT DATABASE()", String::class.java))
            .thenReturn("eligo_stage2_test")

        assertThatThrownBy {
            Stage2TestDatabaseCleaner.cleanAtomicMigrationDatabase(
                jdbcTemplate
            ) { cleanupInvoked.set(true) }
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("eligo_stage2_atomic_test")
            .hasMessageContaining("eligo_stage2_test")

        assertThat(cleanupInvoked).isFalse()
    }

    @Test
    fun executesCleanupOnlyAfterActualDedicatedDatabaseIsConfirmed() {
        rememberAndSetDatabaseTestsProperty("true")
        val jdbcTemplate = mock(JdbcTemplate::class.java)
        `when`(jdbcTemplate.queryForObject("SELECT DATABASE()", String::class.java))
            .thenReturn("eligo_stage2_test")

        Stage2TestDatabaseCleaner.cleanStage2Database(
            jdbcTemplate
        ) { jdbcTemplate.update("DELETE FROM users") }

        verify(jdbcTemplate).update("DELETE FROM users")
    }

    @Test
    fun deletesOrganizationMembersBeforeOrganizationsAndUsers() {
        rememberAndSetDatabaseTestsProperty("true")
        val jdbcTemplate = mock(JdbcTemplate::class.java)
        `when`(jdbcTemplate.queryForObject("SELECT DATABASE()", String::class.java))
            .thenReturn("eligo_stage2_test")

        Stage2TestDatabaseCleaner.cleanAllStage2Tables(jdbcTemplate)

        val order = inOrder(jdbcTemplate)
        order.verify(jdbcTemplate).update("DELETE FROM organization_members")
        order.verify(jdbcTemplate).update("DELETE FROM organizations")
        order.verify(jdbcTemplate).update("DELETE FROM users")
    }

    @Test
    fun deletesActivityChildrenBeforeActivitiesAndOwners() {
        rememberAndSetDatabaseTestsProperty("true")
        val jdbcTemplate = mock(JdbcTemplate::class.java)
        `when`(jdbcTemplate.queryForObject("SELECT DATABASE()", String::class.java))
            .thenReturn("eligo_stage2_test")

        Stage2TestDatabaseCleaner.cleanAllStage2Tables(jdbcTemplate)

        val order = inOrder(jdbcTemplate)
        order.verify(jdbcTemplate).update("DELETE FROM activity_participations")
        order.verify(jdbcTemplate).update("DELETE FROM activity_media")
        order.verify(jdbcTemplate).update("DELETE FROM activity_lifecycle_events")
        order.verify(jdbcTemplate).update(
            "DELETE FROM activity_create_idempotency_tombstones"
        )
        order.verify(jdbcTemplate).update("DELETE FROM activities")
        order.verify(jdbcTemplate).update("DELETE FROM organizations")
        order.verify(jdbcTemplate).update("DELETE FROM file_objects")
        order.verify(jdbcTemplate).update("DELETE FROM users")
    }

    @Test
    fun preservesFlywayInterestTagSeeds() {
        rememberAndSetDatabaseTestsProperty("true")
        val jdbcTemplate = mock(JdbcTemplate::class.java)
        `when`(jdbcTemplate.queryForObject("SELECT DATABASE()", String::class.java))
            .thenReturn("eligo_stage2_test")

        Stage2TestDatabaseCleaner.cleanAllStage2Tables(jdbcTemplate)

        verify(jdbcTemplate, never()).update("DELETE FROM interest_tags")
    }

    private fun rememberAndSetDatabaseTestsProperty(value: String?) {
        previousDatabaseTestsProperty = System.getProperty("eligo.database.tests")
        if (value == null) {
            System.clearProperty("eligo.database.tests")
        } else {
            System.setProperty("eligo.database.tests", value)
        }
    }
}
