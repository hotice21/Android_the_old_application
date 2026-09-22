package com.eligo.server.participation

import java.util.function.Function
import java.util.function.Consumer

import com.eligo.server.activity.service.ActivityParticipationAccessService
import com.eligo.server.activity.service.ActivityParticipationSnapshot
import com.eligo.server.common.api.CursorPage
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.participation.mapper.ActivityParticipantRow
import com.eligo.server.participation.mapper.ActivityParticipationMapper
import com.eligo.server.participation.mapper.MyParticipationRow
import com.eligo.server.participation.service.DefaultParticipationReadService
import com.eligo.server.participation.vo.ActivityParticipantSummaryView
import com.eligo.server.participation.vo.MyParticipationView
import com.eligo.server.security.UserPrincipal
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ParticipationReadServiceTests {

    private val activities = mock<ActivityParticipationAccessService>()
    private val participations = mock<ActivityParticipationMapper>()
    private lateinit var service: DefaultParticipationReadService

    @BeforeEach
    fun setUp() {
        service = DefaultParticipationReadService(
            activities,
            participations,
            Clock.fixed(NOW, ZoneOffset.UTC))
    }

    @Test
    fun listsOnlySafeParticipantFieldsWithStableCursor() {
        whenever(activities.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity(2)))
        whenever(participations.findActiveParticipantPage(
            eq(ACTIVITY_ID), eq(null), eq(null), eq(2)))
            .thenReturn(listOf(
                ActivityParticipantRow(
                    303L, "参与者甲", 501L, LOCAL_NOW.minusHours(2)),
                ActivityParticipantRow(
                    304L, "参与者乙", null, LOCAL_NOW.minusHours(1))))

        val page = service.listParticipants(PRINCIPAL, ACTIVITY_ID, null, 1)

        assertThat(page.items).singleElement().satisfies(Consumer {  item: ActivityParticipantSummaryView ->
            assertThat(item.userId).isEqualTo("303")
            assertThat(item.nickname).isEqualTo("参与者甲")
            assertThat(item.avatar!!.url)
                .isEqualTo("/api/v1/files/501/content")
         })
        assertThat(page.hasMore).isTrue()
        assertThat(page.nextCursor).isNotBlank()
    }

    @Test
    fun listsOwnParticipationHistoryWithStatusAndKeyword() {
        whenever(participations.findMyParticipationPage(
            eq(USER_ID), eq(3), eq("露营"), eq(null), eq(null), eq(21)))
            .thenReturn(listOf(myRow()))

        val page = service.listMyParticipations(
            PRINCIPAL, null, 20, "TERMINATED", " 露营 ")

        assertThat(page.items).singleElement().satisfies(Consumer {  item: MyParticipationView ->
            assertThat(item.userId).isEqualTo("202")
            assertThat(item.status).isEqualTo("TERMINATED")
            assertThat(item.terminationReason).isEqualTo("REMOVED_BY_OWNER")
            assertThat(item.activity!!.activityId).isEqualTo("1001")
            assertThat(item.activity!!.registrationStatus).isEqualTo("OPEN")
            assertThat(item.activity!!.latitude)
                .isEqualByComparingTo("22.5430960")
            assertThat(item.activity!!.longitude)
                .isEqualByComparingTo("114.0578650")
         })
    }

    @Test
    fun hiddenActivityIsClosedInOwnParticipationSummary() {
        whenever(participations.findMyParticipationPage(
            eq(USER_ID), eq(null), eq(null), eq(null), eq(null), eq(21)))
            .thenReturn(listOf(myRow(5)))

        val page = service.listMyParticipations(
            PRINCIPAL, null, 20, null, null)

        assertThat(page.items).singleElement().satisfies(Consumer {  item: MyParticipationView ->
            assertThat(item.activity!!.status).isEqualTo("HIDDEN")
            assertThat(item.activity!!.registrationStatus).isEqualTo("CLOSED")
         })
    }

    @Test
    fun rejectsHiddenParticipantListAndInvalidFilters() {
        whenever(activities.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity(5)))

        assertError({ service.listParticipants(
            PRINCIPAL, ACTIVITY_ID, null, 20) }, CommonErrorCode.RESOURCE_NOT_FOUND)
        assertError({ service.listMyParticipations(
            PRINCIPAL, null, 0, null, null) }, CommonErrorCode.VALIDATION_FAILED)
        assertError({ service.listMyParticipations(
            PRINCIPAL, null, 20, "UNKNOWN", null) }, CommonErrorCode.VALIDATION_FAILED)
        assertError({ service.listMyParticipations(
            PRINCIPAL, null, 20, null, "   ") }, CommonErrorCode.VALIDATION_FAILED)
    }

    @Test
    fun rejectsMalformedOpaqueCursors() {
        whenever(activities.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity(2)))

        assertError({ service.listParticipants(
            PRINCIPAL, ACTIVITY_ID, "not-a-cursor", 20) },
            CommonErrorCode.VALIDATION_FAILED)
        assertError({ service.listMyParticipations(
            PRINCIPAL, "not-a-cursor", 20, null, null) },
            CommonErrorCode.VALIDATION_FAILED)
        assertError({ service.listParticipants(
            PRINCIPAL, ACTIVITY_ID, "   ", 20) },
            CommonErrorCode.VALIDATION_FAILED)
        assertError({ service.listMyParticipations(
            PRINCIPAL, "", 20, null, null) },
            CommonErrorCode.VALIDATION_FAILED)
    }

    @Test
    fun keywordLengthUsesUnicodeCodePoints() {
        val twentyCharacters = "😀".repeat(20)
        whenever(participations.findMyParticipationPage(
            eq(USER_ID), eq(null), eq(twentyCharacters),
            eq(null), eq(null), eq(21)))
            .thenReturn(emptyList())

        assertThat(service.listMyParticipations(
            PRINCIPAL, null, 20, null, twentyCharacters).items).isEmpty()
        assertError({ service.listMyParticipations(
            PRINCIPAL, null, 20, null, "😀".repeat(21)) },
            CommonErrorCode.VALIDATION_FAILED)
    }

    @Test
    fun exposesParticipationStatusWithoutLeakingMapperEntity() {
        val participation = com.eligo.server.participation.entity.ActivityParticipationEntity()
        participation.status = 2
        whenever(participations.findByActivityAndUser(ACTIVITY_ID, USER_ID))
            .thenReturn(Optional.of(participation))

        assertThat(service.findStatus(ACTIVITY_ID, USER_ID)).contains("CANCELLED")
    }

    private fun activity(status: Int): ActivityParticipationSnapshot {
        return ActivityParticipationSnapshot(
            ACTIVITY_ID, status, 303L, null,
            LOCAL_NOW.minusHours(1), LOCAL_NOW.plusHours(1),
            LOCAL_NOW.plusHours(2), LOCAL_NOW.plusHours(3), 20, 0)
    }

    private fun myRow(): MyParticipationRow {
        return myRow(2)
    }

    private fun myRow(activityStatus: Int): MyParticipationRow {
        return MyParticipationRow(
            8001L, USER_ID, 3,
            LOCAL_NOW.minusDays(1), null, LOCAL_NOW.minusHours(2), 2,
            ACTIVITY_ID, activityStatus, "露营活动", "CAMPING", 501L,
            "USER", 303L, "发起者", null,
            LOCAL_NOW.minusHours(1), LOCAL_NOW.plusHours(1),
            LOCAL_NOW.plusHours(2), LOCAL_NOW.plusHours(3),
            "440305", "活动地址",
            BigDecimal("22.5430960"), BigDecimal("114.0578650"),
            20, 5)
    }

    private fun assertError(operation: () -> Unit, expectedError: Any) {
        assertThatThrownBy(operation)
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  exception -> (exception as BusinessException).errorCode  })
            .isEqualTo(expectedError)
    }

    companion object {
        private const val USER_ID = 202L
        private const val ACTIVITY_ID = 1001L
        private val NOW = Instant.parse("2026-08-09T00:00:00Z")
        private val LOCAL_NOW = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)
        private val PRINCIPAL = UserPrincipal(USER_ID, "session-m3")
    }
}
