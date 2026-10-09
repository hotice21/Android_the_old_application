package com.eligo.server.contentfilter.service

import com.eligo.server.contentfilter.BlockedContentException
import com.eligo.server.contentfilter.entity.SensitiveWordEntity
import com.eligo.server.contentfilter.mapper.SensitiveWordMapper
import jakarta.annotation.PostConstruct
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 敏感词过滤结果。
 *
 * @property filtered 处理后的文本（替换或原样）
 * @property hits 命中的敏感词列表
 * @property blocked 是否应该被拦截（severity=2 时 filter 会抛 BlockedContentException）
 * @property marked 是否只是标记
 */
data class FilterResult(
    val filtered: String,
    val hits: List<String>,
    val blocked: Boolean,
    val marked: Boolean
)

data class CompiledRule(
    val pattern: Pattern,
    val severity: Int,
    val replacementText: String?,
    val sourceWord: String
)

/**
 * 单例敏感词过滤服务。
 *
 * 启动时从 DB 加载 [status=1][SensitiveWordEntity.status] 的敏感词正则编译到内存。
 * 支持运行时 [refresh] 重新加载。
 *
 * severity 语义：
 * - 1 替换 → 将匹配文本替换为 [CompiledRule.replacementText]
 * - 2 拦截 → [filter] 直接抛 [BlockedContentException]
 * - 3 标记 → 返回 [FilterResult.marked]=true，但放行
 *
 * 线程安全：所有读写都通过同步块保护。
 */
@Service
class ContentFilterService(
    private val sensitiveWordMapper: SensitiveWordMapper
) {

    private val log = LoggerFactory.getLogger(ContentFilterService::class.java)

    @Volatile
    private var rules: List<CompiledRule> = emptyList()

    @PostConstruct
    fun init() {
        try {
            refresh()
        } catch (t: Throwable) {
            log.error("加载敏感词表失败，contentFilter 将以空规则运行", t)
        }
    }

    @Synchronized
    fun refresh() {
        val rows = sensitiveWordMapper.selectList(null)
            .filter { it.status == 1 && !it.wordPattern.isNullOrBlank() }
        val compiled = rows.mapNotNull { entity ->
            try {
                val flags = Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
                CompiledRule(
                    pattern = Pattern.compile(entity.wordPattern!!, flags),
                    severity = entity.severity ?: 3,
                    replacementText = entity.replacementText,
                    sourceWord = entity.wordPattern!!
                )
            } catch (e: Throwable) {
                log.warn("编译敏感词正则失败 pattern={}: {}", entity.wordPattern, e.message)
                null
            }
        }
        rules = compiled
        log.info("敏感词规则加载完成，共 {} 条", compiled.size)
    }

    fun filter(text: String): FilterResult {
        var filtered = text
        val hits = LinkedHashSet<String>()
        var blocked = false
        var marked = false

        // 先检测 severity=2 的拦截
        for (rule in rules) {
            if (rule.severity != 2) continue
            val matcher = rule.pattern.matcher(filtered)
            if (matcher.find()) {
                hits.add(rule.sourceWord)
                blocked = true
            }
        }
        if (blocked) {
            throw BlockedContentException(hits.toList())
        }

        // severity=1 替换；severity=3 标记但放行
        for (rule in rules) {
            when (rule.severity) {
                1 -> {
                    val matcher = rule.pattern.matcher(filtered)
                    if (matcher.find()) {
                        hits.add(rule.sourceWord)
                        val replacement = rule.replacementText ?: "*"
                        filtered = rule.pattern.matcher(filtered).replaceAll(replacement)
                    }
                }
                3 -> {
                    val matcher = rule.pattern.matcher(filtered)
                    if (matcher.find()) {
                        hits.add(rule.sourceWord)
                        marked = true
                    }
                }
            }
        }
        return FilterResult(filtered, hits.toList(), blocked, marked)
    }

    /**
     * 判断规则是否已加载（单元测试或启动健康检查用）。
     */
    fun hasRules(): Boolean = rules.isNotEmpty()

    /**
     * 当前规则数量。
     */
    fun ruleCount(): Int = rules.size

    companion object {
        // 调用点说明（写注释即可，不用改现有代码）：
        // PostController 发布帖子前调用 contentFilterService.filter(content) + filter(title)
        // ActivityCommentController 发评论前调用 contentFilterService.filter(content)
        // ChatController 发消息前仅 message_type=1 时调用 contentFilterService.filter(content)
    }
}
