package com.eligo.server.activity

import com.eligo.server.activity.entity.ActivityEntity
import com.eligo.server.activity.mapper.ActivityMapper
import com.eligo.server.activity.service.ActivityEngagementAccessService
import com.eligo.server.activity.service.ActivityEngagementSnapshot
import com.eligo.server.activity.service.ActivityReadService
import com.eligo.server.activity.vo.PublicActivitySummaryView
import com.eligo.server.organization.entity.OrganizationEntity
import com.eligo.server.organization.mapper.OrganizationMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class ActivityEngagementAccessServiceTests {

    private val activities = mock(ActivityMapper::class.java)
    private val organizations = mock(OrganizationMapper::class.java)
    private val reads = mock(ActivityReadService::class.java)
    private val service = ActivityEngagementAccessService(
        activities,
        organizations,
        reads,
        Clock.fixed(NOW, ZoneOffset.UTC)
    )

    @Test
    fun exposesOnlyPublicStatusesAndCalculatesEffectiveEnd() {
        val draft = activity(301L, 1, 202L, null, "2026-08-23T08:00:00")
        `when`(activities.selectById(301L)).thenReturn(draft)
        assertThat(service.findPublicById(301L)).isEmpty()

        val duePublished = activity(302L, 2, 202L, null, "2026-08-22T08:00:00")
        `when`(activities.selectById(302L)).thenReturn(duePublished)

        assertThat(service.findPublicById(302L)).hasValueSatisfying { snapshot ->
            assertThat(snapshot.status).isEqualTo(4)
            assertThat(snapshot.endsAt)
                .isEqualTo(LocalDateTime.parse("2026-08-22T08:00:00"))
        }
    }

    @Test
    fun recognizesPersonalAndCurrentOrganizationOwner() {
        val personal = service.findPublicById(0L)
        assertThat(personal).isEmpty()

        val personalSnapshot = ActivityEngagementSnapshot(
            301L, 2, LocalDateTime.parse("2026-08-23T08:00:00"), 202L, null
        )
        assertThat(service.isOwner(202L, personalSnapshot)).isTrue()

        val organization = OrganizationEntity()
        organization.id = 401L
        `when`(organizations.findActiveOwnedByUserId(202L))
            .thenReturn(listOf(organization))
        val organizationSnapshot = ActivityEngagementSnapshot(
            302L, 2, LocalDateTime.parse("2026-08-23T08:00:00"),
            null, 401L
        )
        assertThat(service.isOwner(202L, organizationSnapshot)).isTrue()
        assertThat(service.isOwner(203L, organizationSnapshot)).isFalse()
    }

    @Test
    fun publicSummariesUseSingleM2BatchReadWithoutLoadingDetailsOneByOne() {
        val first = mock(PublicActivitySummaryView::class.java)
        val second = mock(PublicActivitySummaryView::class.java)
        `when`(reads.findPublicSummaries(listOf(301L, 302L)))
            .thenReturn(mapOf(301L to first, 302L to second))

        assertThat(service.findPublicSummaries(listOf(301L, 302L, 301L)))
            .containsEntry(301L, first)
            .containsEntry(302L, second)
        verify(reads).findPublicSummaries(listOf(301L, 302L))
        verify(reads, never()).getPublicActivity(anyLong())
    }

    private fun activity(
        id: Long,
        status: Int,
        ownerUserId: Long?,
        ownerOrganizationId: Long?,
        endsAt: String
    ): ActivityEntity {
        val entity = ActivityEntity()
        entity.id = id
        entity.status = status
        entity.ownerUserId = ownerUserId
        entity.ownerOrganizationId = ownerOrganizationId
        entity.endsAt = LocalDateTime.parse(endsAt)
        return entity
    }

    companion object {
        private val NOW = Instant.parse("2026-08-22T08:00:00Z")
    }
}
