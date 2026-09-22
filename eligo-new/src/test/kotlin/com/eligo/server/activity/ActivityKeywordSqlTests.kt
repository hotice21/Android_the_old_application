package com.eligo.server.activity

import com.eligo.server.activity.mapper.ActivityReadMapper
import org.apache.ibatis.annotations.Select
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class ActivityKeywordSqlTests {

    @Test
    fun publicPageSqlMatchesKeywordAgainstTitleOrDescription() {
        val method = ActivityReadMapper::class.java.getMethod(
            "findPublicPage",
            Int::class.javaPrimitiveType,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            LocalDateTime::class.java,
            java.lang.Long::class.java,
            LocalDateTime::class.java,
            Int::class.javaPrimitiveType
        )
        val sql = method.getAnnotation(Select::class.java).value.joinToString("\n")

        assertThat(sql)
            .contains("#{keyword} IS NULL")
            .contains("LOCATE(#{keyword}, a.title)>0")
            .contains("LOCATE(#{keyword}, COALESCE(a.description, ''))>0")
    }
}
