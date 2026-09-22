package com.eligo.server.activity

import com.eligo.server.activity.entity.ActivityTopicEntity
import com.eligo.server.activity.entity.ActivityTopicRelationEntity
import com.eligo.server.activity.mapper.ActivityTopicMapper
import com.eligo.server.activity.mapper.ActivityTopicRelationMapper
import com.eligo.server.activity.service.DefaultActivityTopicService
import com.eligo.server.common.error.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import java.util.Optional
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.dao.DuplicateKeyException
import java.time.LocalDateTime

class ActivityTopicServiceTests {

    private val topics = mock(ActivityTopicMapper::class.java)
    private val relations = mock(ActivityTopicRelationMapper::class.java)
    private val service = DefaultActivityTopicService(topics, relations)

    @Test
    fun normalizeTrimsAndDeduplicatesIgnoringCaseWhilePreservingFirstDisplayOrder() {
        assertThat(service.normalize(listOf("  徒步  ", "Hiking", "hiking", "徒步")))
            .containsExactly("徒步", "Hiking")
    }

    @Test
    fun normalizeRejectsBlankOverlongAndMoreThanFiveTopics() {
        assertThatThrownBy { service.normalize(listOf(" ")) }
            .isInstanceOf(BusinessException::class.java)
        assertThatThrownBy { service.normalize(listOf("一".repeat(21))) }
            .isInstanceOf(BusinessException::class.java)
        assertThatThrownBy {
            service.normalize(listOf("一", "二", "三", "四", "五", "六"))
        }
            .isInstanceOf(BusinessException::class.java)
    }

    @Test
    fun replaceReusesGlobalTopicAndPersistsActivityOrder() {
        val now = LocalDateTime.of(2026, 8, 22, 8, 0)
        val existing = ActivityTopicEntity()
        existing.id = 91L
        existing.normalizedName = "hiking"
        existing.displayName = "Hiking"
        `when`(topics.findByNormalizedName("hiking")).thenReturn(Optional.of(existing))
        `when`(topics.findByNormalizedName("徒步")).thenReturn(Optional.empty())
        `when`(topics.insert(any(ActivityTopicEntity::class.java))).thenAnswer { invocation ->
            val inserted = invocation.getArgument<ActivityTopicEntity>(0)
            inserted.id = 92L
            1
        }

        service.replace(7L, listOf("Hiking", "徒步"), now)

        verify(relations).deleteByActivityId(7L)
        val captor = ArgumentCaptor.forClass(ActivityTopicRelationEntity::class.java)
        verify(relations, times(2)).insert(captor.capture())
        assertThat(captor.allValues)
            .extracting(
                ActivityTopicRelationEntity::activityId,
                ActivityTopicRelationEntity::topicId,
                ActivityTopicRelationEntity::sortOrder,
                ActivityTopicRelationEntity::createdAt
            )
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple(7L, 91L, 1, now),
                org.assertj.core.groups.Tuple.tuple(7L, 92L, 2, now)
            )
    }

    @Test
    fun duplicateTopicInsertUsesCurrentReadToLoadConcurrentWinner() {
        val now = LocalDateTime.of(2026, 8, 22, 8, 0)
        val winner = ActivityTopicEntity()
        winner.id = 93L
        winner.normalizedName = "hiking"
        winner.displayName = "Hiking"
        `when`(topics.findByNormalizedName("hiking")).thenReturn(Optional.empty())
        `when`(topics.insert(any(ActivityTopicEntity::class.java)))
            .thenThrow(DuplicateKeyException("并发同名话题"))
        `when`(topics.lockByNormalizedName("hiking"))
            .thenReturn(Optional.of(winner))

        service.replace(8L, listOf("Hiking"), now)

        verify(topics).lockByNormalizedName("hiking")
        val captor = ArgumentCaptor.forClass(ActivityTopicRelationEntity::class.java)
        verify(relations).insert(captor.capture())
        assertThat(captor.value.topicId).isEqualTo(93L)
    }
}
