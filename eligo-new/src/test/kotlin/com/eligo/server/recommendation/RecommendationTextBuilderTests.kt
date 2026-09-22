package com.eligo.server.recommendation

import com.eligo.server.profile.entity.InterestTagEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RecommendationTextBuilderTests {

    private val builder = RecommendationTextBuilder()

    @Test
    fun postTextKeepsLabelsOmitsBlankFieldsAndTruncatesByCodePoint() {
        val content = "😀".repeat(4_001)

        val text = builder.postText(
            "  周末徒步 ", content, "深圳山野", "户外活动"
        )

        assertThat(text).startsWith("标题：周末徒步\n正文：😀😀😀")
        assertThat(text).contains("关联活动：深圳山野", "活动分类：户外活动")
        assertThat(text.codePointCount(0, text.length)).isEqualTo(4_031)
        assertThat(builder.postText(null, " ", null, null)).isEmpty()
    }

    @Test
    fun userQueryUsesStableInterestOrderAndFixedTopics() {
        val outdoor = tag("OUTDOOR", "户外")
        val food = tag("FOOD", "美食")

        assertThat(builder.userQueryText(listOf(outdoor, food)))
            .isEqualTo(
                "我感兴趣的内容：户外、美食\n" +
                    "相关主题：徒步、露营、美食、餐厅、烹饪"
            )
        assertThat(builder.interestCacheKey(listOf(outdoor, food), "bge-m3-v1"))
            .isEqualTo(builder.interestCacheKey(listOf(outdoor, food), "bge-m3-v1"))
    }

    private fun tag(code: String, name: String): InterestTagEntity {
        val tag = InterestTagEntity()
        tag.id = code.hashCode().toLong()
        tag.tagCode = code
        tag.tagName = name
        return tag
    }
}
