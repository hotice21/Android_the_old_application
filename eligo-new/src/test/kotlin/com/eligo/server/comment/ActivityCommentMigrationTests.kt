package com.eligo.server.comment

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ActivityCommentMigrationTests {

    private val migration = Path.of(
        "src/main/resources/db/migration/V18__create_activity_comments.sql"
    )

    @Test
    fun migrationCreatesIdempotentSoftDeletedOneLevelCommentStorage() {
        assertThat(migration).exists()
        val sql = Files.readString(migration)
        assertThat(sql)
            .containsIgnoringCase("CREATE TABLE activity_comments")
            .containsIgnoringCase(
                "UNIQUE KEY uk_activity_comment_idempotency(author_user_id, idempotency_key)"
            )
            .containsIgnoringCase(
                "KEY idx_activity_comment_page(activity_id, created_at, id)"
            )
            .containsIgnoringCase(
                "KEY idx_activity_comment_author(author_user_id, status, id)"
            )
            .containsIgnoringCase(
                "FOREIGN KEY(parent_comment_id) REFERENCES activity_comments(id) ON DELETE RESTRICT"
            )
            .containsIgnoringCase("CHECK(status IN (1, 2))")
            .containsIgnoringCase("status=2 AND content IS NULL AND deleted_at IS NOT NULL")
    }
}
