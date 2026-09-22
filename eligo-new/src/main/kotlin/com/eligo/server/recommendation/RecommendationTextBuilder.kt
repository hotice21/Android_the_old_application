package com.eligo.server.recommendation

import com.eligo.server.profile.entity.InterestTagEntity
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.HexFormat

class RecommendationTextBuilder {

    fun postText(
        title: String?,
        content: String?,
        activityTitle: String?,
        activityCategory: String?
    ): String {
        val lines = mutableListOf<String>()
        addLine(lines, "标题", title, false)
        addLine(lines, "正文", content, true)
        addLine(lines, "关联活动", activityTitle, false)
        addLine(lines, "活动分类", activityCategory, false)
        return lines.joinToString("\n")
    }

    fun userQueryText(tags: List<InterestTagEntity>?): String {
        if (tags == null || tags.isEmpty()) {
            return ""
        }
        val names = tags
            .map { it.tagName }
            .map { trimToNull(it) }
            .filterNotNull()
            .joinToString("、")
        val topics = LinkedHashSet<String>()
        for (tag in tags) {
            val values = TOPICS.getOrDefault(tag.tagCode, emptyList())
            topics.addAll(values)
        }
        val result = StringBuilder()
        if (names.isNotBlank()) {
            result.append("我感兴趣的内容：").append(names)
        }
        if (topics.isNotEmpty()) {
            if (result.isNotEmpty()) {
                result.append('\n')
            }
            result.append("相关主题：").append(topics.joinToString("、"))
        }
        return result.toString()
    }

    fun interestCacheKey(tags: List<InterestTagEntity>, indexIdentity: String): String {
        val source = tags
            .map { it.id }
            .sortedWith(nullsLast(naturalOrder()))
            .map { it.toString() }
            .joinToString(",") +
            "|$indexIdentity"
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(source.toByteArray(StandardCharsets.UTF_8)))
        } catch (exception: NoSuchAlgorithmException) {
            throw IllegalStateException("运行环境缺少 SHA-256", exception)
        }
    }

    fun fingerprint(text: String): String {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(text.toByteArray(StandardCharsets.UTF_8)))
        } catch (exception: NoSuchAlgorithmException) {
            throw IllegalStateException("运行环境缺少 SHA-256", exception)
        }
    }

    private fun addLine(
        lines: MutableList<String>,
        label: String,
        value: String?,
        truncateContent: Boolean
    ) {
        val normalized = trimToNull(value) ?: return
        var text = normalized
        if (truncateContent
            && text.codePointCount(0, text.length) > MAX_CONTENT_CODE_POINTS) {
            val end = text.offsetByCodePoints(0, MAX_CONTENT_CODE_POINTS)
            text = text.substring(0, end)
        }
        lines.add("$label：$text")
    }

    private fun trimToNull(value: String?): String? {
        if (value == null) {
            return null
        }
        val normalized = value.trim()
        return if (normalized.isEmpty()) null else normalized
    }

    companion object {
        private const val MAX_CONTENT_CODE_POINTS = 4_000
        private val TOPICS: Map<String, List<String>> = mapOf(
            "OUTDOOR" to listOf("徒步", "露营"),
            "SPORTS" to listOf("跑步", "骑行"),
            "FOOD" to listOf("美食", "餐厅", "烹饪"),
            "TRAVEL" to listOf("旅行", "出行", "景点"),
            "MUSIC" to listOf("音乐", "演出"),
            "MOVIE" to listOf("电影", "观影"),
            "READING" to listOf("阅读", "读书"),
            "PHOTOGRAPHY" to listOf("摄影", "照片"),
            "GAMING" to listOf("游戏", "电竞"),
            "PETS" to listOf("宠物", "养宠"))
    }
}
