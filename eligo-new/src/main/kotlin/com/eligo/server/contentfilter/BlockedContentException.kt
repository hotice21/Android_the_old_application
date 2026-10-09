package com.eligo.server.contentfilter

/**
 * 被拦截时抛出的异常。[hits] 包含命中的敏感词列表。
 */
class BlockedContentException(
    val hits: List<String>
) : RuntimeException("内容包含敏感词")
