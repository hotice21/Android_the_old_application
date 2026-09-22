package com.eligo.server.activity

import com.eligo.server.activity.mapper.ActivityReadMapper
import org.apache.ibatis.annotations.Select
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class ActivityTopicSqlTests {

    @Test
    fun publicQueriesFilterByExactNormalizedTopicRelationship() {
        val ordinary = ActivityReadMapper::class.java.getMethod(
            "findPublicPage",
            Int::class.javaPrimitiveType, String::class.java, String::class.java,
            String::class.java, String::class.java,
            LocalDateTime::class.java, java.lang.Long::class.java, LocalDateTime::class.java,
            Int::class.javaPrimitiveType
        )
        val nearby = ActivityReadMapper::class.java.declaredMethods
            .first { it.name == "findPublicPageByDistance" && it.getAnnotation(Select::class.java) != null }

        for (method in listOf(ordinary, nearby)) {
            val sql = method.getAnnotation(Select::class.java).value.joinToString("\n")
            assertThat(sql)
                .contains("#{topic} IS NULL")
                .contains("FROM activity_topic_relations atr")
                .contains("JOIN activity_topics t ON t.id=atr.topic_id")
                .contains("t.normalized_name=#{topic}")
        }
    }
}
