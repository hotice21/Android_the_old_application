package com.eligo.server.activity.service

import java.time.LocalDateTime

interface ActivityTopicService {

    fun normalize(values: List<String>?): List<String>

    fun normalizeFilter(value: String?): String?

    fun replace(activityId: Long, normalizedTopics: List<String>, now: LocalDateTime)

    fun deleteByActivityId(activityId: Long)

    fun findByActivityId(activityId: Long): List<String>

    fun findByActivityIds(activityIds: List<Long>): Map<Long, List<String>>
}
