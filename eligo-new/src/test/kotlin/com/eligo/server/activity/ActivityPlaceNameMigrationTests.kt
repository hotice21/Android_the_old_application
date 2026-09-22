package com.eligo.server.activity

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ActivityPlaceNameMigrationTests {

    @Test
    fun migrationAddsNullablePlaceNameForHistoricalCompatibility() {
        assertThat(MIGRATION).exists()

        val sql = Files.readString(MIGRATION)
        assertThat(sql)
            .containsIgnoringCase("ALTER TABLE activities")
            .containsIgnoringCase("place_name VARCHAR(100) NULL")
    }

    companion object {
        private val MIGRATION = Path.of(
            "src/main/resources/db/migration/V15__add_activity_place_name.sql"
        )
    }
}
