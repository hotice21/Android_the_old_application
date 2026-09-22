package com.eligo.server.activity.service

import com.eligo.server.activity.entity.ActivityTopicEntity
import com.eligo.server.activity.entity.ActivityTopicRelationEntity
import com.eligo.server.activity.mapper.ActivityTopicMapper
import com.eligo.server.activity.mapper.ActivityTopicRelationMapper
import com.eligo.server.activity.mapper.ActivityTopicRow
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import java.time.LocalDateTime
import java.util.Locale
import org.springframework.context.annotation.Profile
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service

@Service
@Profile("!test")
class DefaultActivityTopicService(
    private val topics: ActivityTopicMapper,
    private val relations: ActivityTopicRelationMapper
) : ActivityTopicService {

    override fun normalize(values: List<String>?): List<String> {
        if (values.isNullOrEmpty()) {
            return emptyList()
        }
        if (values.size > MAX_TOPICS) {
            throw validation()
        }
        val normalizedNames = LinkedHashSet<String>()
        val result = ArrayList<String>()
        for (value in values) {
            val displayName = value?.trim()
            if (displayName.isNullOrEmpty()
                || displayName.codePointCount(0, displayName.length) > MAX_TOPIC_LENGTH
            ) {
                throw validation()
            }
            if (normalizedNames.add(normalizedName(displayName))) {
                result.add(displayName)
            }
        }
        return result.toList()
    }

    override fun normalizeFilter(value: String?): String? {
        if (value.isNullOrBlank()) {
            return null
        }
        val displayName = value.trim()
        if (displayName.codePointCount(0, displayName.length) > MAX_TOPIC_LENGTH) {
            throw validation()
        }
        return normalizedName(displayName)
    }

    override fun replace(
        activityId: Long,
        normalizedTopics: List<String>,
        now: LocalDateTime
    ) {
        relations.deleteByActivityId(activityId)
        var sortOrder = 1
        for (displayName in normalizedTopics) {
            val topic = findOrCreate(displayName, now)
            val relation = ActivityTopicRelationEntity().apply {
                this.activityId = activityId
                topicId = topic.id
                this.sortOrder = sortOrder++
                createdAt = now
            }
            relations.insert(relation)
        }
    }

    override fun deleteByActivityId(activityId: Long) {
        relations.deleteByActivityId(activityId)
    }

    override fun findByActivityId(activityId: Long): List<String> =
        relations.findByActivityId(activityId).map { it.displayName!! }

    override fun findByActivityIds(activityIds: List<Long>): Map<Long, List<String>> {
        if (activityIds.isEmpty()) {
            return emptyMap()
        }
        val result = LinkedHashMap<Long, MutableList<String>>()
        activityIds.forEach { id -> result[id] = mutableListOf() }
        for (row in relations.findByActivityIds(activityIds)) {
            result[row.activityId]?.add(row.displayName!!)
        }
        return result.mapValues { it.value.toList() }.toMap()
    }

    private fun findOrCreate(displayName: String, now: LocalDateTime): ActivityTopicEntity {
        val normalizedName = normalizedName(displayName)
        val existing = topics.findByNormalizedName(normalizedName)
        if (existing.isPresent) {
            return existing.get()
        }
        val topic = ActivityTopicEntity().apply {
            this.normalizedName = normalizedName
            this.displayName = displayName
            createdAt = now
        }
        return try {
            topics.insert(topic)
            topic
        } catch (exception: DuplicateKeyException) {
            topics.lockByNormalizedName(normalizedName)
                .orElseThrow { exception }
        }
    }

    private fun normalizedName(displayName: String): String =
        displayName.lowercase(Locale.ROOT)

    private fun validation(): BusinessException =
        BusinessException(CommonErrorCode.VALIDATION_FAILED)

    companion object {
        private const val MAX_TOPICS = 5
        private const val MAX_TOPIC_LENGTH = 20
    }
}
