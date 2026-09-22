package com.eligo.server.activity

import com.eligo.server.activity.entity.ActivityEntity
import com.eligo.server.activity.mapper.ActivityMapper
import com.eligo.server.activity.service.ActivityParticipationAccessService
import com.eligo.server.activity.service.ActivityParticipationSnapshot
import com.eligo.server.organization.entity.OrganizationEntity
import com.eligo.server.organization.mapper.OrganizationMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.lang.reflect.Method
import java.time.LocalDateTime
import java.util.Optional

class ActivityParticipationAccessServiceTests {

    private val activities = mock(ActivityMapper::class.java)
    private val organizations = mock(OrganizationMapper::class.java)
    private val service = ActivityParticipationAccessService(activities, organizations)

    @Test
    fun exposesOnlyParticipationSnapshotFromLockedActivity() {
        val activity = activity()
        `when`(activities.lockById(1001L)).thenReturn(Optional.of(activity))

        assertThat(service.lockById(1001L)).contains(
            ActivityParticipationSnapshot(
                1001L, 2, 202L, null,
                LocalDateTime.parse("2026-08-16T08:00:00"),
                LocalDateTime.parse("2026-08-16T09:00:00"),
                LocalDateTime.parse("2026-08-16T10:00:00"),
                LocalDateTime.parse("2026-08-16T11:00:00"), 20, 3
            )
        )
    }

    @Test
    fun ownershipCheckKeepsOrganizationPersistenceInsideM2() {
        val personal = snapshot(activity())
        assertThat(service.isOwner(202L, personal)).isTrue()
        verifyNoInteractions(organizations)

        val organizationActivity = activity()
        organizationActivity.ownerUserId = null
        organizationActivity.ownerOrganizationId = 401L
        val organization = snapshot(organizationActivity)
        val owned = OrganizationEntity()
        owned.id = 401L
        `when`(organizations.findActiveOwnedByUserId(202L)).thenReturn(listOf(owned))

        assertThat(service.isOwner(202L, organization)).isTrue()
    }

    @Test
    fun commandSidePortMethodsRequireExistingTransaction() {
        assertMandatory("lockById", Long::class.javaPrimitiveType!!)
        assertMandatory("isOwner", Long::class.javaPrimitiveType!!, ActivityParticipationSnapshot::class.java)
        assertMandatory("incrementParticipantCount", Long::class.javaPrimitiveType!!)
        assertMandatory("decrementParticipantCount", Long::class.javaPrimitiveType!!)
    }

    private fun assertMandatory(name: String, vararg parameterTypes: Class<*>) {
        val method: Method = ActivityParticipationAccessService::class.java
            .getMethod(name, *parameterTypes)
        assertThat(method.getAnnotation(Transactional::class.java).propagation)
            .isEqualTo(Propagation.MANDATORY)
    }

    private fun activity(): ActivityEntity {
        val activity = ActivityEntity()
        activity.id = 1001L
        activity.status = 2
        activity.ownerUserId = 202L
        activity.registrationStartsAt = LocalDateTime.parse("2026-08-16T08:00:00")
        activity.registrationEndsAt = LocalDateTime.parse("2026-08-16T09:00:00")
        activity.startsAt = LocalDateTime.parse("2026-08-16T10:00:00")
        activity.endsAt = LocalDateTime.parse("2026-08-16T11:00:00")
        activity.capacity = 20
        activity.participantCount = 3
        return activity
    }

    private fun snapshot(activity: ActivityEntity): ActivityParticipationSnapshot {
        return ActivityParticipationSnapshot(
            activity.id!!, activity.status, activity.ownerUserId,
            activity.ownerOrganizationId, activity.registrationStartsAt,
            activity.registrationEndsAt, activity.startsAt, activity.endsAt,
            activity.capacity, activity.participantCount
        )
    }
}
