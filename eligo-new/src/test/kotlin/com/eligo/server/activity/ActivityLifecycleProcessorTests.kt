package com.eligo.server.activity

import com.eligo.server.activity.entity.ActivityLifecycleEventEntity
import com.eligo.server.activity.mapper.ActivityLifecycleEventMapper
import com.eligo.server.activity.mapper.ActivityMapper
import com.eligo.server.activity.service.ActivityLifecycleProcessor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

class ActivityLifecycleProcessorTests {

    private val activities = mock(ActivityMapper::class.java)
    private val events = mock(ActivityLifecycleEventMapper::class.java)
    private val processor = ActivityLifecycleProcessor(activities, events)

    @Test
    fun endedPublishedActivityIncrementsVersionAndWritesOneSystemEvent() {
        `when`(activities.endPublishedIfDue(101L, NOW)).thenReturn(1)

        assertThat(processor.endIfDue(101L, NOW)).isTrue()

        val event = ArgumentCaptor.forClass(ActivityLifecycleEventEntity::class.java)
        verify(events).insert(event.capture())
        assertThat(event.value.activityId).isEqualTo(101L)
        assertThat(event.value.fromStatus).isEqualTo(2)
        assertThat(event.value.toStatus).isEqualTo(4)
        assertThat(event.value.operatorUserId).isNull()
        assertThat(event.value.createdAt).isEqualTo(NOW)
    }

    @Test
    fun changedOrNotDueActivityDoesNotWriteLifecycleEvent() {
        `when`(activities.endPublishedIfDue(102L, NOW)).thenReturn(0)

        assertThat(processor.endIfDue(102L, NOW)).isFalse()

        verify(events, never()).insert(any(ActivityLifecycleEventEntity::class.java))
    }

    @Test
    fun eachActivityUsesAnIndependentTransaction() {
        val transactional = ActivityLifecycleProcessor::class.java
            .getMethod("endIfDue", Long::class.javaPrimitiveType, LocalDateTime::class.java)
            .getAnnotation(Transactional::class.java)

        assertThat(transactional).isNotNull()
        assertThat(transactional.propagation).isEqualTo(Propagation.REQUIRES_NEW)
    }

    companion object {
        private val NOW = LocalDateTime.parse("2026-08-09T05:00:00")
    }
}
