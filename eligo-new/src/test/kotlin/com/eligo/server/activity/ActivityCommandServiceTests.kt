package com.eligo.server.activity

import java.util.function.Function

import com.eligo.server.account.service.AccountStateLockService
import com.eligo.server.activity.dto.OrganizationActivityCreateRequest
import com.eligo.server.activity.dto.OrganizationActivityUpdateRequest
import com.eligo.server.activity.dto.PersonalActivityCreateRequest
import com.eligo.server.activity.dto.PersonalActivityUpdateRequest
import com.eligo.server.activity.entity.ActivityCreateIdempotencyTombstoneEntity
import com.eligo.server.activity.entity.ActivityEntity
import com.eligo.server.activity.entity.ActivityLifecycleEventEntity
import com.eligo.server.activity.entity.ActivityMediaEntity
import com.eligo.server.activity.mapper.ActivityCreateIdempotencyTombstoneMapper
import com.eligo.server.activity.mapper.ActivityLifecycleEventMapper
import com.eligo.server.activity.mapper.ActivityMapper
import com.eligo.server.activity.mapper.ActivityMediaMapper
import com.eligo.server.activity.service.ActivityCreateOutcome
import com.eligo.server.activity.service.ActivityTopicService
import com.eligo.server.activity.service.DefaultActivityCommandService
import com.eligo.server.activity.vo.PublicImageView
import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.file.entity.FileObjectEntity
import com.eligo.server.file.mapper.FileObjectMapper
import com.eligo.server.organization.entity.OrganizationEntity
import com.eligo.server.organization.mapper.OrganizationMapper
import com.eligo.server.profile.entity.UserProfileEntity
import com.eligo.server.profile.mapper.UserProfileMapper
import com.eligo.server.profile.service.ProfileCompletionReader
import com.eligo.server.profile.service.RegionCatalog
import com.eligo.server.participation.service.ParticipationActivityCancellationService
import com.eligo.server.security.UserPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.InOrder
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class ActivityCommandServiceTests {

    private val activities = mock(ActivityMapper::class.java)
    private val idempotencyTombstones = mock(ActivityCreateIdempotencyTombstoneMapper::class.java)
    private val media = mock(ActivityMediaMapper::class.java)
    private val topicService = mock(ActivityTopicService::class.java)
    private val events = mock(ActivityLifecycleEventMapper::class.java)
    private val participationCancellation = mock(ParticipationActivityCancellationService::class.java)
    private val accountStates = mock(AccountStateLockService::class.java)
    private val organizations = mock(OrganizationMapper::class.java)
    private val profiles = mock(UserProfileMapper::class.java)
    private val files = mock(FileObjectMapper::class.java)
    private val completion = mock(ProfileCompletionReader::class.java)
    private val regions = mock(RegionCatalog::class.java)
    private val redis = mock(StringRedisTemplate::class.java)
    @Suppress("UNCHECKED_CAST")
    private val values = mock(ValueOperations::class.java) as ValueOperations<String, String>
    private val idempotency = ConcurrentHashMap<String, String>()
    private val rememberedTtls = mutableListOf<Duration>()
    private lateinit var service: DefaultActivityCommandService

    @BeforeEach
    fun setUp() {
        `when`(redis.opsForValue()).thenReturn(values)
        rememberedTtls.clear()
        `when`(values.get(any<String>())).thenAnswer { invocation ->
            idempotency[invocation.getArgument(0)]
        }
        doAnswer { invocation ->
            idempotency[invocation.getArgument(0)] = invocation.getArgument(1)
            rememberedTtls.add(invocation.getArgument(2))
            null
        }.`when`(values).set(any<String>(), any<String>(), any<Duration>())
        `when`(completion.isCompleted(USER_ID)).thenReturn(true)
        `when`(regions.isDistrictCode("440305")).thenReturn(true)
        `when`(media.findByActivityId(any<Long>())).thenReturn(listOf())
        `when`(topicService.normalize(org.mockito.kotlin.anyOrNull<List<String>>()))
            .thenAnswer { invocation ->
                val values = invocation.getArgument<List<String>?>(0)
                values?.toList() ?: listOf<String>()
            }
        `when`(topicService.findByActivityId(any<Long>())).thenReturn(listOf())
        `when`(profiles.findByUserId(USER_ID)).thenReturn(java.util.Optional.of(profile()))
        `when`(idempotencyTombstones.findByScopeAndKey(any<String>(), any<String>()))
            .thenReturn(java.util.Optional.empty())
        service = DefaultActivityCommandService(
            activities,
            idempotencyTombstones,
            media,
            events,
            organizations,
            profiles,
            files,
            completion,
            regions,
            redis,
            participationCancellation,
            accountStates,
            topics = topicService,
            clock = Clock.fixed(NOW, ZoneOffset.UTC)
        )
    }

    @Test
    fun createsPersonalDraftAndWritesCreationLifecycleEvent() {
        val saved = arrayOfNulls<ActivityEntity>(1)
        doAnswer { invocation ->
            val entity = invocation.getArgument<ActivityEntity>(0)
            entity.id = 1001L
            saved[0] = entity
            1
        }.`when`(activities).insert(any<ActivityEntity>())

        val outcome = service.createPersonal(PRINCIPAL, personalRequest(), "create-personal-1")

        assertThat(outcome.replayed).isFalse()
        assertThat(outcome.view.activityId).isEqualTo("1001")
        assertThat(outcome.view.status).isEqualTo("DRAFT")
        assertThat(outcome.view.title).isEqualTo("个人草稿")
        assertThat(outcome.view.owner.ownerType).isEqualTo("USER")
        assertThat(outcome.view.signupDetails).isEqualTo("报名说明")
        assertThat(saved[0]!!.ownerUserId).isEqualTo(USER_ID)
        assertThat(saved[0]!!.ownerOrganizationId).isNull()
        assertThat(saved[0]!!.operatorUserId).isEqualTo(USER_ID)
        assertThat(saved[0]!!.status).isEqualTo(1)
        assertThat(saved[0]!!.participantCount).isZero()
        assertThat(saved[0]!!.version).isZero()
        assertThat(saved[0]!!.createIdempotencyFingerprint)
            .`as`("无坐标请求在滚动发布期间必须保持旧节点可重放")
            .isEqualTo(LEGACY_COORDINATE_FREE_FINGERPRINT)
        verify(events).insert(any<ActivityLifecycleEventEntity>())
    }

    @Test
    fun createPersistsNormalizedTopicsAndReturnsThem() {
        doAnswer { invocation ->
            val entity = invocation.getArgument<ActivityEntity>(0)
            entity.id = 1023L
            1
        }.`when`(activities).insert(any<ActivityEntity>())
        `when`(topicService.normalize(listOf("  徒步  ", "Hiking", "hiking")))
            .thenReturn(listOf("徒步", "Hiking"))
        `when`(topicService.findByActivityId(1023L)).thenReturn(listOf("徒步", "Hiking"))

        val outcome = service.createPersonal(
            PRINCIPAL,
            personalRequestWithTopics(listOf("  徒步  ", "Hiking", "hiking")),
            "create-topics"
        )

        verify(topicService).replace(1023L, listOf("徒步", "Hiking"), localNow())
        assertThat(outcome.view.topics).containsExactly("徒步", "Hiking")
    }

    @Test
    fun idempotencyRejectsChangedTopics() {
        val saved = arrayOfNulls<ActivityEntity>(1)
        doAnswer { invocation ->
            val entity = invocation.getArgument<ActivityEntity>(0)
            entity.id = 1024L
            saved[0] = entity
            1
        }.`when`(activities).insert(any<ActivityEntity>())
        `when`(activities.findByCreateIdempotencyScope(any<String>(), eq("create-topic-idempotent")))
            .thenAnswer { java.util.Optional.ofNullable(saved[0]) }

        service.createPersonal(
            PRINCIPAL, personalRequestWithTopics(listOf("徒步")), "create-topic-idempotent"
        )

        assertThatThrownBy {
            service.createPersonal(
                PRINCIPAL, personalRequestWithTopics(listOf("露营")), "create-topic-idempotent"
            )
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(com.eligo.server.activity.error.ActivityErrorCode.IDEMPOTENCY_KEY_CONFLICT)
    }

    @Test
    fun idempotencyRejectsAddingTopicsToRequestThatOriginallyOmittedThem() {
        val saved = arrayOfNulls<ActivityEntity>(1)
        doAnswer { invocation ->
            val entity = invocation.getArgument<ActivityEntity>(0)
            entity.id = 1026L
            saved[0] = entity
            1
        }.`when`(activities).insert(any<ActivityEntity>())
        `when`(activities.findByCreateIdempotencyScope(any<String>(), eq("create-topic-added")))
            .thenAnswer { java.util.Optional.ofNullable(saved[0]) }

        service.createPersonal(
            PRINCIPAL, personalRequestWithTopics(null), "create-topic-added"
        )

        assertThatThrownBy {
            service.createPersonal(
                PRINCIPAL, personalRequestWithTopics(listOf("徒步")), "create-topic-added"
            )
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(com.eligo.server.activity.error.ActivityErrorCode.IDEMPOTENCY_KEY_CONFLICT)
    }

    @Test
    fun createNormalizesAndPersistsPlaceName() {
        val saved = arrayOfNulls<ActivityEntity>(1)
        doAnswer { invocation ->
            val entity = invocation.getArgument<ActivityEntity>(0)
            entity.id = 1019L
            saved[0] = entity
            1
        }.`when`(activities).insert(any<ActivityEntity>())
        val request = PersonalActivityCreateRequest(
            "个人草稿", "HIKING", "活动介绍", null, listOf<String>(),
            at(1), at(2), at(3), at(4), "440305", "详细地址",
            null, null, 20, "报名说明", "组织者留言",
            "UNLIMITED", null, null, null, null, "  市民中心东门  "
        )

        val outcome = service.createPersonal(PRINCIPAL, request, "create-place-name")

        assertThat(saved[0]!!.placeName).isEqualTo("市民中心东门")
        assertThat(outcome.view.placeName).isEqualTo("市民中心东门")
    }

    @Test
    fun createRejectsPlaceNameLongerThanOneHundredUnicodeCodePoints() {
        val request = PersonalActivityCreateRequest(
            "个人草稿", "HIKING", "活动介绍", null, listOf<String>(),
            at(1), at(2), at(3), at(4), "440305", "详细地址",
            null, null, 20, "报名说明", "组织者留言",
            "UNLIMITED", null, null, null, null, "地点".repeat(51)
        )

        assertThatThrownBy {
            service.createPersonal(PRINCIPAL, request, "create-place-name-too-long")
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(CommonErrorCode.VALIDATION_FAILED)
        verify(activities, never()).insert(any<ActivityEntity>())
    }

    @Test
    fun publishRejectsDraftWithoutPlaceName() {
        val draft = publishableDraft(3020L)
        draft.placeName = null
        `when`(activities.lockById(3020L)).thenReturn(java.util.Optional.of(draft))
        `when`(files.lockById(501L)).thenReturn(java.util.Optional.of(usableFile(501L)))

        assertThatThrownBy { service.publish(PRINCIPAL, 3020L) }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(CommonErrorCode.VALIDATION_FAILED)
    }

    @Test
    fun createNormalizesCoordinatesToSevenDecimalPlaces() {
        val saved = arrayOfNulls<ActivityEntity>(1)
        doAnswer { invocation ->
            val entity = invocation.getArgument<ActivityEntity>(0)
            entity.id = 1016L
            saved[0] = entity
            1
        }.`when`(activities).insert(any<ActivityEntity>())

        val outcome = service.createPersonal(
            PRINCIPAL,
            personalRequestWithCoordinates(BigDecimal("22.5"), BigDecimal("114.057865")),
            "create-location-normalized"
        )

        assertThat(saved[0]!!.latitude).isEqualByComparingTo("22.5000000")
        assertThat(saved[0]!!.latitude!!.scale()).isEqualTo(7)
        assertThat(saved[0]!!.longitude).isEqualByComparingTo("114.0578650")
        assertThat(saved[0]!!.longitude!!.scale()).isEqualTo(7)
        assertThat(outcome.view.coordinateSystem).isEqualTo("GCJ-02")
    }

    @Test
    fun createRejectsUnsupportedCoordinateSystem() {
        assertThatThrownBy {
            service.createPersonal(
                PRINCIPAL,
                personalRequestWithCoordinateSystem(
                    BigDecimal("22.5"), BigDecimal("114.057865"), "WGS84"
                ),
                "create-location-wgs84"
            )
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(CommonErrorCode.VALIDATION_FAILED)

        verify(activities, never()).insert(any<ActivityEntity>())
    }

    @Test
    fun createRejectsCoordinateSystemWithoutCoordinates() {
        assertThatThrownBy {
            service.createPersonal(
                PRINCIPAL,
                personalRequestWithCoordinateSystem(null, null, "GCJ-02"),
                "create-location-system-only"
            )
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(CommonErrorCode.VALIDATION_FAILED)

        verify(activities, never()).insert(any<ActivityEntity>())
    }

    @Test
    fun createRejectsUnpairedCoordinatesBeforeInsert() {
        assertThatThrownBy {
            service.createPersonal(
                PRINCIPAL,
                personalRequestWithCoordinates(BigDecimal("22.5"), null),
                "create-location-unpaired"
            )
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(CommonErrorCode.VALIDATION_FAILED)

        verify(activities, never()).insert(any<ActivityEntity>())
    }

    @Test
    fun idempotencyNormalizesEquivalentCoordinatesAndRejectsChangedCoordinates() {
        val saved = arrayOfNulls<ActivityEntity>(1)
        doAnswer { invocation ->
            val entity = invocation.getArgument<ActivityEntity>(0)
            entity.id = 1017L
            saved[0] = entity
            1
        }.`when`(activities).insert(any<ActivityEntity>())
        `when`(activities.selectById(1017L)).thenAnswer { saved[0] }
        `when`(activities.findByCreateIdempotencyScope(any<String>(), eq("create-location-idempotent")))
            .thenAnswer { java.util.Optional.ofNullable(saved[0]) }

        val first = service.createPersonal(
            PRINCIPAL,
            personalRequestWithCoordinates(BigDecimal("22.5"), BigDecimal("114.057865")),
            "create-location-idempotent"
        )
        val replay = service.createPersonal(
            PRINCIPAL,
            personalRequestWithCoordinates(BigDecimal("22.5000000"), BigDecimal("114.0578650")),
            "create-location-idempotent"
        )

        assertThat(first.replayed).isFalse()
        assertThat(replay.replayed).isTrue()
        assertThatThrownBy {
            service.createPersonal(
                PRINCIPAL,
                personalRequestWithCoordinates(BigDecimal("22.5000001"), BigDecimal("114.0578650")),
                "create-location-idempotent"
            )
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(com.eligo.server.activity.error.ActivityErrorCode.IDEMPOTENCY_KEY_CONFLICT)
        verify(activities).insert(any<ActivityEntity>())
    }

    @Test
    fun idempotencyRejectsChangedPlaceName() {
        val saved = arrayOfNulls<ActivityEntity>(1)
        doAnswer { invocation ->
            val entity = invocation.getArgument<ActivityEntity>(0)
            entity.id = 1022L
            saved[0] = entity
            1
        }.`when`(activities).insert(any<ActivityEntity>())
        `when`(activities.selectById(1022L)).thenAnswer { saved[0] }
        `when`(activities.findByCreateIdempotencyScope(any<String>(), eq("create-place-idempotent")))
            .thenAnswer { java.util.Optional.ofNullable(saved[0]) }

        service.createPersonal(
            PRINCIPAL, personalRequestWithPlaceName("市民中心东门"), "create-place-idempotent"
        )

        assertThatThrownBy {
            service.createPersonal(
                PRINCIPAL, personalRequestWithPlaceName("市民中心西门"), "create-place-idempotent"
            )
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(com.eligo.server.activity.error.ActivityErrorCode.IDEMPOTENCY_KEY_CONFLICT)
    }

    @Test
    fun idempotencyRejectsAddingPlaceNameToRequestThatOriginallyOmittedIt() {
        val saved = arrayOfNulls<ActivityEntity>(1)
        doAnswer { invocation ->
            val entity = invocation.getArgument<ActivityEntity>(0)
            entity.id = 1025L
            saved[0] = entity
            1
        }.`when`(activities).insert(any<ActivityEntity>())
        `when`(activities.findByCreateIdempotencyScope(any<String>(), eq("create-place-added")))
            .thenAnswer { java.util.Optional.ofNullable(saved[0]) }

        service.createPersonal(
            PRINCIPAL, personalRequestWithPlaceName(null), "create-place-added"
        )
        idempotency.clear()

        assertThatThrownBy {
            service.createPersonal(
                PRINCIPAL, personalRequestWithPlaceName("市民中心东门"), "create-place-added"
            )
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(com.eligo.server.activity.error.ActivityErrorCode.IDEMPOTENCY_KEY_CONFLICT)
    }

    @Test
    fun legacyDatabaseFingerprintReplaysSameCoordinateFreeRequestAfterUpgrade() {
        val idempotencyKey = "create-legacy-database"
        val legacy = legacyCoordinateFreeDraft(1018L, idempotencyKey)
        `when`(activities.findByCreateIdempotencyScope(any<String>(), eq(idempotencyKey)))
            .thenReturn(java.util.Optional.of(legacy))

        val replay = service.createPersonal(PRINCIPAL, personalRequest(), idempotencyKey)

        assertThat(replay.replayed).isTrue()
        assertThat(replay.view.activityId).isEqualTo("1018")
        verify(activities, never()).insert(any<ActivityEntity>())
    }

    @Test
    fun legacyRedisFingerprintReplaysSameCoordinateFreeRequestAfterUpgrade() {
        val idempotencyKey = "create-legacy-redis"
        val legacy = legacyCoordinateFreeDraft(1019L, idempotencyKey)
        idempotency["eligo:activity:create:202:USER:202:$idempotencyKey"] =
            "${legacy.id}|$LEGACY_COORDINATE_FREE_FINGERPRINT"
        `when`(activities.selectById(legacy.id)).thenReturn(legacy)
        `when`(activities.findByCreateIdempotencyScope(any<String>(), eq(idempotencyKey)))
            .thenReturn(java.util.Optional.of(legacy))

        val replay = service.createPersonal(PRINCIPAL, personalRequest(), idempotencyKey)

        assertThat(replay.replayed).isTrue()
        assertThat(replay.view.activityId).isEqualTo("1019")
        verify(activities, never()).findByCreateIdempotencyScope(any<String>(), eq(idempotencyKey))
    }

    @Test
    fun legacyTombstoneFingerprintPreservesDeletedResultAfterUpgrade() {
        val idempotencyKey = "create-legacy-tombstone"
        val legacy = ActivityCreateIdempotencyTombstoneEntity()
        legacy.createIdempotencyFingerprint = LEGACY_COORDINATE_FREE_FINGERPRINT
        legacy.expiresAt = localNow().plusHours(1)
        `when`(idempotencyTombstones.findByScopeAndKey(any<String>(), eq(idempotencyKey)))
            .thenReturn(java.util.Optional.of(legacy))

        assertThatThrownBy { service.createPersonal(PRINCIPAL, personalRequest(), idempotencyKey) }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(com.eligo.server.activity.error.ActivityErrorCode.IDEMPOTENCY_RESULT_DELETED)
    }

    @Test
    fun legacyFingerprintDoesNotMatchRequestThatAddsCoordinates() {
        val idempotencyKey = "create-legacy-with-location"
        val legacy = legacyCoordinateFreeDraft(1020L, idempotencyKey)
        `when`(activities.findByCreateIdempotencyScope(any<String>(), eq(idempotencyKey)))
            .thenReturn(java.util.Optional.of(legacy))

        assertThatThrownBy {
            service.createPersonal(
                PRINCIPAL,
                personalRequestWithCoordinates(
                    BigDecimal("22.5430960"), BigDecimal("114.0578650")
                ),
                idempotencyKey
            )
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(com.eligo.server.activity.error.ActivityErrorCode.IDEMPOTENCY_KEY_CONFLICT)
    }

    @Test
    fun duplicateKeyWinnerWithLegacyFingerprintReplaysAfterUpgrade() {
        val idempotencyKey = "create-legacy-race-winner"
        val legacy = legacyCoordinateFreeDraft(1021L, idempotencyKey)
        `when`(activities.insert(any<ActivityEntity>()))
            .thenThrow(DuplicateKeyException("并发创建命中唯一键"))
        `when`(activities.lockByCreateIdempotencyScope(any<String>(), eq(idempotencyKey)))
            .thenReturn(java.util.Optional.of(legacy))

        val replay = service.createPersonal(PRINCIPAL, personalRequest(), idempotencyKey)

        assertThat(replay.replayed).isTrue()
        assertThat(replay.view.activityId).isEqualTo("1021")
    }

    @Test
    fun titleLengthUsesUnicodeCodePoints() {
        doAnswer { invocation ->
            val entity = invocation.getArgument<ActivityEntity>(0)
            entity.id = 1006L
            1
        }.`when`(activities).insert(any<ActivityEntity>())

        val maximumTitle = "😀".repeat(20)
        val outcome = service.createPersonal(
            PRINCIPAL, personalRequestWithTitle(maximumTitle), "create-unicode-20"
        )

        assertThat(outcome.view.title).isEqualTo(maximumTitle)
        assertThatThrownBy {
            service.createPersonal(
                PRINCIPAL, personalRequestWithTitle("😀".repeat(21)), "create-unicode-21"
            )
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(CommonErrorCode.VALIDATION_FAILED)
        verify(activities, times(1)).insert(any<ActivityEntity>())
    }

    @Test
    fun createsOrganizationDraftOnlyForCurrentActiveOwner() {
        val organization = organization()
        `when`(organizations.findActiveOwnedByUserId(USER_ID)).thenReturn(listOf(organization))
        `when`(organizations.selectById(ORGANIZATION_ID)).thenReturn(organization)
        doAnswer { invocation ->
            val entity = invocation.getArgument<ActivityEntity>(0)
            entity.id = 1002L
            1
        }.`when`(activities).insert(any<ActivityEntity>())

        val outcome = service.createOrganization(
            PRINCIPAL, ORGANIZATION_ID, organizationRequest(), "create-organization-1"
        )

        assertThat(outcome.view.status).isEqualTo("DRAFT")
        assertThat(outcome.view.owner.ownerType).isEqualTo("ORGANIZATION")
        assertThat(outcome.view.owner.displayName).isEqualTo("山海户外")
        assertThat(outcome.view.signupDetails).isNull()
        assertThat(outcome.view.organizerMessage).isNull()
        verify(organizations).findActiveOwnedByUserId(USER_ID)
    }

    @Test
    fun organizationTitleLengthUsesUnicodeCodePoints() {
        val organization = organization()
        `when`(organizations.findActiveOwnedByUserId(USER_ID)).thenReturn(listOf(organization))
        `when`(organizations.selectById(ORGANIZATION_ID)).thenReturn(organization)
        doAnswer { invocation ->
            val entity = invocation.getArgument<ActivityEntity>(0)
            entity.id = 1007L
            1
        }.`when`(activities).insert(any<ActivityEntity>())

        val maximumTitle = "😀".repeat(20)
        val outcome = service.createOrganization(
            PRINCIPAL, ORGANIZATION_ID, organizationRequestWithTitle(maximumTitle),
            "create-organization-unicode-20"
        )

        assertThat(outcome.view.title).isEqualTo(maximumTitle)
        assertThatThrownBy {
            service.createOrganization(
                PRINCIPAL, ORGANIZATION_ID, organizationRequestWithTitle("😀".repeat(21)),
                "create-organization-unicode-21"
            )
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(CommonErrorCode.VALIDATION_FAILED)
        verify(activities, times(1)).insert(any<ActivityEntity>())
    }

    @Test
    fun rejectsIncompleteProfileBeforeCreatingPersonalDraft() {
        `when`(completion.isCompleted(USER_ID)).thenReturn(false)

        assertThatThrownBy { service.createPersonal(PRINCIPAL, personalRequest(), "create-incomplete") }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(AccountUserFileErrorCode.PROFILE_INCOMPLETE)
        verify(activities, never()).insert(any<ActivityEntity>())
    }

    @Test
    fun replaysSameCreateKeyAndRejectsDifferentRequest() {
        val saved = arrayOfNulls<ActivityEntity>(1)
        doAnswer { invocation ->
            val entity = invocation.getArgument<ActivityEntity>(0)
            entity.id = 1003L
            saved[0] = entity
            1
        }.`when`(activities).insert(any<ActivityEntity>())
        `when`(activities.selectById(1003L)).thenAnswer { saved[0] }
        `when`(activities.findByCreateIdempotencyScope(any<String>(), eq("create-replay")))
            .thenAnswer { java.util.Optional.ofNullable(saved[0]) }

        val first = service.createPersonal(PRINCIPAL, personalRequest(), "create-replay")
        val second = service.createPersonal(PRINCIPAL, personalRequest(), "create-replay")

        assertThat(first.replayed).isFalse()
        assertThat(second.replayed).isTrue()
        assertThat(second.view.activityId).isEqualTo("1003")
        verify(activities).insert(any<ActivityEntity>())

        assertThatThrownBy {
            service.createPersonal(
                PRINCIPAL,
                PersonalActivityCreateRequest(
                    "另一个草稿", "HIKING", null, null, listOf<String>(),
                    null, null, null, null, null, null,
                    capacity = null, signupDetails = null, organizerMessage = null
                ),
                "create-replay"
            )
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(com.eligo.server.activity.error.ActivityErrorCode.IDEMPOTENCY_KEY_CONFLICT)
    }

    @Test
    fun staleRedisEntryFallsBackToDatabaseTruth() {
        idempotency["eligo:activity:create:202:USER:202:create-stale-cache"] =
            "999|stale-fingerprint"
        doAnswer { invocation ->
            val entity = invocation.getArgument<ActivityEntity>(0)
            entity.id = 1011L
            1
        }.`when`(activities).insert(any<ActivityEntity>())

        val outcome = service.createPersonal(PRINCIPAL, personalRequest(), "create-stale-cache")

        assertThat(outcome.replayed).isFalse()
        assertThat(outcome.view.activityId).isEqualTo("1011")
        verify(activities).insert(any<ActivityEntity>())
    }

    @Test
    fun rolledBackCreateDoesNotLeaveRedisResult() {
        doAnswer { invocation ->
            val entity = invocation.getArgument<ActivityEntity>(0)
            entity.id = 1012L
            1
        }.`when`(activities).insert(any<ActivityEntity>())
        TransactionSynchronizationManager.initSynchronization()
        try {
            service.createPersonal(PRINCIPAL, personalRequest(), "create-rollback-cache")

            assertThat(idempotency).doesNotContainKey(
                "eligo:activity:create:202:USER:202:create-rollback-cache"
            )
            TransactionSynchronizationManager.getSynchronizations().forEach { synchronization ->
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)
            }
            assertThat(idempotency).doesNotContainKey(
                "eligo:activity:create:202:USER:202:create-rollback-cache"
            )
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    @Test
    fun committedCreateWritesRedisResultAfterCommit() {
        doAnswer { invocation ->
            val entity = invocation.getArgument<ActivityEntity>(0)
            entity.id = 1014L
            1
        }.`when`(activities).insert(any<ActivityEntity>())
        val cacheKey = "eligo:activity:create:202:USER:202:create-commit-cache"
        TransactionSynchronizationManager.initSynchronization()
        try {
            service.createPersonal(PRINCIPAL, personalRequest(), "create-commit-cache")

            assertThat(idempotency).doesNotContainKey(cacheKey)
            TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
            assertThat(idempotency).containsKey(cacheKey)
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    @Test
    fun committedCreateCalculatesRedisTtlAtCommitTime() {
        val current = AtomicReference(NOW)
        val mutableClock = object : Clock() {
            override fun getZone(): ZoneOffset = ZoneOffset.UTC
            override fun withZone(zone: ZoneId): Clock = this
            override fun instant(): Instant = current.get()
        }
        service = DefaultActivityCommandService(
            activities, idempotencyTombstones, media, events, organizations,
            profiles, files, completion, regions, redis,
            participationCancellation, accountStates, clock = mutableClock
        )
        doAnswer { invocation ->
            val entity = invocation.getArgument<ActivityEntity>(0)
            entity.id = 1016L
            1
        }.`when`(activities).insert(any<ActivityEntity>())

        TransactionSynchronizationManager.initSynchronization()
        try {
            service.createPersonal(PRINCIPAL, personalRequest(), "create-commit-ttl")
            current.set(NOW.plus(Duration.ofHours(2)))
            TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }

            assertThat(rememberedTtls).containsExactly(Duration.ofHours(22))
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    @Test
    fun redisReadFailureDoesNotBlockDatabaseCreate() {
        val cacheKey = "eligo:activity:create:202:USER:202:create-redis-down"
        `when`(values.get(cacheKey)).thenThrow(IllegalStateException("缓存不可用"))
        doAnswer { invocation ->
            val entity = invocation.getArgument<ActivityEntity>(0)
            entity.id = 1015L
            1
        }.`when`(activities).insert(any<ActivityEntity>())

        val outcome = service.createPersonal(PRINCIPAL, personalRequest(), "create-redis-down")

        assertThat(outcome.replayed).isFalse()
        assertThat(outcome.view.activityId).isEqualTo("1015")
    }

    @Test
    fun activityFilesAreLockedInIdentifierOrder() {
        `when`(files.lockById(501L)).thenReturn(java.util.Optional.of(usableFile(501L)))
        `when`(files.lockById(502L)).thenReturn(java.util.Optional.of(usableFile(502L)))
        `when`(files.lockById(503L)).thenReturn(java.util.Optional.of(usableFile(503L)))
        doAnswer { invocation ->
            val entity = invocation.getArgument<ActivityEntity>(0)
            entity.id = 1013L
            1
        }.`when`(activities).insert(any<ActivityEntity>())
        val request = PersonalActivityCreateRequest(
            "锁序活动", "HIKING", "活动介绍", "503", listOf("502", "501"),
            at(1), at(2), at(3), at(4), "440305", "活动地址", null, null, 20,
            "报名说明", "组织者留言"
        )

        service.createPersonal(PRINCIPAL, request, "create-file-order")

        val order = inOrder(files)
        order.verify(files).lockById(501L)
        order.verify(files).lockById(502L)
        order.verify(files).lockById(503L)
    }

    @Test
    fun allowsReuseOfCreateKeyAfterTwentyFourHours() {
        val saved = arrayOfNulls<ActivityEntity>(2)
        doAnswer { invocation ->
            val entity = invocation.getArgument<ActivityEntity>(0)
            val index = if (saved[0] == null) 0 else 1
            entity.id = if (index == 0) 1006L else 1007L
            saved[index] = entity
            1
        }.`when`(activities).insert(any<ActivityEntity>())
        val existing = arrayOfNulls<ActivityEntity>(1)
        `when`(activities.findByCreateIdempotencyScope(any<String>(), any<String>()))
            .thenAnswer { java.util.Optional.ofNullable(existing[0]) }

        service.createPersonal(PRINCIPAL, personalRequest(), "create-expiring")
        existing[0] = draft(1006L, USER_ID, null)
        existing[0]!!.createIdempotencyFingerprint = saved[0]!!.createIdempotencyFingerprint
        existing[0]!!.createdAt = LocalDateTime.ofInstant(
            NOW.minus(Duration.ofHours(25)), ZoneOffset.UTC
        )
        idempotency.clear()
        `when`(
            activities.clearExpiredCreateIdempotency(
                any<Long>(), any<String>(), any<String>(), any<LocalDateTime>()
            )
        ).thenAnswer {
            existing[0] = null
            1
        }

        val second = service.createPersonal(PRINCIPAL, personalRequest(), "create-expiring")

        assertThat(second.replayed).isFalse()
        assertThat(second.view.activityId).isEqualTo("1007")
        verify(activities, times(2)).insert(any<ActivityEntity>())
    }

    @Test
    fun doesNotDeleteIdempotencyTombstoneWhenCurrentKeyHasNoRecord() {
        doAnswer { invocation ->
            val entity = invocation.getArgument<ActivityEntity>(0)
            entity.id = 1009L
            1
        }.`when`(activities).insert(any<ActivityEntity>())

        val outcome = service.createPersonal(PRINCIPAL, personalRequest(), "create-without-tombstone")

        assertThat(outcome.replayed).isFalse()
        verify(idempotencyTombstones, never()).deleteExpiredByScopeAndKey(
            any<String>(), any<String>(), any<LocalDateTime>()
        )
    }

    @Test
    fun deletesConfirmedExpiredTombstoneBeforeCreatingReplacement() {
        val expired = ActivityCreateIdempotencyTombstoneEntity()
        expired.expiresAt = localNow().minusSeconds(1)
        `when`(idempotencyTombstones.findByScopeAndKey(any<String>(), eq("create-expired-tombstone")))
            .thenReturn(java.util.Optional.of(expired))
        `when`(
            idempotencyTombstones.deleteExpiredByScopeAndKey(
                any<String>(), eq("create-expired-tombstone"), eq(localNow())
            )
        ).thenReturn(1)
        doAnswer { invocation ->
            val entity = invocation.getArgument<ActivityEntity>(0)
            entity.id = 1010L
            1
        }.`when`(activities).insert(any<ActivityEntity>())

        val outcome = service.createPersonal(PRINCIPAL, personalRequest(), "create-expired-tombstone")

        assertThat(outcome.replayed).isFalse()
        assertThat(outcome.view.activityId).isEqualTo("1010")
        verify(idempotencyTombstones).deleteExpiredByScopeAndKey(
            any<String>(), eq("create-expired-tombstone"), eq(localNow())
        )
    }

    @Test
    fun replayDoesNotExtendCreateKeyBeyondOriginalTwentyFourHours() {
        val saved = arrayOfNulls<ActivityEntity>(1)
        doAnswer { invocation ->
            val entity = invocation.getArgument<ActivityEntity>(0)
            entity.id = 1008L
            saved[0] = entity
            1
        }.`when`(activities).insert(any<ActivityEntity>())
        val existing = arrayOfNulls<ActivityEntity>(1)
        `when`(activities.findByCreateIdempotencyScope(any<String>(), any<String>()))
            .thenAnswer { java.util.Optional.ofNullable(existing[0]) }

        service.createPersonal(PRINCIPAL, personalRequest(), "create-ttl")
        existing[0] = draft(1008L, USER_ID, null)
        existing[0]!!.createIdempotencyFingerprint = saved[0]!!.createIdempotencyFingerprint
        existing[0]!!.createdAt = LocalDateTime.ofInstant(
            NOW.minus(Duration.ofHours(23)), ZoneOffset.UTC
        )
        idempotency.clear()

        val replay = service.createPersonal(PRINCIPAL, personalRequest(), "create-ttl")

        assertThat(replay.replayed).isTrue()
        assertThat(rememberedTtls).hasSize(2)
        assertThat(rememberedTtls[1]).isEqualTo(Duration.ofHours(1))
    }

    @Test
    fun distinguishesNullAndLiteralNullInCreateFingerprint() {
        val saved = arrayOfNulls<ActivityEntity>(1)
        doAnswer { invocation ->
            val entity = invocation.getArgument<ActivityEntity>(0)
            entity.id = 1004L
            saved[0] = entity
            1
        }.`when`(activities).insert(any<ActivityEntity>())
        `when`(activities.findByCreateIdempotencyScope(any<String>(), eq("create-null-marker")))
            .thenAnswer { java.util.Optional.ofNullable(saved[0]) }

        service.createPersonal(
            PRINCIPAL, personalRequestWith(null, "报名说明", "组织者留言"), "create-null-marker"
        )

        assertThatThrownBy {
            service.createPersonal(
                PRINCIPAL, personalRequestWith("null", "报名说明", "组织者留言"), "create-null-marker"
            )
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(com.eligo.server.activity.error.ActivityErrorCode.IDEMPOTENCY_KEY_CONFLICT)
    }

    @Test
    fun distinguishesDelimiterBoundariesInCreateFingerprint() {
        val saved = arrayOfNulls<ActivityEntity>(1)
        doAnswer { invocation ->
            val entity = invocation.getArgument<ActivityEntity>(0)
            entity.id = 1005L
            saved[0] = entity
            1
        }.`when`(activities).insert(any<ActivityEntity>())
        `when`(activities.findByCreateIdempotencyScope(any<String>(), eq("create-delimiter")))
            .thenAnswer { java.util.Optional.ofNullable(saved[0]) }

        service.createPersonal(
            PRINCIPAL, personalRequestWith(null, "报名|说明", "留言"), "create-delimiter"
        )

        assertThatThrownBy {
            service.createPersonal(
                PRINCIPAL, personalRequestWith(null, "报名", "说明|留言"), "create-delimiter"
            )
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(com.eligo.server.activity.error.ActivityErrorCode.IDEMPOTENCY_KEY_CONFLICT)
    }

    @Test
    fun publishesCompleteDraftOnceAndRepeatedPublicationIsIdempotent() {
        val cover = usableFile(501L)
        `when`(files.lockById(501L)).thenReturn(java.util.Optional.of(cover))
        val draft = publishableDraft(2001L)
        `when`(activities.lockById(2001L)).thenReturn(
            java.util.Optional.of(draft), java.util.Optional.of(draft)
        )
        `when`(activities.publishById(2001L, localNow())).thenAnswer {
            draft.status = 2
            draft.publishedAt = localNow()
            draft.updatedAt = localNow()
            draft.version = 1
            1
        }

        val first = service.publish(PRINCIPAL, 2001L)
        `when`(completion.isCompleted(USER_ID)).thenReturn(false)
        val second = service.publish(PRINCIPAL, 2001L)

        assertThat(first.status).isEqualTo("PUBLISHED")
        assertThat(first.publishedAt).isEqualTo(NOW)
        assertThat(second.status).isEqualTo("PUBLISHED")
        verify(completion).isCompleted(USER_ID)
        verify(activities).publishById(2001L, localNow())
        verify(events).insert(any<ActivityLifecycleEventEntity>())
    }

    @Test
    fun publishRejectsDraftWithoutCoordinates() {
        val draft = publishableDraft(2013L)
        draft.latitude = null
        draft.longitude = null
        `when`(activities.lockById(2013L)).thenReturn(java.util.Optional.of(draft))

        assertThatThrownBy { service.publish(PRINCIPAL, 2013L) }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(CommonErrorCode.VALIDATION_FAILED)

        verify(activities, never()).publishById(any<Long>(), any<LocalDateTime>())
    }

    @Test
    fun rejectsRepeatedPublicationWhenPublishedActivityHasReachedEnd() {
        val published = publishableDraft(2007L)
        published.status = 2
        published.publishedAt = localNow().minusHours(3)
        published.endsAt = localNow()
        published.version = 1
        `when`(activities.lockById(2007L)).thenReturn(java.util.Optional.of(published))

        assertThatThrownBy { service.publish(PRINCIPAL, 2007L) }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(com.eligo.server.activity.error.ActivityErrorCode.STATUS_CONFLICT)

        verify(activities, never()).publishById(any<Long>(), any<LocalDateTime>())
    }

    @Test
    fun rejectsPublicationWhenDraftHasReachedEnd() {
        val draft = publishableDraft(2009L)
        draft.registrationStartsAt = localNow().minusHours(4)
        draft.registrationEndsAt = localNow().minusHours(3)
        draft.startsAt = localNow().minusHours(2)
        draft.endsAt = localNow()
        `when`(files.lockById(501L)).thenReturn(java.util.Optional.of(usableFile(501L)))
        `when`(activities.lockById(2009L)).thenReturn(java.util.Optional.of(draft))
        `when`(activities.publishById(2009L, localNow())).thenReturn(1)

        assertThatThrownBy { service.publish(PRINCIPAL, 2009L) }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(com.eligo.server.activity.error.ActivityErrorCode.STATUS_CONFLICT)

        verify(activities, never()).publishById(any<Long>(), any<LocalDateTime>())
        verify(events, never()).insert(any<ActivityLifecycleEventEntity>())
    }

    @Test
    fun rejectsPublicationWhenFileLockWaitCrossesActivityEnd() {
        val advancingClock = mock(Clock::class.java)
        `when`(advancingClock.instant()).thenReturn(NOW, NOW.plus(Duration.ofHours(4)))
        service = commandService(advancingClock)
        val draft = publishableDraft(2011L)
        `when`(files.lockById(501L)).thenReturn(java.util.Optional.of(usableFile(501L)))
        `when`(activities.lockById(2011L)).thenReturn(java.util.Optional.of(draft))
        `when`(activities.publishById(any<Long>(), any<LocalDateTime>())).thenReturn(1)

        assertThatThrownBy { service.publish(PRINCIPAL, 2011L) }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(com.eligo.server.activity.error.ActivityErrorCode.STATUS_CONFLICT)

        verify(activities, never()).publishById(any<Long>(), any<LocalDateTime>())
        verify(events, never()).insert(any<ActivityLifecycleEventEntity>())
    }

    @Test
    fun rejectsDraftPublicationWhenProfileBecameIncomplete() {
        `when`(files.lockById(501L)).thenReturn(java.util.Optional.of(usableFile(501L)))
        val draft = publishableDraft(2006L)
        `when`(activities.lockById(2006L)).thenReturn(java.util.Optional.of(draft))
        `when`(completion.isCompleted(USER_ID)).thenReturn(false)

        assertThatThrownBy { service.publish(PRINCIPAL, 2006L) }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(AccountUserFileErrorCode.PROFILE_INCOMPLETE)
        verify(activities, never()).publishById(any<Long>(), any())
        verify(events, never()).insert(any<ActivityLifecycleEventEntity>())
    }

    @Test
    fun rejectsAvatarPurposeWhenPublishingActivity() {
        val cover = usableFile(504L)
        cover.purpose = FileObjectEntity.PURPOSE_AVATAR
        `when`(files.lockById(504L)).thenReturn(java.util.Optional.of(cover))
        val draft = publishableDraft(2002L)
        draft.coverFileId = 504L
        `when`(activities.lockById(2002L)).thenReturn(java.util.Optional.of(draft))

        assertThatThrownBy { service.publish(PRINCIPAL, 2002L) }
            .isInstanceOfSatisfying(BusinessException::class.java) { exception ->
                assertThat(exception.errorCode)
                    .isEqualTo(AccountUserFileErrorCode.FILE_STATE_CONFLICT)
            }
    }

    @Test
    fun cancelsPublishedActivityAndWritesCancellationLifecycleEvent() {
        val published = publishableDraft(2003L)
        published.status = 2
        published.publishedAt = localNow()
        published.version = 1
        published.participantCount = 2
        `when`(activities.lockById(2003L)).thenReturn(java.util.Optional.of(published))
        `when`(activities.cancelById(eq(2003L), eq(2), any<LocalDateTime>(), eq(USER_ID)))
            .thenReturn(1)

        val result = service.cancel(PRINCIPAL, 2003L)

        assertThat(result.status).isEqualTo("CANCELLED")
        assertThat(result.version).isEqualTo(2)
        assertThat(result.participantCount).isZero()
        verify(participationCancellation).terminateActiveParticipations(
            eq(2003L), eq(2), any<LocalDateTime>()
        )
        verify(activities).cancelById(
            eq(2003L), eq(2), any<LocalDateTime>(), eq(USER_ID)
        )
        verify(events).insert(any<ActivityLifecycleEventEntity>())
    }

    @Test
    fun cancellationUsesOneCommandTimeForValidationAndWrites() {
        val advancingClock = mock(Clock::class.java)
        `when`(advancingClock.instant()).thenReturn(NOW, NOW.plusSeconds(1))
        service = commandService(advancingClock)
        val published = publishableDraft(2012L)
        published.status = 2
        published.publishedAt = localNow()
        published.version = 1
        published.participantCount = 2
        `when`(activities.lockById(2012L)).thenReturn(java.util.Optional.of(published))
        `when`(activities.cancelById(2012L, 2, localNow(), USER_ID)).thenReturn(1)

        service.cancel(PRINCIPAL, 2012L)

        verify(participationCancellation).terminateActiveParticipations(2012L, 2, localNow())
        verify(activities).cancelById(2012L, 2, localNow(), USER_ID)
        verify(advancingClock).instant()
    }

    @Test
    fun rejectsCancellationWhenPublishedActivityHasReachedEnd() {
        val published = publishableDraft(2008L)
        published.status = 2
        published.publishedAt = localNow().minusHours(3)
        published.endsAt = localNow()
        published.version = 1
        `when`(activities.lockById(2008L)).thenReturn(java.util.Optional.of(published))
        `when`(activities.cancelById(eq(2008L), eq(2), any<LocalDateTime>(), eq(USER_ID)))
            .thenReturn(1)

        assertThatThrownBy { service.cancel(PRINCIPAL, 2008L) }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(com.eligo.server.activity.error.ActivityErrorCode.STATUS_CONFLICT)

        verify(activities, never()).cancelById(
            eq(2008L), eq(2), any<LocalDateTime>(), eq(USER_ID)
        )
    }

    @Test
    fun rejectsCancellationWhenHiddenActivityHasReachedEnd() {
        val hidden = publishableDraft(2010L)
        hidden.status = 5
        hidden.publishedAt = localNow().minusHours(3)
        hidden.endsAt = localNow()
        hidden.version = 1
        hidden.participantCount = 1
        `when`(activities.lockById(2010L)).thenReturn(java.util.Optional.of(hidden))
        `when`(activities.cancelById(eq(2010L), eq(5), any<LocalDateTime>(), eq(USER_ID)))
            .thenReturn(1)

        assertThatThrownBy { service.cancel(PRINCIPAL, 2010L) }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(com.eligo.server.activity.error.ActivityErrorCode.STATUS_CONFLICT)

        verify(activities, never()).cancelById(
            eq(2010L), eq(5), any<LocalDateTime>(), eq(USER_ID)
        )
        verifyNoInteractions(participationCancellation)
    }

    @Test
    fun cancelsHiddenActivityAndRepeatedCancellationIsIdempotent() {
        val hidden = publishableDraft(2004L)
        hidden.status = 5
        hidden.publishedAt = localNow()
        hidden.version = 4
        `when`(activities.lockById(2004L)).thenReturn(
            java.util.Optional.of(hidden), java.util.Optional.of(hidden)
        )
        `when`(activities.cancelById(eq(2004L), eq(5), any<LocalDateTime>(), eq(USER_ID)))
            .thenReturn(1)

        val first = service.cancel(PRINCIPAL, 2004L)
        val second = service.cancel(PRINCIPAL, 2004L)

        assertThat(first.status).isEqualTo("CANCELLED")
        assertThat(second.status).isEqualTo("CANCELLED")
        verify(activities).cancelById(
            eq(2004L), eq(5), any<LocalDateTime>(), eq(USER_ID)
        )
        verify(events).insert(any<ActivityLifecycleEventEntity>())
    }

    @Test
    fun rejectsCancellationForDraftAndEndedActivity() {
        for (blockedStatus in listOf(1, 4)) {
            val blocked = draft(2005L + blockedStatus, USER_ID, null)
            blocked.status = blockedStatus
            `when`(activities.lockById(blocked.id!!)).thenReturn(java.util.Optional.of(blocked))

            assertThatThrownBy { service.cancel(PRINCIPAL, blocked.id!!) }
                .isInstanceOf(BusinessException::class.java)
                .extracting(Function {  (it as BusinessException).errorCode  })
                .isEqualTo(com.eligo.server.activity.error.ActivityErrorCode.STATUS_CONFLICT)
        }
    }

    @Test
    fun deletesOwnedDraftChildrenAndActivityWithoutTouchingFiles() {
        val draft = draft(2010L, USER_ID, null)
        `when`(activities.lockById(2010L)).thenReturn(java.util.Optional.of(draft))
        `when`(activities.deleteDraftById(2010L)).thenReturn(1)

        service.deleteDraft(PRINCIPAL, 2010L)

        verify(media).deleteByActivityId(2010L)
        verify(events).deleteByActivityId(2010L)
        verify(activities).deleteDraftById(2010L)
        verifyNoInteractions(files)
    }

    @Test
    fun rejectsDraftDeletionForNonDraftOrUnauthorizedActivity() {
        for (blockedStatus in listOf(2, 3, 4, 5)) {
            val activityId = 2010L + blockedStatus
            val blocked = draft(activityId, USER_ID, null)
            blocked.status = blockedStatus
            `when`(activities.lockById(activityId)).thenReturn(java.util.Optional.of(blocked))

            assertThatThrownBy { service.deleteDraft(PRINCIPAL, activityId) }
                .isInstanceOf(BusinessException::class.java)
                .extracting(Function {  (it as BusinessException).errorCode  })
                .isEqualTo(com.eligo.server.activity.error.ActivityErrorCode.STATUS_CONFLICT)
        }

        val otherOwner = draft(2020L, USER_ID + 1, null)
        `when`(activities.lockById(2020L)).thenReturn(java.util.Optional.of(otherOwner))
        assertThatThrownBy { service.deleteDraft(PRINCIPAL, 2020L) }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND)

        for (blockedStatus in listOf(2, 3, 4, 5)) {
            verify(media, never()).deleteByActivityId(2010L + blockedStatus)
        }
        verify(media, never()).deleteByActivityId(2020L)
        verify(activities, never()).deleteDraftById(any<Long>())
    }

    @Test
    fun rejectsPublicationWhenRequiredFieldsAreMissing() {
        val draft = draft(2002L, USER_ID, null)
        `when`(activities.lockById(2002L)).thenReturn(java.util.Optional.of(draft))

        assertThatThrownBy { service.publish(PRINCIPAL, 2002L) }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(CommonErrorCode.VALIDATION_FAILED)
        verify(activities, never()).publishById(any<Long>(), any())
    }

    @Test
    fun updatesPersonalDraftAndReplacesMediaUsingExpectedVersion() {
        val draft = draft(3001L, USER_ID, null)
        draft.version = 2
        `when`(activities.lockById(3001L)).thenReturn(java.util.Optional.of(draft))
        `when`(files.lockById(601L)).thenReturn(java.util.Optional.of(usableFile(601L)))
        `when`(media.findByActivityId(3001L)).thenReturn(listOf(media(601L, 1)))
        `when`(
            activities.updateContentByIdAndVersion(
                any<ActivityEntity>(), eq(2), any<LocalDateTime>()
            )
        ).thenAnswer { invocation ->
            val updated = invocation.getArgument<ActivityEntity>(0)
            updated.version = 3
            updated.updatedAt = invocation.getArgument(2)
            1
        }

        val result = service.updatePersonal(
            PRINCIPAL, 3001L,
            PersonalActivityUpdateRequest(
                2, "更新标题", "CAMPING", "更新介绍", null, listOf("601"),
                at(5), at(6), at(6), at(7), "440305", "更新地址",
                capacity = 30, signupDetails = "新报名说明", organizerMessage = "新组织者留言"
            )
        )

        assertThat(result.status).isEqualTo("DRAFT")
        assertThat(result.title).isEqualTo("更新标题")
        assertThat(result.version).isEqualTo(3)
        assertThat(result.signupDetails).isEqualTo("新报名说明")
        assertThat(result.media).extracting(Function {  it.fileId  }).containsExactly("601")
        verify(media).deleteByActivityId(3001L)
        verify(media).insert(any<ActivityMediaEntity>())
    }

    @Test
    fun updatesPublishedPersonalActivityAndPreservesPublishedStatus() {
        val published = publishableDraft(3002L)
        published.status = 2
        published.publishedAt = localNow()
        published.version = 4
        `when`(activities.lockById(3002L)).thenReturn(java.util.Optional.of(published))
        `when`(files.lockById(602L)).thenReturn(java.util.Optional.of(usableFile(602L)))
        `when`(media.findByActivityId(3002L)).thenReturn(listOf())
        `when`(
            activities.updateContentByIdAndVersion(
                any<ActivityEntity>(), eq(4), any<LocalDateTime>()
            )
        ).thenAnswer { invocation ->
            val updated = invocation.getArgument<ActivityEntity>(0)
            updated.version = 5
            updated.updatedAt = invocation.getArgument(2)
            1
        }

        val result = service.updatePersonal(
            PRINCIPAL, 3002L,
            PersonalActivityUpdateRequest(
                4, "已发布更新", "HIKING", "完整活动介绍", "602", listOf<String>(),
                at(1), at(2), at(2), at(3), "440305", "深圳市南山区活动地址",
                BigDecimal("22.5430960"), BigDecimal("114.0578650"),
                20, "报名说明", "组织者留言",
                "UNLIMITED", null, null, null, null, "市民中心东门"
            )
        )

        assertThat(result.status).isEqualTo("PUBLISHED")
        assertThat(result.version).isEqualTo(5)
        verify(events, never()).insert(any<ActivityLifecycleEventEntity>())
    }

    @Test
    fun publishedUpdateRejectsClearingCoordinates() {
        val published = publishableDraft(3010L)
        published.status = 2
        published.publishedAt = localNow()
        published.version = 4
        `when`(activities.lockById(3010L)).thenReturn(java.util.Optional.of(published))
        `when`(files.lockById(602L)).thenReturn(java.util.Optional.of(usableFile(602L)))

        assertThatThrownBy {
            service.updatePersonal(
                PRINCIPAL, 3010L,
                PersonalActivityUpdateRequest(
                    4, "已发布更新", "HIKING", "完整活动介绍", "602", listOf<String>(),
                    at(1), at(2), at(2), at(3), "440305", "深圳市南山区活动地址",
                    null, null, 20, "报名说明", "组织者留言"
                )
            )
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(CommonErrorCode.VALIDATION_FAILED)

        verify(activities, never()).updateContentByIdAndVersion(
            any<ActivityEntity>(), eq(4), any<LocalDateTime>()
        )
    }

    @Test
    fun publishedUpdateRejectsClearingPlaceName() {
        val published = publishableDraft(3011L)
        published.status = 2
        published.publishedAt = localNow()
        published.version = 4
        `when`(activities.lockById(3011L)).thenReturn(java.util.Optional.of(published))
        `when`(files.lockById(602L)).thenReturn(java.util.Optional.of(usableFile(602L)))

        assertThatThrownBy {
            service.updatePersonal(
                PRINCIPAL, 3011L,
                PersonalActivityUpdateRequest(
                    4, "已发布更新", "HIKING", "完整活动介绍", "602", listOf<String>(),
                    at(1), at(2), at(2), at(3), "440305", "深圳市南山区活动地址",
                    BigDecimal("22.5430960"), BigDecimal("114.0578650"),
                    20, "报名说明", "组织者留言",
                    "UNLIMITED", null, null, null, null, null
                )
            )
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(CommonErrorCode.VALIDATION_FAILED)

        verify(activities, never()).updateContentByIdAndVersion(
            any<ActivityEntity>(), eq(4), any<LocalDateTime>()
        )
    }

    @Test
    fun rejectsUpdateThatWouldMoveEndedPublishedActivityBackIntoFuture() {
        val published = publishableDraft(3007L)
        published.status = 2
        published.publishedAt = localNow().minusHours(3)
        published.endsAt = localNow()
        published.version = 4
        `when`(activities.lockById(3007L)).thenReturn(java.util.Optional.of(published))
        `when`(files.lockById(602L)).thenReturn(java.util.Optional.of(usableFile(602L)))
        `when`(
            activities.updateContentByIdAndVersion(
                any<ActivityEntity>(), eq(4), any<LocalDateTime>()
            )
        ).thenReturn(1)

        assertThatThrownBy {
            service.updatePersonal(
                PRINCIPAL, 3007L,
                PersonalActivityUpdateRequest(
                    4, "结束后更新", "HIKING", "完整活动介绍", "602", listOf<String>(),
                    at(1), at(2), at(2), at(3), "440305", "深圳市南山区活动地址",
                    BigDecimal("22.5430960"), BigDecimal("114.0578650"),
                    20, "报名说明", "组织者留言",
                    "UNLIMITED", null, null, null, null, "市民中心东门"
                )
            )
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(com.eligo.server.activity.error.ActivityErrorCode.STATUS_CONFLICT)

        verify(activities, never()).updateContentByIdAndVersion(
            any<ActivityEntity>(), eq(4), any<LocalDateTime>()
        )
    }

    @Test
    fun rejectsPublishedPersonalUpdateWithReplacementEndAlreadyReached() {
        val published = publishableDraft(3008L)
        published.status = 2
        published.publishedAt = localNow().minusHours(3)
        published.version = 4
        `when`(activities.lockById(3008L)).thenReturn(java.util.Optional.of(published))
        `when`(files.lockById(602L)).thenReturn(java.util.Optional.of(usableFile(602L)))
        `when`(
            activities.updateContentByIdAndVersion(
                any<ActivityEntity>(), eq(4), any<LocalDateTime>()
            )
        ).thenReturn(1)

        assertThatThrownBy {
            service.updatePersonal(
                PRINCIPAL, 3008L,
                PersonalActivityUpdateRequest(
                    4, "已到期替换内容", "HIKING", "完整活动介绍", "602", listOf<String>(),
                    at(-4), at(-3), at(-2), at(-1), "440305", "深圳市南山区活动地址",
                    BigDecimal("22.5430960"), BigDecimal("114.0578650"),
                    20, "报名说明", "组织者留言",
                    "UNLIMITED", null, null, null, null, "市民中心东门"
                )
            )
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(com.eligo.server.activity.error.ActivityErrorCode.STATUS_CONFLICT)

        verify(activities, never()).updateContentByIdAndVersion(
            any<ActivityEntity>(), eq(4), any<LocalDateTime>()
        )
    }

    @Test
    fun rejectsPublishedOrganizationUpdateWithReplacementEndAlreadyReached() {
        val organization = organization()
        `when`(organizations.findActiveOwnedByUserId(USER_ID)).thenReturn(listOf(organization))
        `when`(organizations.selectById(ORGANIZATION_ID)).thenReturn(organization)
        val published = publishableDraft(3009L)
        published.ownerUserId = null
        published.ownerOrganizationId = ORGANIZATION_ID
        published.signupDetails = null
        published.organizerMessage = null
        published.status = 2
        published.publishedAt = localNow().minusHours(3)
        published.version = 4
        `when`(activities.lockById(3009L)).thenReturn(java.util.Optional.of(published))
        `when`(files.lockById(602L)).thenReturn(java.util.Optional.of(usableFile(602L)))
        `when`(
            activities.updateContentByIdAndVersion(
                any<ActivityEntity>(), eq(4), any<LocalDateTime>()
            )
        ).thenReturn(1)

        assertThatThrownBy {
            service.updateOrganization(
                PRINCIPAL, ORGANIZATION_ID, 3009L,
                OrganizationActivityUpdateRequest(
                    4, "企业已到期替换内容", "HIKING", "完整活动介绍", "602", listOf<String>(),
                    at(-4), at(-3), at(-2), at(-1), "440305", "深圳市南山区活动地址",
                    BigDecimal("22.5430960"), BigDecimal("114.0578650"),
                    20, "UNLIMITED", null, null, null, null, "市民中心东门"
                )
            )
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(com.eligo.server.activity.error.ActivityErrorCode.STATUS_CONFLICT)

        verify(activities, never()).updateContentByIdAndVersion(
            any<ActivityEntity>(), eq(4), any<LocalDateTime>()
        )
    }

    @Test
    fun updatesOrganizationActivityWithoutPersonalFields() {
        val organization = organization()
        `when`(organizations.findActiveOwnedByUserId(USER_ID)).thenReturn(listOf(organization))
        `when`(organizations.selectById(ORGANIZATION_ID)).thenReturn(organization)
        val activity = draft(3003L, null, ORGANIZATION_ID)
        activity.version = 1
        `when`(activities.lockById(3003L)).thenReturn(java.util.Optional.of(activity))
        `when`(media.findByActivityId(3003L)).thenReturn(listOf())
        `when`(
            activities.updateContentByIdAndVersion(
                any<ActivityEntity>(), eq(1), any<LocalDateTime>()
            )
        ).thenAnswer { invocation ->
            val updated = invocation.getArgument<ActivityEntity>(0)
            updated.version = 2
            updated.updatedAt = invocation.getArgument(2)
            1
        }

        val result = service.updateOrganization(
            PRINCIPAL, ORGANIZATION_ID, 3003L,
            OrganizationActivityUpdateRequest(
                1, "企业活动更新", "CAMPING", "企业活动介绍", null, listOf<String>(),
                null, null, null, null, null, null, null, null
            )
        )

        assertThat(result.status).isEqualTo("DRAFT")
        assertThat(result.owner.ownerType).isEqualTo("ORGANIZATION")
        assertThat(result.version).isEqualTo(2)
        assertThat(result.signupDetails).isNull()
        assertThat(result.organizerMessage).isNull()
        verify(activities).updateContentByIdAndVersion(
            org.mockito.kotlin.argThat<ActivityEntity> { entity ->
                entity.operatorUserId == USER_ID
            },
            eq(1), any<LocalDateTime>()
        )
    }

    @Test
    fun rejectsVersionConflictAndForbiddenOrIncompleteStatusesBeforeWriting() {
        val stale = draft(3004L, USER_ID, null)
        stale.version = 2
        `when`(activities.lockById(3004L)).thenReturn(java.util.Optional.of(stale))
        `when`(
            activities.updateContentByIdAndVersion(
                any<ActivityEntity>(), eq(2), any<LocalDateTime>()
            )
        ).thenReturn(0)

        assertThatThrownBy {
            service.updatePersonal(
                PRINCIPAL, 3004L,
                PersonalActivityUpdateRequest(
                    2, "更新", null, null, null, listOf<String>(),
                    null, null, null, null, null, null,
                    capacity = null, signupDetails = null, organizerMessage = null
                )
            )
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(com.eligo.server.activity.error.ActivityErrorCode.VERSION_CONFLICT)
        verify(media, never()).deleteByActivityId(3004L)

        val ended = draft(3005L, USER_ID, null)
        ended.status = 4
        ended.publishedAt = localNow()
        `when`(activities.lockById(3005L)).thenReturn(java.util.Optional.of(ended))
        assertThatThrownBy {
            service.updatePersonal(
                PRINCIPAL, 3005L,
                PersonalActivityUpdateRequest(
                    0, "更新", null, null, null, listOf<String>(),
                    null, null, null, null, null, null,
                    capacity = null, signupDetails = null, organizerMessage = null
                )
            )
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(com.eligo.server.activity.error.ActivityErrorCode.STATUS_CONFLICT)

        for (blockedStatus in listOf(3, 5)) {
            val blocked = draft(3005L + blockedStatus, USER_ID, null)
            blocked.status = blockedStatus
            `when`(activities.lockById(blocked.id!!)).thenReturn(java.util.Optional.of(blocked))
            assertThatThrownBy {
                service.updatePersonal(
                    PRINCIPAL, blocked.id!!,
                    PersonalActivityUpdateRequest(
                        0, "更新", null, null, null, listOf<String>(),
                        null, null, null, null, null, null,
                        capacity = null, signupDetails = null, organizerMessage = null
                    )
                )
            }
                .isInstanceOf(BusinessException::class.java)
                .extracting(Function {  (it as BusinessException).errorCode  })
                .isEqualTo(com.eligo.server.activity.error.ActivityErrorCode.STATUS_CONFLICT)
        }

        val incompletePublished = draft(3006L, USER_ID, null)
        incompletePublished.status = 2
        incompletePublished.publishedAt = localNow()
        `when`(activities.lockById(3006L)).thenReturn(java.util.Optional.of(incompletePublished))
        assertThatThrownBy {
            service.updatePersonal(
                PRINCIPAL, 3006L,
                PersonalActivityUpdateRequest(
                    0, "更新", null, null, null, listOf<String>(),
                    null, null, null, null, null, null,
                    capacity = null, signupDetails = null, organizerMessage = null
                )
            )
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(CommonErrorCode.VALIDATION_FAILED)
    }

    private fun personalRequest(): PersonalActivityCreateRequest {
        return personalRequestWith("活动介绍", "报名说明", "组织者留言")
    }

    private fun personalRequestWithCoordinates(
        latitude: BigDecimal?,
        longitude: BigDecimal?
    ): PersonalActivityCreateRequest {
        return PersonalActivityCreateRequest(
            "个人草稿", "HIKING", "活动介绍", null, listOf<String>(),
            at(1), at(2), at(3), at(4), "440305", "深圳市南山区活动地址",
            latitude, longitude, 20, "报名说明", "组织者留言"
        )
    }

    private fun personalRequestWithCoordinateSystem(
        latitude: BigDecimal?,
        longitude: BigDecimal?,
        coordinateSystem: String
    ): PersonalActivityCreateRequest {
        return PersonalActivityCreateRequest(
            "个人草稿", "HIKING", "活动介绍", null, listOf<String>(),
            at(1), at(2), at(3), at(4), "440305", "深圳市南山区活动地址",
            latitude, longitude, 20, "报名说明", "组织者留言",
            "UNLIMITED", null, null, null, null, "市民中心东门", coordinateSystem
        )
    }

    private fun personalRequestWithPlaceName(placeName: String?): PersonalActivityCreateRequest {
        return PersonalActivityCreateRequest(
            "个人草稿", "HIKING", "活动介绍", null, listOf<String>(),
            at(1), at(2), at(3), at(4), "440305", "深圳市南山区活动地址",
            null, null, 20, "报名说明", "组织者留言",
            "UNLIMITED", null, null, null, null, placeName
        )
    }

    private fun personalRequestWithTopics(topics: List<String>?): PersonalActivityCreateRequest {
        return PersonalActivityCreateRequest(
            "个人草稿", "HIKING", "活动介绍", null, listOf<String>(),
            at(1), at(2), at(3), at(4), "440305", "深圳市南山区活动地址",
            null, null, 20, "报名说明", "组织者留言",
            "UNLIMITED", null, null, null, null, null, null, topics
        )
    }

    private fun personalRequestWith(
        description: String?,
        signupDetails: String?,
        organizerMessage: String?
    ): PersonalActivityCreateRequest {
        return PersonalActivityCreateRequest(
            " 个人草稿 ", "HIKING", description, null, listOf<String>(),
            at(1), at(2), at(3), at(4), "440305", "深圳市南山区活动地址",
            null, null, 20, signupDetails, organizerMessage
        )
    }

    private fun personalRequestWithTitle(title: String): PersonalActivityCreateRequest {
        return PersonalActivityCreateRequest(
            title, "HIKING", "活动介绍", null, listOf<String>(),
            at(1), at(2), at(3), at(4), "440305", "深圳市南山区活动地址",
            null, null, 20, "报名说明", "组织者留言"
        )
    }

    private fun organizationRequest(): OrganizationActivityCreateRequest {
        return organizationRequestWithTitle("企业草稿")
    }

    private fun organizationRequestWithTitle(title: String): OrganizationActivityCreateRequest {
        return OrganizationActivityCreateRequest(
            title, null, null, null, listOf<String>(),
            null, null, null, null, null, null, null, null
        )
    }

    private fun draft(id: Long, ownerUserId: Long?, ownerOrganizationId: Long?): ActivityEntity {
        val entity = ActivityEntity()
        entity.id = id
        entity.ownerUserId = ownerUserId
        entity.ownerOrganizationId = ownerOrganizationId
        entity.operatorUserId = USER_ID
        entity.status = 1
        entity.title = "草稿"
        entity.participantCount = 0
        entity.version = 0
        entity.createdAt = localNow()
        entity.updatedAt = localNow()
        return entity
    }

    private fun legacyCoordinateFreeDraft(id: Long, idempotencyKey: String): ActivityEntity {
        val entity = draft(id, USER_ID, null)
        entity.title = "个人草稿"
        entity.createIdempotencyScope = "USER:$USER_ID:$USER_ID"
        entity.createIdempotencyKey = idempotencyKey
        entity.createIdempotencyFingerprint = LEGACY_COORDINATE_FREE_FINGERPRINT
        return entity
    }

    private fun publishableDraft(id: Long): ActivityEntity {
        val entity = draft(id, USER_ID, null)
        entity.title = "可发布活动"
        entity.categoryCode = "HIKING"
        entity.description = "完整活动介绍"
        entity.coverFileId = 501L
        entity.registrationStartsAt = localNow().plusHours(1)
        entity.registrationEndsAt = localNow().plusHours(2)
        entity.startsAt = localNow().plusHours(2)
        entity.endsAt = localNow().plusHours(3)
        entity.regionCode = "440305"
        entity.addressDetail = "深圳市南山区活动地址"
        entity.placeName = "市民中心东门"
        entity.latitude = BigDecimal("22.5430960")
        entity.longitude = BigDecimal("114.0578650")
        entity.capacity = 20
        return entity
    }

    private fun media(fileId: Long, sortOrder: Int): ActivityMediaEntity {
        val entity = ActivityMediaEntity()
        entity.activityId = 3001L
        entity.fileId = fileId
        entity.sortOrder = sortOrder
        return entity
    }

    private fun profile(): UserProfileEntity {
        val profile = UserProfileEntity()
        profile.userId = USER_ID
        profile.nickname = "测试用户"
        profile.avatarFileId = 502L
        return profile
    }

    private fun organization(): OrganizationEntity {
        val organization = OrganizationEntity()
        organization.id = ORGANIZATION_ID
        organization.name = "山海户外"
        organization.avatarFileId = 503L
        organization.status = 1
        return organization
    }

    private fun usableFile(fileId: Long): FileObjectEntity {
        val file = FileObjectEntity()
        file.id = fileId
        file.uploaderType = FileObjectEntity.UPLOADER_USER
        file.uploaderId = USER_ID
        file.accessLevel = FileObjectEntity.ACCESS_PUBLIC
        file.purpose = FileObjectEntity.PURPOSE_ACTIVITY
        file.scanStatus = FileObjectEntity.SCAN_PASSED
        file.lifecycleStatus = FileObjectEntity.LIFECYCLE_ACTIVE
        file.version = 0
        return file
    }

    private fun localNow(): LocalDateTime {
        return LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)
    }

    private fun commandService(commandClock: Clock): DefaultActivityCommandService {
        return DefaultActivityCommandService(
            activities, idempotencyTombstones, media, events, organizations,
            profiles, files, completion, regions, redis,
            participationCancellation, accountStates, clock = commandClock
        )
    }

    private fun at(hoursFromNow: Long): Instant {
        return NOW.plusSeconds(hoursFromNow * 3600)
    }

    companion object {
        private const val USER_ID = 202L
        private const val ORGANIZATION_ID = 401L
        private val NOW = Instant.parse("2026-08-07T00:00:00Z")
        private val PRINCIPAL = UserPrincipal(USER_ID, "session-a")
        private const val LEGACY_COORDINATE_FREE_FINGERPRINT =
            "aGpTfAHKrgqVZGuph0RllaVy6wU4gqoVLue-k8RGKkk"
    }
}
