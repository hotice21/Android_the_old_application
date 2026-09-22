package com.eligo.server.common.api

/**
 * 不可变游标分页结果。
 *
 * 与原始 Java record 的 `List.copyOf` 语义保持一致：传入的列表会被复制为只读 [List]，
 * 调用方保留的可变引用不会被暴露。
 */
class CursorPage<T>(
    items: List<T>,
    val nextCursor: String?,
    val hasMore: Boolean
) {
    val items: List<T> = items.toList()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CursorPage<*>) return false
        return items == other.items &&
            nextCursor == other.nextCursor &&
            hasMore == other.hasMore
    }

    override fun hashCode(): Int {
        var result = items.hashCode()
        result = 31 * result + (nextCursor?.hashCode() ?: 0)
        result = 31 * result + hasMore.hashCode()
        return result
    }

    override fun toString(): String =
        "CursorPage(items=$items, nextCursor=$nextCursor, hasMore=$hasMore)"
}
