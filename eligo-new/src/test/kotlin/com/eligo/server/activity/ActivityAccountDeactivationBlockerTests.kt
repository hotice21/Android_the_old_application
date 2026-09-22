package com.eligo.server.activity

import com.eligo.server.activity.mapper.ActivityMapper
import com.eligo.server.activity.service.ActivityAccountDeactivationBlocker
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.LocalDateTime

class ActivityAccountDeactivationBlockerTests {

    private val activities = mock(ActivityMapper::class.java)
    private val blocker = ActivityAccountDeactivationBlocker(activities)

    @Test
    fun ongoingManagedActivityBlocksAtCallerProvidedTime() {
        `when`(activities.existsOngoingManagedByUserId(USER_ID, NOW)).thenReturn(true)

        assertThat(blocker.blockingReason(USER_ID, NOW))
            .contains("存在进行中的发起活动")
    }

    @Test
    fun noOngoingManagedActivityDoesNotBlock() {
        `when`(activities.existsOngoingManagedByUserId(USER_ID, NOW)).thenReturn(false)

        assertThat(blocker.blockingReason(USER_ID, NOW)).isEmpty()
    }

    companion object {
        private const val USER_ID = 202L
        private val NOW = LocalDateTime.parse("2026-08-16T08:00:00")
    }
}
