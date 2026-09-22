package com.eligo.server.activity

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ActivityTopicMigrationTests {

    @Test
    fun migrationCreatesReusableTopicsAndOrderedActivityRelations() {
        assertThat(MIGRATION).exists()

        val sql = Files.readString(MIGRATION)
        assertThat(sql)
            .containsIgnoringCase("CREATE TABLE activity_topics")
            .containsIgnoringCase("UNIQUE KEY uk_activity_topic_normalized(normalized_name)")
            .containsIgnoringCase("CREATE TABLE activity_topic_relations")
            .containsIgnoringCase("UNIQUE KEY uk_activity_topic_pair(activity_id, topic_id)")
            .containsIgnoringCase("UNIQUE KEY uk_activity_topic_sort(activity_id, sort_order)")
            .containsIgnoringCase("KEY idx_activity_topic_filter(topic_id, activity_id)")
    }

    companion object {
        private val MIGRATION = Path.of(
            "src/main/resources/db/migration/V16__create_activity_topics.sql"
        )
    }
}
