package com.eligo.server.favorite

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ActivityFavoriteMigrationTests {

    private val migration = Path.of(
        "src/main/resources/db/migration/V17__create_activity_favorites.sql"
    )

    @Test
    fun migrationCreatesUniqueFavoriteRelationAndStablePageIndex() {
        assertThat(migration).exists()

        val sql = Files.readString(migration)
        assertThat(sql)
            .containsIgnoringCase("CREATE TABLE activity_favorites")
            .containsIgnoringCase(
                "UNIQUE KEY uk_activity_favorite_pair(user_id, activity_id)"
            )
            .containsIgnoringCase(
                "KEY idx_activity_favorite_page(user_id, favorited_at, id)"
            )
            .containsIgnoringCase(
                "KEY idx_activity_favorite_activity(activity_id, id)"
            )
            .containsIgnoringCase(
                "FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE RESTRICT"
            )
            .containsIgnoringCase(
                "FOREIGN KEY(activity_id) REFERENCES activities(id) ON DELETE RESTRICT"
            )
    }
}
