package com.eligo.server.activity

import java.util.function.Function
import java.util.function.Consumer

import com.eligo.server.activity.mapper.ActivityManagedDetailRow
import com.eligo.server.activity.mapper.ActivityManagedRow
import com.eligo.server.activity.mapper.ActivityMapRow
import com.eligo.server.activity.mapper.ActivityPublicMediaRow
import com.eligo.server.activity.mapper.ActivityPublicRow
import com.eligo.server.activity.mapper.ActivityReadMapper
import com.eligo.server.activity.service.ActivityTopicService
import com.eligo.server.activity.service.DefaultActivityReadService
import com.eligo.server.activity.vo.ActivityOwnerSummaryView
import com.eligo.server.activity.vo.ActivityMapView
import com.eligo.server.activity.vo.ManagedActivityDetailView
import com.eligo.server.activity.vo.ManagedActivitySummaryView
import com.eligo.server.activity.vo.PublicActivityDetailView
import com.eligo.server.activity.vo.PublicActivitySummaryView
import com.eligo.server.activity.vo.PublicImageView
import com.eligo.server.common.api.CursorPage
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.organization.entity.OrganizationEntity
import com.eligo.server.organization.mapper.OrganizationMapper
import com.eligo.server.participation.service.ParticipationReadService
import com.eligo.server.security.UserPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.Mockito.anyList
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class ActivityReadServiceTests {

    private val mapper = mock(ActivityReadMapper::class.java)
    private val organizations = mock(OrganizationMapper::class.java)
    private val participations = mock(ParticipationReadService::class.java)
    private val topics = mock(ActivityTopicService::class.java)
    private lateinit var service: DefaultActivityReadService

    @BeforeEach
    fun setUp() {
        val organization = OrganizationEntity()
        organization.id = 41L
        `when`(organizations.findActiveOwnedByUserId(21L)).thenReturn(listOf(organization))
        `when`(topics.normalizeFilter(org.mockito.kotlin.anyOrNull<String>()))
            .thenAnswer { invocation -> invocation.getArgument(0) }
        `when`(topics.findByActivityIds(any<List<Long>>()))
            .thenReturn(mapOf())
        `when`(topics.findByActivityId(any<Long>()))
            .thenReturn(listOf())
        service = DefaultActivityReadService(
            mapper = mapper,
            regionCatalog = null,
            organizations = organizations,
            participations = participations,
            topics = topics,
            clock = Clock.fixed(NOW, ZoneOffset.UTC)
        )
    }

    @Test
    fun listMapsPublicFieldsAndDerivesOpenRegistrationStatus() {
        val row = row(2, "USER", 21L, "小明", 12L)
        `when`(mapper.findPublicPage(2, null, null, null, null, null, at(0), 21))
            .thenReturn(listOf(row))

        val result = service.listPublicActivities(null, 20, null, null, null)

        assertThat(result.items).hasSize(1)
        val item = result.items[0]
        assertThat(item.activityId).isEqualTo("1")
        assertThat(item.status).isEqualTo("PUBLISHED")
        assertThat(item.owner.ownerType).isEqualTo("USER")
        assertThat(item.owner.ownerId).isEqualTo("21")
        assertThat(item.owner.displayName).isEqualTo("小明")
        assertThat(item.cover!!.url).isEqualTo("/api/v1/files/11/content")
        assertThat(item.latitude).isEqualByComparingTo(LATITUDE)
        assertThat(item.longitude).isEqualByComparingTo(LONGITUDE)
        assertThat(item.placeName).isEqualTo("市民中心东门")
        assertThat(item.coordinateSystem).isEqualTo("GCJ-02")
        assertThat(item.registrationStatus).isEqualTo("OPEN")
        assertThat(item.feeType).isEqualTo("FREE")
        assertThat(result.hasMore).isFalse()
        assertThat(result.nextCursor).isNull()
    }

    @Test
    fun listNormalizesKeywordAndPassesItToTitleOrDescriptionQuery() {
        val row = row(2, "USER", 21L, "小明", 12L)
        `when`(mapper.findPublicPage(2, null, null, "夜跑", null, null, at(0), 21))
            .thenReturn(listOf(row))

        val result = service.listPublicActivities(null, 20, null, null, null, "  夜跑  ")

        assertThat(result.items).extracting(Function {  it.activityId  }).containsExactly("1")
        verify(mapper).findPublicPage(2, null, null, "夜跑", null, null, at(0), 21)
    }

    @Test
    fun listFiltersByNormalizedTopicAndReturnsBatchTopics() {
        val row = row(2, "USER", 21L, "小明", 12L)
        `when`(topics.normalizeFilter("  Hiking  ")).thenReturn("hiking")
        `when`(mapper.findPublicPage(2, null, null, null, "hiking", null, null, at(0), 21))
            .thenReturn(listOf(row))
        `when`(topics.findByActivityIds(listOf(1L)))
            .thenReturn(mapOf(1L to listOf("Hiking", "夜跑")))

        val result = service.listPublicActivities(
            null, 20, null, null, null, null, "  Hiking  ", null, null, null
        )

        assertThat(result.items[0].topics).containsExactly("Hiking", "夜跑")
        verify(mapper).findPublicPage(2, null, null, null, "hiking", null, null, at(0), 21)
    }

    @Test
    fun publicSummariesBatchLoadRowsAndTopicsOnce() {
        val first = rowWithDistance(11L, 0L)
        val second = rowWithDistance(12L, 0L)
        `when`(mapper.findPublicSummariesByIds(listOf(11L, 12L)))
            .thenReturn(listOf(first, second))
        `when`(topics.findByActivityIds(listOf(11L, 12L)))
            .thenReturn(mapOf(11L to listOf("徒步"), 12L to listOf("露营")))

        val result = service.findPublicSummaries(listOf(11L, 12L, 11L))

        assertThat(result.keys).containsExactlyInAnyOrder(11L, 12L)
        assertThat(result[11L]!!.topics).containsExactly("徒步")
        assertThat(result[12L]!!.topics).containsExactly("露营")
        verify(mapper).findPublicSummariesByIds(listOf(11L, 12L))
        verify(topics).findByActivityIds(listOf(11L, 12L))
    }

    @Test
    fun blankKeywordIsTreatedAsAbsent() {
        `when`(mapper.findPublicPage(2, null, null, null, null, null, at(0), 21))
            .thenReturn(listOf())

        service.listPublicActivities(null, 20, null, null, null, "  \t ")

        verify(mapper).findPublicPage(2, null, null, null, null, null, at(0), 21)
    }

    @Test
    fun keywordLongerThanTwentyUnicodeCodePointsIsRejected() {
        val keyword = "活动".repeat(11)

        assertThatThrownBy {
            service.listPublicActivities(null, 20, null, null, null, keyword)
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(CommonErrorCode.VALIDATION_FAILED)
        verifyNoInteractions(mapper)
    }

    @Test
    fun nearbyListReturnsDatabaseSortedHaversineDistanceAndRadiusFilter() {
        val first = rowWithDistance(11L, 123L)
        val second = rowWithDistance(12L, 456L)
        `when`(
            mapper.findPublicPageByDistance(
                2, null, null, null,
                LATITUDE, LONGITUDE,
                bd("22.4981299"), bd("22.5880621"),
                bd("114.0091788"), bd("114.1065512"),
                5000, null, null, at(0), 3
            )
        ).thenReturn(listOf(first, second))

        val result = service.listPublicActivities(
            null, 2, null, null, null, null, LATITUDE, LONGITUDE, 5000
        )

        assertThat(result.items).extracting(Function {  it.activityId  }).containsExactly("11", "12")
        assertThat(result.items).extracting(Function {  it.distanceMeters  }).containsExactly(123L, 456L)
        verify(mapper).findPublicPageByDistance(
            2, null, null, null,
            LATITUDE, LONGITUDE,
            bd("22.4981299"), bd("22.5880621"),
            bd("114.0091788"), bd("114.1065512"),
            5000, null, null, at(0), 3
        )
    }

    @Test
    fun nearbyListRejectsUnpairedCoordinatesAndInvalidRadius() {
        val invalidCalls = listOf(
            { service.listPublicActivities(null, 20, null, null, null, null, LATITUDE, null, null); Unit },
            { service.listPublicActivities(null, 20, null, null, null, null, null, null, 1000); Unit },
            { service.listPublicActivities(null, 20, null, null, null, null, LATITUDE, LONGITUDE, 0); Unit },
            { service.listPublicActivities(null, 20, null, null, null, null, LATITUDE, LONGITUDE, 50001); Unit }
        )

        assertThat(invalidCalls).allSatisfy { call ->
            assertThatThrownBy(call)
                .isInstanceOf(BusinessException::class.java)
                .extracting(Function {  (it as BusinessException).errorCode  })
                .isEqualTo(CommonErrorCode.VALIDATION_FAILED)
        }
        verifyNoInteractions(mapper)
    }

    @Test
    fun nearbyCursorIsBoundToCoordinatesRadiusAndFilters() {
        `when`(
            mapper.findPublicPageByDistance(
                2, null, null, "夜跑",
                LATITUDE, LONGITUDE,
                bd("22.4981299"), bd("22.5880621"),
                bd("114.0091788"), bd("114.1065512"),
                5000, null, null, at(0), 2
            )
        ).thenReturn(listOf(rowWithDistance(11L, 123L), rowWithDistance(12L, 456L)))
        val cursor = service.listPublicActivities(
            null, 1, null, null, null, "夜跑", LATITUDE, LONGITUDE, 5000
        ).nextCursor

        assertThat(cursor).isNotBlank()
        assertThatThrownBy {
            service.listPublicActivities(cursor, 1, null, null, null, "晨跑", LATITUDE, LONGITUDE, 5000)
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(CommonErrorCode.VALIDATION_FAILED)
    }

    @Test
    fun mapQueryNormalizesBoundsFiltersAndTruncates() {
        val first = mapRow(1L, at(-1), at(1), 2)
        val second = mapRow(2L, at(-2), at(-1), 2)
        val extra = mapRow(3L, at(-1), at(1), 20)
        `when`(
            mapper.findMapItems(
                BigDecimal("22.5000000"), BigDecimal("22.6000000"),
                BigDecimal("113.9000000"), BigDecimal("114.2000000"),
                "HIKING", at(0), 3
            )
        ).thenReturn(listOf(first, second, extra))

        val result = service.listActivitiesOnMap(
            BigDecimal("22.5"), BigDecimal("22.6"),
            BigDecimal("113.9"), BigDecimal("114.2"),
            " hiking ", 2
        )

        assertThat(result.items).extracting(Function {  it.activityId  }).containsExactly("1", "2")
        assertThat(result.items[0].cover!!.url).isEqualTo("/api/v1/files/11/content")
        assertThat(result.items[0].latitude).isEqualByComparingTo(LATITUDE)
        assertThat(result.items[0].longitude).isEqualByComparingTo(LONGITUDE)
        assertThat(result.items[0].placeName).isEqualTo("市民中心东门")
        assertThat(result.items[0].coordinateSystem).isEqualTo("GCJ-02")
        assertThat(result.items[0].registrationStatus).isEqualTo("OPEN")
        assertThat(result.items[1].registrationStatus).isEqualTo("CLOSED")
        assertThat(result.items[0].feeType).isEqualTo("FREE")
        assertThat(result.truncated).isTrue()
        verify(mapper).findMapItems(
            eq(BigDecimal("22.5000000")), eq(BigDecimal("22.6000000")),
            eq(BigDecimal("113.9000000")), eq(BigDecimal("114.2000000")),
            eq("HIKING"), eq(at(0)), eq(3)
        )
    }

    @Test
    fun mapViewportCanSortByUserDistance() {
        `when`(
            mapper.findMapItemsByDistance(
                bd("22.5000000"), bd("22.6000000"),
                bd("114.0000000"), bd("114.1000000"),
                LATITUDE, LONGITUDE, null, "HIKING", at(0), 3
            )
        ).thenReturn(listOf(mapRowWithDistance(11L, 120L), mapRowWithDistance(12L, 450L)))

        val result = service.listActivitiesOnMap(
            bd("22.5"), bd("22.6"), bd("114.0"), bd("114.1"),
            LATITUDE, LONGITUDE, null, "HIKING", 2
        )

        assertThat(result.items).extracting(Function {  it.activityId  }).containsExactly("11", "12")
        assertThat(result.items).extracting(Function {  it.distanceMeters  }).containsExactly(120L, 450L)
    }

    @Test
    fun mapNearbyModeFiltersByCenterAndRadiusWithoutBounds() {
        `when`(
            mapper.findMapItemsByDistance(
                bd("22.4981299"), bd("22.5880621"),
                bd("114.0091788"), bd("114.1065512"),
                LATITUDE, LONGITUDE, 5000, null, at(0), 2
            )
        ).thenReturn(listOf(mapRowWithDistance(11L, 321L)))

        val result = service.listActivitiesOnMap(
            null, null, null, null, LATITUDE, LONGITUDE, 5000, null, 1
        )

        assertThat(result.items).singleElement()
            .satisfies(Consumer {  assertThat(it.distanceMeters).isEqualTo(321L)  })
        assertThat(result.truncated).isFalse()
    }

    @Test
    fun mapRejectsPartialMixedAndIncompleteQueryModes() {
        val invalidCalls = listOf(
            { service.listActivitiesOnMap(bd("22.5"), null, bd("114.0"), bd("114.1"), null, null, null, null, 100); Unit },
            { service.listActivitiesOnMap(null, null, null, null, null, null, null, null, 100); Unit },
            { service.listActivitiesOnMap(bd("22.5"), bd("22.6"), bd("114.0"), bd("114.1"), LATITUDE, LONGITUDE, 5000, null, 100); Unit },
            { service.listActivitiesOnMap(null, null, null, null, LATITUDE, null, 5000, null, 100); Unit },
            { service.listActivitiesOnMap(null, null, null, null, LATITUDE, LONGITUDE, null, null, 100); Unit },
            { service.listActivitiesOnMap(null, null, null, null, LATITUDE, LONGITUDE, 50001, null, 100); Unit }
        )

        assertThat(invalidCalls).allSatisfy { call ->
            assertThatThrownBy(call)
                .isInstanceOf(BusinessException::class.java)
                .extracting(Function {  (it as BusinessException).errorCode  })
                .isEqualTo(CommonErrorCode.VALIDATION_FAILED)
        }
        verifyNoInteractions(mapper)
    }

    @Test
    fun mapQueryRejectsInvalidBoundsCategoryAndLimitBeforeMapperCall() {
        val invalidCalls = listOf(
            { service.listActivitiesOnMap(null, bd("22.6"), bd("113.9"), bd("114.2"), null, 100); Unit },
            { service.listActivitiesOnMap(bd("22.6"), bd("22.6"), bd("113.9"), bd("114.2"), null, 100); Unit },
            { service.listActivitiesOnMap(bd("-90.0000001"), bd("22.6"), bd("113.9"), bd("114.2"), null, 100); Unit },
            { service.listActivitiesOnMap(bd("22.5"), bd("22.6"), bd("180"), bd("181"), null, 100); Unit },
            { service.listActivitiesOnMap(bd("22.50000001"), bd("22.6"), bd("113.9"), bd("114.2"), null, 100); Unit },
            { service.listActivitiesOnMap(bd("22.5"), bd("22.6"), bd("113.9"), bd("114.2"), "unknown", 100); Unit },
            { service.listActivitiesOnMap(bd("22.5"), bd("22.6"), bd("113.9"), bd("114.2"), null, 0); Unit },
            { service.listActivitiesOnMap(bd("22.5"), bd("22.6"), bd("113.9"), bd("114.2"), null, 201); Unit }
        )

        for (call in invalidCalls) {
            assertThatThrownBy(call)
                .isInstanceOf(BusinessException::class.java)
                .extracting(Function {  (it as BusinessException).errorCode  })
                .isEqualTo(CommonErrorCode.VALIDATION_FAILED)
        }
        verifyNoInteractions(mapper)
    }

    @Test
    fun expiredPublishedDetailUsesEndedForActivityAndRegistrationStatus() {
        val row = rowWithSchedule(2, at(-4), at(-3), at(-2), at(0))
        `when`(mapper.findPublicById(1L)).thenReturn(row)
        `when`(mapper.findPublicMedia(1L)).thenReturn(listOf())

        val result = service.getPublicActivity(1L)

        assertThat(result.status).isEqualTo("ENDED")
        assertThat(result.registrationStatus).isEqualTo("ENDED")
    }

    @Test
    fun closedRegistrationRemainsClosedBeforeActivityEnds() {
        val row = rowWithSchedule(2, at(-2), at(0), at(1), at(2))
        `when`(mapper.findPublicById(1L)).thenReturn(row)
        `when`(mapper.findPublicMedia(1L)).thenReturn(listOf())

        val result = service.getPublicActivity(1L)

        assertThat(result.status).isEqualTo("PUBLISHED")
        assertThat(result.registrationStatus).isEqualTo("CLOSED")
    }

    @Test
    fun listUsesStableCursorAndRequestsOneExtraRow() {
        val first = row(1L, at(2), 2, "USER", 21L, "小明", 12L)
        val second = row(2L, at(3), 2, "ORGANIZATION", 41L, "山海户外", 13L)
        val third = row(3L, at(4), 2, "USER", 22L, "小红", 14L)
        `when`(mapper.findPublicPage(2, null, null, null, null, null, at(0), 3))
            .thenReturn(listOf(first, second, third))

        val result = service.listPublicActivities(null, 2, null, null, null)

        assertThat(result.items).extracting(Function {  it.activityId  }).containsExactly("1", "2")
        assertThat(result.hasMore).isTrue()
        assertThat(result.nextCursor).isNotBlank()
        `when`(mapper.findPublicPage(2, null, null, null, at(3), 2L, at(0), 3))
            .thenReturn(listOf(third))
        val nextPage = service.listPublicActivities(result.nextCursor, 2, null, null, null)

        assertThat(nextPage.items).extracting(Function {  it.activityId  }).containsExactly("3")
        verify(mapper).findPublicPage(
            eq(2), eq(null), eq(null), eq(null), eq(second.startsAt), eq(2L),
            eq(at(0)), eq(3)
        )
    }

    @Test
    fun rejectsUnsupportedPublicStatus() {
        assertThatThrownBy { service.listPublicActivities(null, 20, "DRAFT", null, null) }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(CommonErrorCode.VALIDATION_FAILED)
    }

    @Test
    fun managedListUsesCurrentUserAndOwnedOrganizationWithStableUpdatedCursor() {
        val principal = UserPrincipal(21L, "session-a")
        val first = managedRow(3L, at(4), 1, "USER", 21L, "小明", 12L)
        val second = managedRow(2L, at(3), 2, "ORGANIZATION", 41L, "山海户外", 13L)
        val third = managedRow(1L, at(2), 5, "USER", 21L, "小明", 12L)
        `when`(mapper.findManagedPage(21L, 41L, null, null, null, null, 3))
            .thenReturn(listOf(first, second, third))

        val result = service.listManagedActivities(principal, null, 2, null, null)

        assertThat(result.items).extracting(Function {  it.activityId  }).containsExactly("3", "2")
        assertThat(result.items[0].status).isEqualTo("DRAFT")
        assertThat(result.items[1].owner.ownerType).isEqualTo("ORGANIZATION")
        assertThat(result.items[0].version).isEqualTo(7)
        assertThat(result.items[0].latitude).isEqualByComparingTo(LATITUDE)
        assertThat(result.items[0].longitude).isEqualByComparingTo(LONGITUDE)
        assertThat(result.items[0].placeName).isEqualTo("市民中心东门")
        assertThat(result.items[0].coordinateSystem).isEqualTo("GCJ-02")
        assertThat(result.hasMore).isTrue()
        assertThat(result.nextCursor).isNotBlank()

        `when`(mapper.findManagedPage(21L, 41L, null, "ORGANIZATION", at(3), 2L, 3))
            .thenReturn(listOf(second))
        val nextPage = service.listManagedActivities(principal, result.nextCursor, 2, null, "ORGANIZATION")

        assertThat(nextPage.items).extracting(Function {  it.activityId  }).containsExactly("2")
        verify(mapper).findManagedPage(
            eq(21L), eq(41L), eq(null), eq("ORGANIZATION"), eq(at(3)), eq(2L), eq(3)
        )
    }

    @Test
    fun rejectsUnsupportedManagedStatusAndOwnerType() {
        val principal = UserPrincipal(21L, "session-a")

        assertThatThrownBy { service.listManagedActivities(principal, null, 20, "UNKNOWN", null) }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(CommonErrorCode.VALIDATION_FAILED)

        assertThatThrownBy { service.listManagedActivities(principal, null, 20, null, "PUBLIC") }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(CommonErrorCode.VALIDATION_FAILED)
    }

    @Test
    fun managedDetailMapsFullFieldsAndHidesOrganizationPersonalFields() {
        val principal = UserPrincipal(21L, "session-a")
        `when`(mapper.findManagedById(21L, 41L, 1L))
            .thenReturn(managedDetailRow(5, "ORGANIZATION", 41L, "山海户外", 13L))
        `when`(mapper.findPublicMedia(1L)).thenReturn(
            listOf(ActivityPublicMediaRow(31L, 1), ActivityPublicMediaRow(32L, 2))
        )

        val result = service.getManagedActivity(principal, 1L)

        assertThat(result.activityId).isEqualTo("1")
        assertThat(result.status).isEqualTo("HIDDEN")
        assertThat(result.title).isEqualTo("完整活动")
        assertThat(result.owner.ownerType).isEqualTo("ORGANIZATION")
        assertThat(result.owner.displayName).isEqualTo("山海户外")
        assertThat(result.cover!!.url).isEqualTo("/api/v1/files/11/content")
        assertThat(result.description).isEqualTo("完整说明")
        assertThat(result.media).extracting(Function {  it.fileId  }).containsExactly("31", "32")
        assertThat(result.registrationStartsAt).isEqualTo(NOW.minusSeconds(7200))
        assertThat(result.registrationEndsAt).isEqualTo(NOW.minusSeconds(3600))
        assertThat(result.participantCount).isEqualTo(2)
        assertThat(result.latitude).isEqualByComparingTo(LATITUDE)
        assertThat(result.longitude).isEqualByComparingTo(LONGITUDE)
        assertThat(result.placeName).isEqualTo("市民中心东门")
        assertThat(result.coordinateSystem).isEqualTo("GCJ-02")
        assertThat(result.version).isEqualTo(9)
        assertThat(result.createdAt).isEqualTo(NOW.minusSeconds(14400))
        assertThat(result.publishedAt).isEqualTo(NOW)
        assertThat(result.signupDetails).isNull()
        assertThat(result.organizerMessage).isNull()
    }

    @Test
    fun managedDetailRequiresAuthenticationAndValidActivityId() {
        assertThatThrownBy { service.getManagedActivity(null, 1L) }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(CommonErrorCode.AUTHENTICATION_REQUIRED)

        val principal = UserPrincipal(21L, "session-a")
        assertThatThrownBy { service.getManagedActivity(principal, 0L) }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(CommonErrorCode.VALIDATION_FAILED)
        verifyNoInteractions(mapper)
    }

    @Test
    fun detailMapsOrderedMediaAndHidesOrganizationPersonalFields() {
        val row = row(2, "ORGANIZATION", 41L, "山海户外", 13L)
        `when`(mapper.findPublicById(1L)).thenReturn(row)
        `when`(mapper.findPublicMedia(1L)).thenReturn(
            listOf(ActivityPublicMediaRow(31L, 1), ActivityPublicMediaRow(32L, 2))
        )

        val result = service.getPublicActivity(1L)

        assertThat(result.owner.ownerType).isEqualTo("ORGANIZATION")
        assertThat(result.owner.displayName).isEqualTo("山海户外")
        assertThat(result.media).extracting(Function {  it.fileId  }).containsExactly("31", "32")
        assertThat(result.signupDetails).isNull()
        assertThat(result.organizerMessage).isNull()
        assertThat(result.description).isEqualTo("活动介绍")
        assertThat(result.latitude).isEqualByComparingTo(LATITUDE)
        assertThat(result.longitude).isEqualByComparingTo(LONGITUDE)
        assertThat(result.placeName).isEqualTo("市民中心东门")
        assertThat(result.coordinateSystem).isEqualTo("GCJ-02")
        assertThat(result.distanceMeters).isNull()
        assertThat(result.myParticipationStatus).isNull()
        assertThat(result.viewerIsOwner).isFalse()
    }

    @Test
    fun authenticatedDetailReturnsParticipationStatusForNonOwner() {
        val row = row(2, "USER", 21L, "小明", 12L)
        `when`(mapper.findPublicById(1L)).thenReturn(row)
        `when`(mapper.findPublicMedia(1L)).thenReturn(listOf())
        `when`(participations.findStatus(1L, 22L))
            .thenReturn(java.util.Optional.of("TERMINATED"))

        val result = service.getPublicActivity(UserPrincipal(22L, "session-viewer"), 1L)

        assertThat(result.myParticipationStatus).isEqualTo("TERMINATED")
        assertThat(result.viewerIsOwner).isFalse()
    }

    @Test
    fun authenticatedOwnerDetailHidesOwnParticipationStatus() {
        val row = row(2, "USER", 21L, "小明", 12L)
        `when`(mapper.findPublicById(1L)).thenReturn(row)
        `when`(mapper.findPublicMedia(1L)).thenReturn(listOf())

        val result = service.getPublicActivity(UserPrincipal(21L, "session-owner"), 1L)

        assertThat(result.myParticipationStatus).isNull()
        assertThat(result.viewerIsOwner).isTrue()
        verifyNoInteractions(participations)
    }

    @Test
    fun hiddenActivityIsNotFoundForPublicDetail() {
        `when`(mapper.findPublicById(1L)).thenReturn(null)

        assertThatThrownBy { service.getPublicActivity(1L) }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND)
    }

    private fun row(
        status: Int,
        ownerType: String,
        ownerId: Long,
        ownerName: String,
        avatarFileId: Long
    ): ActivityPublicRow {
        return row(1L, at(2), status, ownerType, ownerId, ownerName, avatarFileId)
    }

    private fun row(
        activityId: Long,
        startsAt: LocalDateTime,
        status: Int,
        ownerType: String,
        ownerId: Long,
        ownerName: String,
        avatarFileId: Long
    ): ActivityPublicRow {
        return ActivityPublicRow(
            activityId, status, "测试活动", "HIKING", 11L,
            ownerType, ownerId, ownerName, avatarFileId,
            at(-1), at(1), startsAt, at(3),
            "440305", "深圳市南山区测试地址", LATITUDE, LONGITUDE,
            20, 2, "活动介绍",
            if (ownerType == "USER") "报名说明" else null,
            if (ownerType == "USER") "组织者留言" else null,
            at(-2), at(-3), at(-1), placeName = "市民中心东门"
        )
    }

    private fun rowWithSchedule(
        status: Int,
        registrationStartsAt: LocalDateTime,
        registrationEndsAt: LocalDateTime,
        startsAt: LocalDateTime,
        endsAt: LocalDateTime
    ): ActivityPublicRow {
        return ActivityPublicRow(
            1L, status, "测试活动", "HIKING", 11L,
            "USER", 21L, "小明", 12L,
            registrationStartsAt, registrationEndsAt, startsAt, endsAt,
            "440305", "深圳市南山区测试地址", LATITUDE, LONGITUDE,
            20, 2, "活动介绍", "报名说明", "组织者留言",
            at(-5), at(-5), at(-1), placeName = "市民中心东门"
        )
    }

    private fun rowWithDistance(activityId: Long, distanceMeters: Long): ActivityPublicRow {
        return ActivityPublicRow(
            activityId, 2, "附近活动$activityId", "HIKING", 11L,
            "USER", 21L, "小明", 12L,
            at(-1), at(1), at(2), at(3),
            "440305", "深圳市南山区测试地址", LATITUDE, LONGITUDE,
            20, 2, "活动介绍", "报名说明", "组织者留言",
            at(-2), at(-3), at(-1), 1,
            null, null, null, null, "市民中心东门", distanceMeters
        )
    }

    private fun mapRow(
        activityId: Long,
        registrationStartsAt: LocalDateTime,
        registrationEndsAt: LocalDateTime,
        participantCount: Int
    ): ActivityMapRow {
        return ActivityMapRow(
            activityId, "地图活动$activityId", "HIKING", 11L,
            registrationStartsAt, registrationEndsAt, at(2), at(3),
            "440305", "深圳市南山区地图地址", LATITUDE, LONGITUDE,
            20, participantCount, "市民中心东门"
        )
    }

    private fun mapRowWithDistance(activityId: Long, distanceMeters: Long): ActivityMapRow {
        return ActivityMapRow(
            activityId, "附近地图活动$activityId", "HIKING", 11L,
            at(-1), at(1), at(2), at(3),
            "440305", "深圳市南山区地图地址", LATITUDE, LONGITUDE,
            20, 2, "市民中心东门", distanceMeters
        )
    }

    private fun managedRow(
        activityId: Long,
        updatedAt: LocalDateTime,
        status: Int,
        ownerType: String,
        ownerId: Long,
        ownerName: String,
        avatarFileId: Long
    ): ActivityManagedRow {
        return ActivityManagedRow(
            activityId, status, "测试活动", "HIKING", 11L,
            ownerType, ownerId, ownerName, avatarFileId,
            at(2), at(3), "440305", "深圳市南山区测试地址", LATITUDE, LONGITUDE,
            20, 2, 7, updatedAt, "市民中心东门"
        )
    }

    private fun managedDetailRow(
        status: Int,
        ownerType: String,
        ownerId: Long,
        ownerName: String,
        avatarFileId: Long
    ): ActivityManagedDetailRow {
        return ActivityManagedDetailRow(
            1L, status, "完整活动", "HIKING", 11L,
            ownerType, ownerId, ownerName, avatarFileId,
            at(-2), at(-1), at(2), at(3),
            "440305", "深圳市南山区完整地址", LATITUDE, LONGITUDE,
            20, 2, "完整说明",
            "企业不应返回的报名说明", "企业不应返回的组织者留言",
            at(0), 9, at(-4), at(1), placeName = "市民中心东门"
        )
    }

    companion object {
        private val NOW = Instant.parse("2026-08-07T00:00:00Z")
        private val LATITUDE = BigDecimal("22.5430960")
        private val LONGITUDE = BigDecimal("114.0578650")

        @JvmStatic
        private fun bd(value: String) = BigDecimal(value)

        @JvmStatic
        private fun at(hoursFromNow: Long): LocalDateTime {
            return LocalDateTime.ofInstant(
                NOW.plusSeconds(hoursFromNow * 3600), ZoneOffset.UTC
            )
        }
    }
}
