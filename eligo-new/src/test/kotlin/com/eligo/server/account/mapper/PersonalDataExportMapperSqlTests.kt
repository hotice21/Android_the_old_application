package com.eligo.server.account.mapper

import com.eligo.server.agreement.mapper.AgreementConsentMapper
import com.eligo.server.file.mapper.FileObjectMapper
import com.eligo.server.post.mapper.PostMapper
import java.time.LocalDateTime
import org.apache.ibatis.annotations.Select
import org.junit.jupiter.api.Test
import org.assertj.core.api.Assertions.assertThat

class PersonalDataExportMapperSqlTests {
    @Test
    fun fileExportUsesStablePrimaryKeyOrderAndBoundedQuery() {
        val select = FileObjectMapper::class.java.getMethod("findExportableByUserId",
                Long::class.java, Int::class.java).getAnnotation(Select::class.java)
        val sql = normalize(select.value)

        assertThat(sql).contains("PURPOSE<>'POST'", "ORDER BY ID ASC", "LIMIT #{LIMIT}")
    }

    @Test
    fun postMediaExportUsesPersonalAuthorInsteadOfUploaderOrOperator() {
        val select = FileObjectMapper::class.java.getMethod(
                "findExportablePersonalPostMedia", Long::class.java, Int::class.java)
            .getAnnotation(Select::class.java)
        val sql = normalize(select.value)

        assertThat(sql).contains("JOIN POST_MEDIA", "JOIN POSTS",
                "P.AUTHOR_USER_ID=#{USERID}", "ORDER BY F.ID ASC", "LIMIT #{LIMIT}")
            .doesNotContain("P.OPERATOR_USER_ID=#{USERID}")

        val postSelect = PostMapper::class.java.getMethod(
                "findPersonalExportPage", Long::class.java, java.lang.Long::class.java, Int::class.java)
            .getAnnotation(Select::class.java)
        assertThat(normalize(postSelect.value)).contains(
                "AUTHOR_USER_ID=#{USERID}", "ID>#{AFTERID}",
                "ORDER BY ID", "LIMIT #{LIMIT}")
    }

    @Test
    fun securityExportUsesStableAscendingCursorAndBoundedPage() {
        val select = AccountSecurityEventMapper::class.java.getMethod("findExportPage",
                Long::class.java, LocalDateTime::class.java, java.lang.Long::class.java, Int::class.java)
            .getAnnotation(Select::class.java)
        val sql = normalize(select.value)

        assertThat(sql).contains("OCCURRED_AT>#{CURSORTIME}",
                "OCCURRED_AT=#{CURSORTIME} AND ID>#{CURSORID}",
                "ORDER BY OCCURRED_AT ASC,ID ASC", "LIMIT #{LIMIT}")
    }

    @Test
    fun consentExportUsesStablePrimaryKeyCursorAndBoundedPage() {
        val select = AgreementConsentMapper::class.java.getMethod("findExportPage",
                Long::class.java, java.lang.Long::class.java, Int::class.java).getAnnotation(Select::class.java)
        val sql = normalize(select.value)

        assertThat(sql).contains("ID>#{AFTERID}", "ORDER BY ID ASC", "LIMIT #{LIMIT}")
    }

    private fun normalize(fragments: Array<String>): String {
        return fragments.joinToString(" ").replace(Regex("\\s+"), " ").uppercase()
    }
}
