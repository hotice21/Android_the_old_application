package com.eligo.server.activity

import java.util.function.Function
import java.util.function.Consumer

import com.eligo.server.account.service.AccountDeactivationBlocker
import com.eligo.server.account.service.AccountStateLockService
import com.eligo.server.activity.dto.OrganizationActivityCreateRequest
import com.eligo.server.activity.dto.OrganizationActivityUpdateRequest
import com.eligo.server.activity.dto.PersonalActivityCreateRequest
import com.eligo.server.activity.dto.PersonalActivityUpdateRequest
import com.eligo.server.activity.entity.ActivityCreateIdempotencyTombstoneEntity
import com.eligo.server.activity.error.ActivityErrorCode
import com.eligo.server.activity.mapper.ActivityCreateIdempotencyTombstoneMapper
import com.eligo.server.activity.mapper.ActivityLifecycleEventMapper
import com.eligo.server.activity.mapper.ActivityMapper
import com.eligo.server.activity.mapper.ActivityMediaMapper
import com.eligo.server.activity.service.ActivityCommandService
import com.eligo.server.activity.service.ActivityCreateOutcome
import com.eligo.server.activity.service.DefaultActivityCommandService
import com.eligo.server.activity.vo.ManagedActivityDetailView
import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.database.Stage2TestDatabaseCleaner
import com.eligo.server.file.mapper.FileObjectMapper
import com.eligo.server.integration.wechat.WechatRestClientFactory
import com.eligo.server.organization.mapper.OrganizationMapper
import com.eligo.server.participation.service.ParticipationActivityCancellationService
import com.eligo.server.profile.mapper.UserProfileMapper
import com.eligo.server.profile.service.ProfileCompletionReader
import com.eligo.server.profile.service.RegionCatalog
import com.eligo.server.security.UserPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.mockito.AdditionalAnswers.delegatesTo
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.stubbing.Answer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.sql.Connection
import java.sql.PreparedStatement
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@SpringBootTest(
    properties = [
        "eligo.security.jwt.signing-key-base64=IB8eHRwbGhkYFxYVFBMSERAPDg0MCwoJCAcGBQQDAgE=",
        "eligo.security.sensitive-data.encryption-key-base64=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
        "eligo.security.sensitive-data.lookup-key-base64=Hx4dHBsaGRgXFhUUExIREA8ODQwLCgkIBwYFBAMCAQA="
    ]
)
@ActiveProfiles("it")
@EnabledIfSystemProperty(named = "eligo.database.tests", matches = "true")
class ActivityCommandDatabaseIntegrationTests {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var dataSource: DataSource

    @Autowired
    private lateinit var service: ActivityCommandService

    @Autowired
    private lateinit var activities: ActivityMapper

    @Autowired
    private lateinit var idempotencyTombstones: ActivityCreateIdempotencyTombstoneMapper

    @Autowired
    private lateinit var media: ActivityMediaMapper

    @Autowired
    private lateinit var events: ActivityLifecycleEventMapper

    @Autowired
    private lateinit var participationCancellation: ParticipationActivityCancellationService

    @Autowired
    private lateinit var organizations: OrganizationMapper

    @Autowired
    private lateinit var profiles: UserProfileMapper

    @Autowired
    private lateinit var files: FileObjectMapper

    @Autowired
    private lateinit var completion: ProfileCompletionReader

    @Autowired
    private lateinit var regions: RegionCatalog

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var deactivationBlockers: List<AccountDeactivationBlocker>

    @Autowired
    private lateinit var accountStates: AccountStateLockService

    @MockitoBean
    private lateinit var redis: StringRedisTemplate

    @MockitoBean
    private lateinit var wechatRestClientFactory: WechatRestClientFactory

    private val values = mock(ValueOperations::class.java) as ValueOperations<String, String>
    private val idempotency = ConcurrentHashMap<String, String>()

    @BeforeEach
    fun prepareDatabase() {
        Stage2TestDatabaseCleaner.cleanAllStage2Tables(jdbcTemplate)
        `when`(redis.opsForValue()).thenReturn(values)
        `when`(values.get(any<String>())).thenAnswer { invocation ->
            idempotency[invocation.getArgument(0)]
        }
        doAnswer { invocation ->
            idempotency[invocation.getArgument(0)] = invocation.getArgument(1)
            null
        }.`when`(values).set(any<String>(), any<String>(), any<java.time.Duration>())
        insertUser(USER_ID)
        insertUser(OTHER_USER_ID)
        insertProfile(USER_ID)
        insertProfile(OTHER_USER_ID)
        insertOrganization()
        insertOwnerRelation()
        insertTemporaryFile(COVER_FILE_ID)
        insertTemporaryFile(DELETE_MEDIA_FILE_ID)
    }

    @Test
    fun personalDraftPublishesAndWritesExactlyTwoLifecycleEvents() {
        val principal = UserPrincipal(USER_ID, "activity-command-it")
        val created = service.createPersonal(
            principal,
            PersonalActivityCreateRequest(
                "可发布活动", "HIKING", "活动介绍",
                COVER_FILE_ID.toString(), listOf<String>(),
                activeAt(1), activeAt(2), activeAt(2), activeAt(3),
                "440305", "深圳市南山区活动地址",
                LOCATION_LATITUDE, LOCATION_LONGITUDE,
                20, "报名说明", "组织者留言"
            ),
            "activity-command-personal"
        )

        val activityId = created.view.activityId.toLong()
        assertThat(created.view.status).isEqualTo("DRAFT")
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT lifecycle_status FROM file_objects WHERE id=?",
                Int::class.java, COVER_FILE_ID
            )
        ).isEqualTo(2)

        val published = service.publish(principal, activityId)

        assertThat(published.status).isEqualTo("PUBLISHED")
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM activities WHERE id=?",
                Int::class.java, activityId
            )
        ).isEqualTo(2)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT latitude FROM activities WHERE id=?",
                BigDecimal::class.java, activityId
            )
        ).isEqualByComparingTo(LOCATION_LATITUDE)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT longitude FROM activities WHERE id=?",
                BigDecimal::class.java, activityId
            )
        ).isEqualByComparingTo(LOCATION_LONGITUDE)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_lifecycle_events WHERE activity_id=?",
                Int::class.java, activityId
            )
        ).isEqualTo(2)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_lifecycle_events " +
                    "WHERE activity_id=? AND from_status=1 AND to_status=2",
                Int::class.java, activityId
            )
        ).isEqualTo(1)
    }

    @Test
    fun publishedPersonalActivityBlocksOwnerDeactivation() {
        val principal = UserPrincipal(USER_ID, "activity-blocker-it")
        val created = service.createPersonal(
            principal,
            PersonalActivityCreateRequest(
                "注销阻断活动", "HIKING", "活动介绍",
                COVER_FILE_ID.toString(), listOf<String>(),
                activeAt(1), activeAt(2), activeAt(2), activeAt(3),
                "440305", "深圳市南山区活动地址",
                LOCATION_LATITUDE, LOCATION_LONGITUDE,
                20, "报名说明", "组织者留言"
            ),
            "activity-blocker-personal"
        )
        service.publish(principal, created.view.activityId.toLong())

        assertThat(blockingReasons(USER_ID)).contains("存在进行中的发起活动")
    }

    @Test
    fun publishedOrganizationActivityBlocksCurrentOwnerDeactivation() {
        val principal = UserPrincipal(USER_ID, "organization-blocker-it")
        val created = service.createOrganization(
            principal,
            ORGANIZATION_ID,
            OrganizationActivityCreateRequest(
                "企业注销阻断活动", "HIKING", "企业活动介绍",
                COVER_FILE_ID.toString(), listOf<String>(),
                activeAt(1), activeAt(2), activeAt(2), activeAt(3),
                "440305", "深圳市南山区企业活动地址",
                LOCATION_LATITUDE, LOCATION_LONGITUDE, 20
            ),
            "activity-blocker-organization"
        )
        service.publish(principal, created.view.activityId.toLong())

        assertThat(blockingReasons(USER_ID)).contains("存在进行中的发起活动")
    }

    @Test
    fun activityLifecycleBoundsOwnerDeactivationBlocker() {
        val principal = UserPrincipal(USER_ID, "activity-blocker-lifecycle-it")
        val created = service.createPersonal(
            principal,
            PersonalActivityCreateRequest(
                "注销阻断生命周期", "HIKING", "活动介绍",
                COVER_FILE_ID.toString(), listOf<String>(),
                activeAt(1), activeAt(2), activeAt(2), activeAt(3),
                "440305", "深圳市南山区活动地址",
                LOCATION_LATITUDE, LOCATION_LONGITUDE,
                20, "报名说明", "组织者留言"
            ),
            "activity-blocker-lifecycle"
        )
        val activityId = created.view.activityId.toLong()

        assertThat(blockingReasons(USER_ID)).doesNotContain("存在进行中的发起活动")

        service.publish(principal, activityId)
        assertThat(blockingReasons(USER_ID)).contains("存在进行中的发起活动")

        jdbcTemplate.update("UPDATE activities SET status=5 WHERE id=?", activityId)
        assertThat(blockingReasons(USER_ID)).contains("存在进行中的发起活动")

        jdbcTemplate.update("UPDATE activities SET status=3 WHERE id=?", activityId)
        assertThat(blockingReasons(USER_ID)).doesNotContain("存在进行中的发起活动")

        jdbcTemplate.update(
            """
                UPDATE activities
                SET status=2,
                    registration_starts_at=DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 4 HOUR),
                    registration_ends_at=DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 3 HOUR),
                    starts_at=DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 2 HOUR),
                    ends_at=UTC_TIMESTAMP(3)
                WHERE id=?
                """, activityId
        )
        assertThat(blockingReasons(USER_ID)).doesNotContain("存在进行中的发起活动")

        jdbcTemplate.update("UPDATE activities SET status=4 WHERE id=?", activityId)
        assertThat(blockingReasons(USER_ID)).doesNotContain("存在进行中的发起活动")
    }

    @Test
    fun oppositeFileOrdersWaitOnTheSameSmallestFileWithoutDeadlock() {
        insertTemporaryFile(UPDATE_COVER_FILE_ID)
        insertTemporaryFile(UPDATE_MEDIA_FILE_ID)
        val principal = UserPrincipal(USER_ID, "file-lock-order-it")
        val descending = PersonalActivityCreateRequest(
            "反序文件活动甲", "HIKING", "活动介绍",
            UPDATE_MEDIA_FILE_ID.toString(),
            listOf(UPDATE_COVER_FILE_ID.toString(), COVER_FILE_ID.toString()),
            activeAt(1), activeAt(2), activeAt(2), activeAt(3),
            "440305", "深圳市南山区活动地址",
            capacity = 20, signupDetails = "报名说明", organizerMessage = "组织者留言"
        )
        val ascending = PersonalActivityCreateRequest(
            "反序文件活动乙", "HIKING", "活动介绍",
            COVER_FILE_ID.toString(),
            listOf(UPDATE_COVER_FILE_ID.toString(), UPDATE_MEDIA_FILE_ID.toString()),
            activeAt(1), activeAt(2), activeAt(2), activeAt(3),
            "440305", "深圳市南山区活动地址",
            capacity = 20, signupDetails = "报名说明", organizerMessage = "组织者留言"
        )

        val executor = Executors.newFixedThreadPool(2)
        dataSource.connection.use { smallestFileBlocker ->
            smallestFileBlocker.autoCommit = false
            lockFile(smallestFileBlocker, COVER_FILE_ID)
            var released = false
            try {
                val first = executor.submit<ActivityCreateOutcome> {
                    service.createPersonal(principal, descending, "opposite-file-order-a")
                }
                val second = executor.submit<ActivityCreateOutcome> {
                    service.createPersonal(principal, ascending, "opposite-file-order-b")
                }
                awaitDatabaseLockWaitCount("file_objects", COVER_FILE_ID, 2)

                assertThat(grantedFileRecordLocks(UPDATE_MEDIA_FILE_ID)).isZero()
                smallestFileBlocker.commit()
                released = true

                val firstOutcome = first[20, TimeUnit.SECONDS]
                val secondOutcome = second[20, TimeUnit.SECONDS]
                assertThat(firstOutcome.replayed).isFalse()
                assertThat(secondOutcome.replayed).isFalse()
                assertThat(firstOutcome.view.activityId)
                    .isNotEqualTo(secondOutcome.view.activityId)
            } finally {
                if (!released) {
                    smallestFileBlocker.rollback()
                }
            }
        }
        executor.shutdownNow()
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()

        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activities", Int::class.java
            )
        ).isEqualTo(2)
    }

    @Test
    fun organizationActivityRequiresActiveOrganizationAndOwnerRelation() {
        val principal = UserPrincipal(USER_ID, "organization-blocker-disabled-it")
        val created = service.createOrganization(
            principal,
            ORGANIZATION_ID,
            OrganizationActivityCreateRequest(
                "企业负责人停用活动", "HIKING", "企业活动介绍",
                COVER_FILE_ID.toString(), listOf<String>(),
                activeAt(1), activeAt(2), activeAt(2), activeAt(3),
                "440305", "深圳市南山区企业活动地址",
                LOCATION_LATITUDE, LOCATION_LONGITUDE, 20
            ),
            "activity-blocker-disabled-owner"
        )
        service.publish(principal, created.view.activityId.toLong())
        assertThat(blockingReasons(USER_ID)).contains("存在进行中的发起活动")

        jdbcTemplate.update("UPDATE organizations SET status=2 WHERE id=?", ORGANIZATION_ID)
        assertThat(blockingReasons(USER_ID)).doesNotContain("存在进行中的发起活动")

        jdbcTemplate.update("UPDATE organizations SET status=1 WHERE id=?", ORGANIZATION_ID)
        assertThat(blockingReasons(USER_ID)).contains("存在进行中的发起活动")

        jdbcTemplate.update(
            """
                UPDATE organization_members
                SET status=2, disabled_at=UTC_TIMESTAMP(3)
                WHERE id=?
                """, ORGANIZATION_MEMBER_ID
        )

        assertThat(blockingReasons(USER_ID)).doesNotContain("存在进行中的发起活动")
    }

    @Test
    fun profileBecomingIncompleteKeepsDraftUnpublished() {
        val principal = UserPrincipal(USER_ID, "activity-profile-it")
        val created = service.createPersonal(
            principal,
            PersonalActivityCreateRequest(
                "资料失效草稿", "HIKING", "活动介绍",
                COVER_FILE_ID.toString(), listOf<String>(),
                activeAt(1), activeAt(2), activeAt(2), activeAt(3),
                "440305", "深圳市南山区活动地址",
                LOCATION_LATITUDE, LOCATION_LONGITUDE,
                20, "报名说明", "组织者留言"
            ),
            "activity-profile-create"
        )
        val activityId = created.view.activityId.toLong()
        jdbcTemplate.update(
            "UPDATE user_profiles SET completed_at=NULL WHERE user_id=?",
            USER_ID
        )

        assertThatThrownBy { service.publish(principal, activityId) }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(AccountUserFileErrorCode.PROFILE_INCOMPLETE)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM activities WHERE id=?",
                Int::class.java, activityId
            )
        ).isEqualTo(1)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_lifecycle_events WHERE activity_id=?",
                Int::class.java, activityId
            )
        ).isEqualTo(1)
    }

    @Test
    fun cancellationMovesPublishedActivityOnceAndIsIdempotent() {
        val principal = UserPrincipal(USER_ID, "activity-cancellation-it")
        val otherPrincipal = UserPrincipal(OTHER_USER_ID, "activity-cancellation-other-it")
        val created = service.createPersonal(
            principal,
            PersonalActivityCreateRequest(
                "可取消活动", "HIKING", "活动介绍",
                COVER_FILE_ID.toString(), listOf<String>(),
                activeAt(1), activeAt(2), activeAt(2), activeAt(3),
                "440305", "深圳市南山区活动地址",
                LOCATION_LATITUDE, LOCATION_LONGITUDE,
                20, "报名说明", "组织者留言"
            ),
            "activity-cancellation-create"
        )
        val activityId = created.view.activityId.toLong()
        service.publish(principal, activityId)

        assertThatThrownBy { service.cancel(otherPrincipal, activityId) }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND)

        val first = service.cancel(principal, activityId)
        val second = service.cancel(principal, activityId)

        assertThat(first.status).isEqualTo("CANCELLED")
        assertThat(second.status).isEqualTo("CANCELLED")
        assertThat(second.version).isEqualTo(first.version)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM activities WHERE id=?", Int::class.java, activityId
            )
        ).isEqualTo(3)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_lifecycle_events " +
                    "WHERE activity_id=? AND from_status=2 AND to_status=3",
                Int::class.java, activityId
            )
        ).isEqualTo(1)
    }

    @Test
    fun deletesDraftChildrenAndKeepsReferencedFileObject() {
        val principal = UserPrincipal(USER_ID, "activity-delete-it")
        val otherPrincipal = UserPrincipal(OTHER_USER_ID, "activity-delete-other-it")
        val created = service.createPersonal(
            principal,
            PersonalActivityCreateRequest(
                "待删除草稿", "HIKING", null,
                COVER_FILE_ID.toString(), listOf(DELETE_MEDIA_FILE_ID.toString()),
                null, null, null, null, null, null,
                capacity = null, signupDetails = null, organizerMessage = null
            ),
            "activity-delete-create"
        )
        val activityId = created.view.activityId.toLong()

        assertThatThrownBy { service.deleteDraft(otherPrincipal, activityId) }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND)

        service.deleteDraft(principal, activityId)

        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activities WHERE id=?",
                Int::class.java, activityId
            )
        ).isZero()
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_media WHERE activity_id=?",
                Int::class.java, activityId
            )
        ).isZero()
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_lifecycle_events WHERE activity_id=?",
                Int::class.java, activityId
            )
        ).isZero()
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM file_objects WHERE id IN (?, ?)",
                Int::class.java, COVER_FILE_ID, DELETE_MEDIA_FILE_ID
            )
        ).isEqualTo(2)

        assertThatThrownBy { service.deleteDraft(principal, activityId) }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND)
    }

    @Test
    fun deletedDraftKeepsCreateIdempotencyReservationUntilOriginalWindowExpires() {
        val principal = UserPrincipal(USER_ID, "activity-delete-idempotency-it")
        val request = PersonalActivityCreateRequest(
            "删除后保留幂等窗口", null, null, null, listOf<String>(),
            null, null, null, null, null, null,
            capacity = null, signupDetails = null, organizerMessage = null
        )

        val created = service.createPersonal(principal, request, "activity-delete-idempotency")
        val activityId = created.view.activityId.toLong()
        service.deleteDraft(principal, activityId)

        assertThatThrownBy {
            service.createPersonal(principal, request, "activity-delete-idempotency")
        }
            .isInstanceOf(BusinessException::class.java)
            .satisfies(Consumer {  exception ->
                assertThat((exception as BusinessException).errorCode.code).isEqualTo(11605)
             })
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activities WHERE owner_user_id=?",
                Int::class.java, USER_ID
            )
        ).isZero()
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_create_idempotency_tombstones " +
                    "WHERE activity_id=? AND expires_at>deleted_at",
                Int::class.java, activityId
            )
        ).isEqualTo(1)
    }

    @Test
    fun deletedDraftRejectsDifferentRequestForTheReservedCreateKey() {
        val principal = UserPrincipal(USER_ID, "activity-delete-conflict-it")
        val original = PersonalActivityCreateRequest(
            "原始草稿", null, null, null, listOf<String>(),
            null, null, null, null, null, null,
            capacity = null, signupDetails = null, organizerMessage = null
        )
        val created = service.createPersonal(principal, original, "activity-delete-conflict")
        service.deleteDraft(principal, created.view.activityId.toLong())
        idempotency.clear()

        val changed = PersonalActivityCreateRequest(
            "不同草稿", null, null, null, listOf<String>(),
            null, null, null, null, null, null,
            capacity = null, signupDetails = null, organizerMessage = null
        )
        assertThatThrownBy {
            service.createPersonal(principal, changed, "activity-delete-conflict")
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(ActivityErrorCode.IDEMPOTENCY_KEY_CONFLICT)
    }

    @Test
    fun deletedDraftCreateKeyCanBeReusedAfterItsOriginalWindowExpires() {
        val principal = UserPrincipal(USER_ID, "activity-delete-expiry-it")
        val request = PersonalActivityCreateRequest(
            "删除后过期重用", null, null, null, listOf<String>(),
            null, null, null, null, null, null,
            capacity = null, signupDetails = null, organizerMessage = null
        )
        val first = service.createPersonal(principal, request, "activity-delete-expiry")
        service.deleteDraft(principal, first.view.activityId.toLong())
        jdbcTemplate.update(
            """
                UPDATE activity_create_idempotency_tombstones
                SET expires_at=UTC_TIMESTAMP(3)-INTERVAL 1 SECOND
                WHERE create_idempotency_key='activity-delete-expiry'
                """
        )
        idempotency.clear()

        val second = service.createPersonal(principal, request, "activity-delete-expiry")

        assertThat(second.replayed).isFalse()
        assertThat(second.view.activityId).isNotEqualTo(first.view.activityId)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activities WHERE owner_user_id=?",
                Int::class.java, USER_ID
            )
        ).isEqualTo(1)
    }

    @Test
    fun organizationDraftUsesOrganizationOwnerAndSameKeyDoesNotDuplicate() {
        val principal = UserPrincipal(USER_ID, "activity-command-org-it")
        val request = OrganizationActivityCreateRequest(
            "企业草稿", null, null, null, listOf<String>(),
            null, null, null, null, null, null, null, null
        )

        val first = service.createOrganization(principal, ORGANIZATION_ID, request, "activity-command-org")
        val second = service.createOrganization(principal, ORGANIZATION_ID, request, "activity-command-org")

        assertThat(second.replayed).isTrue()
        assertThat(second.view.activityId).isEqualTo(first.view.activityId)
        val activityId = first.view.activityId.toLong()
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activities WHERE id=? AND owner_organization_id=? " +
                    "AND owner_user_id IS NULL AND signup_details IS NULL " +
                    "AND organizer_message IS NULL",
                Int::class.java, activityId, ORGANIZATION_ID
            )
        ).isEqualTo(1)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activities WHERE owner_organization_id=?",
                Int::class.java, ORGANIZATION_ID
            )
        ).isEqualTo(1)
    }

    @Test
    fun reusesCreateKeyAfterTwentyFourHoursWithRealDatabaseState() {
        val principal = UserPrincipal(USER_ID, "activity-command-expiry-it")
        val request = PersonalActivityCreateRequest(
            "过期后重用幂等键", null, null, null, listOf<String>(),
            null, null, null, null, null, null,
            capacity = null, signupDetails = null, organizerMessage = null
        )

        val first = service.createPersonal(principal, request, "activity-command-expiry")
        val firstId = first.view.activityId.toLong()
        jdbcTemplate.update(
            "UPDATE activities SET created_at=UTC_TIMESTAMP(3)-INTERVAL 25 HOUR WHERE id=?",
            firstId
        )
        idempotency.clear()

        val second = service.createPersonal(principal, request, "activity-command-expiry")

        assertThat(second.replayed).isFalse()
        assertThat(second.view.activityId).isNotEqualTo(first.view.activityId)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activities WHERE owner_user_id=?",
                Int::class.java, USER_ID
            )
        ).isEqualTo(2)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT create_idempotency_scope FROM activities WHERE id=?",
                String::class.java, firstId
            )
        ).isNull()
    }

    @Test
    fun concurrentExpiredCreateKeyCreatesOneReplacementAndReplaysIt() {
        val principal = UserPrincipal(USER_ID, "activity-command-expiry-concurrent-it")
        val request = PersonalActivityCreateRequest(
            "并发重用过期幂等键", null, null, null, listOf<String>(),
            null, null, null, null, null, null,
            capacity = null, signupDetails = null, organizerMessage = null
        )
        val first = service.createPersonal(principal, request, "activity-command-expiry-concurrent")
        val firstId = first.view.activityId.toLong()
        jdbcTemplate.update(
            "UPDATE activities SET created_at=UTC_TIMESTAMP(3)-INTERVAL 25 HOUR WHERE id=?",
            firstId
        )
        idempotency.clear()

        val create = Callable { service.createPersonal(principal, request, "activity-command-expiry-concurrent") }
        val outcomes = runConcurrently(create, create)

        assertThat(outcomes).extracting(Function {  it.replayed  })
            .containsExactlyInAnyOrder(false, true)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activities WHERE owner_user_id=?",
                Int::class.java, USER_ID
            )
        ).isEqualTo(2)
    }

    @Test
    fun draftDeletionAndExpiredReplayDoNotDeadlockAtOriginalWindowBoundary() {
        val principal = UserPrincipal(USER_ID, "activity-delete-replay-race-it")
        val request = PersonalActivityCreateRequest(
            "删除重放并发", null, null, null, listOf<String>(),
            null, null, null, null, null, null,
            capacity = null, signupDetails = null, organizerMessage = null
        )
        val idempotencyKey = "activity-delete-replay-race"
        val original = service.createPersonal(principal, request, idempotencyKey)
        val originalId = original.view.activityId.toLong()

        val boundary = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val createdAt = LocalDateTime.ofInstant(
            boundary.minus(24, ChronoUnit.HOURS).plusSeconds(1),
            ZoneOffset.UTC
        )
        jdbcTemplate.update("UPDATE activities SET created_at=? WHERE id=?", createdAt, originalId)
        idempotency.clear()

        val deletionReachedTombstoneInsert = CountDownLatch(1)
        val replayCheckedTombstone = CountDownLatch(1)
        val deletionMapper: ActivityCreateIdempotencyTombstoneMapper = mock(
            ActivityCreateIdempotencyTombstoneMapper::class.java,
            delegatesTo<ActivityCreateIdempotencyTombstoneMapper>(idempotencyTombstones)
        )
        doAnswer { invocation ->
            deletionReachedTombstoneInsert.countDown()
            if (!replayCheckedTombstone.await(5, TimeUnit.SECONDS)) {
                throw AssertionError("重放事务未在限定时间内检查墓碑")
            }
            idempotencyTombstones.insert(invocation.getArgument(0))
        }.`when`(deletionMapper).insert(any<ActivityCreateIdempotencyTombstoneEntity>())

        val replayMapper: ActivityCreateIdempotencyTombstoneMapper = mock(
            ActivityCreateIdempotencyTombstoneMapper::class.java,
            delegatesTo<ActivityCreateIdempotencyTombstoneMapper>(idempotencyTombstones)
        )
        doAnswer { invocation ->
            val result = idempotencyTombstones.findByScopeAndKey(
                invocation.getArgument(0), invocation.getArgument(1)
            )
            replayCheckedTombstone.countDown()
            result
        }.`when`(replayMapper).findByScopeAndKey(any<String>(), eq(idempotencyKey))

        val deletionService = commandService(
            deletionMapper, Clock.fixed(boundary, ZoneOffset.UTC)
        )
        val replayService = commandService(
            replayMapper, Clock.fixed(boundary.plusSeconds(2), ZoneOffset.UTC)
        )
        val transactions = TransactionTemplate(transactionManager)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val deletion = executor.submit {
                transactions.executeWithoutResult { deletionService.deleteDraft(principal, originalId) }
            }
            assertThat(deletionReachedTombstoneInsert.await(5, TimeUnit.SECONDS)).isTrue()
            val replay = executor.submit<ActivityCreateOutcome> {
                transactions.execute {
                    replayService.createPersonal(principal, request, idempotencyKey)
                }
            }

            val replacement = replay[20, TimeUnit.SECONDS]
            deletion[20, TimeUnit.SECONDS]

            assertThat(replacement).isNotNull()
            assertThat(replacement.replayed).isFalse()
            assertThat(replacement.view.activityId).isNotEqualTo(original.view.activityId)
            assertThat(
                jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM activities " +
                        "WHERE create_idempotency_scope=? AND create_idempotency_key=?",
                    Int::class.java,
                    "USER:$USER_ID:$USER_ID", idempotencyKey
                )
            ).isEqualTo(1)
        } finally {
            executor.shutdownNow()
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()
        }
    }

    @Test
    fun concurrentSameCreateKeyCreatesOneDraftAndReplaysTheWinner() {
        val principal = UserPrincipal(USER_ID, "activity-command-concurrent-it")
        val create = Callable {
            service.createPersonal(
                principal,
                PersonalActivityCreateRequest(
                    "并发创建草稿", null, null, null, listOf<String>(),
                    null, null, null, null, null, null,
                    capacity = null, signupDetails = null, organizerMessage = null
                ),
                "activity-command-concurrent"
            )
        }

        val outcomes = runConcurrently(create, create)

        assertThat(outcomes).extracting(Function {  it.replayed  })
            .containsExactlyInAnyOrder(false, true)
        assertThat(outcomes).extracting(Function {  it.view.activityId  })
            .containsOnly(outcomes[0].view.activityId)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activities " +
                    "WHERE create_idempotency_scope=? AND create_idempotency_key=?",
                Int::class.java,
                "USER:$USER_ID:$USER_ID", "activity-command-concurrent"
            )
        ).isEqualTo(1)
    }

    @Test
    fun updatesPersonalDraftAndPublishedActivityWithVersionAndOrganizationDraft() {
        val principal = UserPrincipal(USER_ID, "activity-update-it")
        insertTemporaryFile(UPDATE_COVER_FILE_ID)
        insertTemporaryFile(UPDATE_MEDIA_FILE_ID)

        val created = service.createPersonal(
            principal,
            PersonalActivityCreateRequest(
                "原始活动", "HIKING", "原始介绍",
                COVER_FILE_ID.toString(), listOf<String>(),
                activeAt(1), activeAt(2), activeAt(2), activeAt(3),
                "440305", "原始地址",
                capacity = 20, signupDetails = "原始报名说明", organizerMessage = "原始留言"
            ),
            "activity-update-create"
        )
        val activityId = created.view.activityId.toLong()

        val clearedDraft = service.updatePersonal(
            principal, activityId,
            PersonalActivityUpdateRequest(
                0, "草稿清空", null, null, null, listOf<String>(),
                null, null, null, null, null, null,
                capacity = null, signupDetails = null, organizerMessage = null
            )
        )
        assertThat(clearedDraft.status).isEqualTo("DRAFT")
        assertThat(clearedDraft.version).isEqualTo(1)
        assertThat(clearedDraft.description).isNull()
        assertThat(clearedDraft.media).isEmpty()

        val readyDraft = service.updatePersonal(
            principal, activityId,
            PersonalActivityUpdateRequest(
                1, "准备发布", "CAMPING", "完整介绍",
                UPDATE_COVER_FILE_ID.toString(), listOf(UPDATE_MEDIA_FILE_ID.toString()),
                activeAt(4), activeAt(5), activeAt(5), activeAt(6),
                "440305", "新地址",
                LOCATION_LATITUDE, LOCATION_LONGITUDE, 30,
                "新报名说明", "新留言"
            )
        )
        assertThat(readyDraft.status).isEqualTo("DRAFT")
        assertThat(readyDraft.version).isEqualTo(2)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_media WHERE activity_id=? AND file_id=?",
                Int::class.java, activityId, UPDATE_MEDIA_FILE_ID
            )
        ).isEqualTo(1)

        val published = service.publish(principal, activityId)
        assertThat(published.status).isEqualTo("PUBLISHED")
        assertThat(published.version).isEqualTo(3)

        val updatedPublished = service.updatePersonal(
            principal, activityId,
            PersonalActivityUpdateRequest(
                3, "已发布更新", "CAMPING", "已发布介绍",
                UPDATE_COVER_FILE_ID.toString(), listOf(UPDATE_MEDIA_FILE_ID.toString()),
                activeAt(4), activeAt(5), activeAt(5), activeAt(6),
                "440305", "已发布地址",
                UPDATED_LATITUDE, UPDATED_LONGITUDE, 30,
                "已发布报名说明", "已发布留言"
            )
        )
        assertThat(updatedPublished.status).isEqualTo("PUBLISHED")
        assertThat(updatedPublished.version).isEqualTo(4)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT title FROM activities WHERE id=?", String::class.java, activityId
            )
        ).isEqualTo("已发布更新")
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT latitude FROM activities WHERE id=?",
                BigDecimal::class.java, activityId
            )
        ).isEqualByComparingTo(UPDATED_LATITUDE)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT longitude FROM activities WHERE id=?",
                BigDecimal::class.java, activityId
            )
        ).isEqualByComparingTo(UPDATED_LONGITUDE)

        assertThatThrownBy {
            service.updatePersonal(
                principal, activityId,
                PersonalActivityUpdateRequest(
                    3, "过期版本", null, null, null, listOf<String>(),
                    null, null, null, null, null, null,
                    capacity = null, signupDetails = null, organizerMessage = null
                )
            )
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(ActivityErrorCode.VERSION_CONFLICT)

        val organization = service.createOrganization(
            principal,
            ORGANIZATION_ID,
            OrganizationActivityCreateRequest(
                "企业原始草稿", null, null, null, listOf<String>(),
                null, null, null, null, null, null, null
            ),
            "activity-update-organization-create"
        )
        val organizationUpdated = service.updateOrganization(
            principal,
            ORGANIZATION_ID,
            organization.view.activityId.toLong(),
            OrganizationActivityUpdateRequest(
                0, "企业更新", "CAMPING", "企业介绍", null, listOf<String>(),
                null, null, null, null, "440305", "企业地址",
                capacity = 50
            )
        )
        assertThat(organizationUpdated.status).isEqualTo("DRAFT")
        assertThat(organizationUpdated.version).isEqualTo(1)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT signup_details IS NULL AND organizer_message IS NULL " +
                    "FROM activities WHERE id=?",
                Boolean::class.java, organization.view.activityId.toLong()
            )
        ).isTrue()
    }

    @Test
    fun rejectsEditWhenOwnerOrOrganizationRelationIsInvalid() {
        val principal = UserPrincipal(USER_ID, "activity-permission-it")
        val otherPrincipal = UserPrincipal(OTHER_USER_ID, "activity-other-it")
        val personalActivityId = service.createPersonal(
            principal,
            PersonalActivityCreateRequest(
                "个人权限活动", null, null, null, listOf<String>(),
                null, null, null, null, null, null,
                capacity = null, signupDetails = null, organizerMessage = null
            ),
            "activity-permission-personal"
        ).view.activityId.toLong()
        val organizationActivityId = service.createOrganization(
            principal,
            ORGANIZATION_ID,
            OrganizationActivityCreateRequest(
                "企业权限活动", null, null, null, listOf<String>(),
                null, null, null, null, null, null, null
            ),
            "activity-permission-organization"
        ).view.activityId.toLong()

        val personalUpdate = PersonalActivityUpdateRequest(
            0, "个人更新", null, null, null, listOf<String>(),
            null, null, null, null, null, null,
            capacity = null, signupDetails = null, organizerMessage = null
        )
        val organizationUpdate = OrganizationActivityUpdateRequest(
            0, "企业更新", null, null, null, listOf<String>(),
            null, null, null, null, null, null, null
        )

        assertNotFound { service.updatePersonal(otherPrincipal, personalActivityId, personalUpdate) }
        assertNotFound { service.updatePersonal(principal, organizationActivityId, personalUpdate) }
        assertNotFound {
            service.updateOrganization(principal, ORGANIZATION_ID, personalActivityId, organizationUpdate)
        }
        assertNotFound {
            service.updateOrganization(otherPrincipal, ORGANIZATION_ID, organizationActivityId, organizationUpdate)
        }
        assertNotFound {
            service.updateOrganization(principal, ORGANIZATION_ID + 1, organizationActivityId, organizationUpdate)
        }

        jdbcTemplate.update(
            "UPDATE organization_members SET status=2, disabled_at=UTC_TIMESTAMP(3) WHERE id=?",
            ORGANIZATION_MEMBER_ID
        )
        assertNotFound {
            service.updateOrganization(principal, ORGANIZATION_ID, organizationActivityId, organizationUpdate)
        }

        jdbcTemplate.update(
            "UPDATE organization_members SET status=1, disabled_at=NULL WHERE id=?",
            ORGANIZATION_MEMBER_ID
        )
        jdbcTemplate.update("UPDATE organizations SET status=2 WHERE id=?", ORGANIZATION_ID)
        assertNotFound {
            service.updateOrganization(principal, ORGANIZATION_ID, organizationActivityId, organizationUpdate)
        }
    }

    private fun assertNotFound(operation: () -> Unit) {
        assertThatThrownBy(operation)
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND)
    }

    private fun blockingReasons(userId: Long): List<String> {
        val now = jdbcTemplate.queryForObject(
            "SELECT UTC_TIMESTAMP(3)", LocalDateTime::class.java
        )
        return deactivationBlockers
            .map { it.blockingReason(userId, now!!) }
            .filter { it.isPresent }
            .map { it.get() }
    }

    private fun insertUser(userId: Long) {
        jdbcTemplate.update(
            """
                INSERT INTO users (id, status, version, created_at, updated_at)
                VALUES (?, 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """, userId
        )
    }

    private fun lockFile(connection: Connection, fileId: Long) {
        connection.prepareStatement(
            "SELECT id FROM file_objects WHERE id=? FOR UPDATE"
        ).use { statement ->
            statement.setLong(1, fileId)
            statement.executeQuery().use { result ->
                assertThat(result.next()).isTrue()
            }
        }
    }

    private fun awaitDatabaseLockWaitCount(
        tableName: String,
        recordId: Long,
        expected: Int
    ) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            val waiting = jdbcTemplate.queryForObject(
                """
                    SELECT COUNT(*)
                    FROM performance_schema.data_lock_waits waits
                    JOIN performance_schema.data_locks requested
                      ON requested.engine=waits.engine
                     AND requested.engine_lock_id=waits.requesting_engine_lock_id
                    WHERE requested.object_schema=DATABASE()
                      AND requested.object_name=?
                      AND requested.index_name='PRIMARY'
                      AND requested.lock_type='RECORD'
                      AND requested.lock_status='WAITING'
                      AND requested.lock_data=?
                    """,
                Int::class.java, tableName, recordId.toString()
            )
            if (waiting != null && waiting >= expected) {
                return
            }
            Thread.sleep(20)
        }
        throw AssertionError(
            "等待目标数据库行锁超时，表=$tableName" +
                "，主键=$recordId" +
                "，期望等待数=$expected"
        )
    }

    private fun grantedFileRecordLocks(fileId: Long): Int {
        val count = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM performance_schema.data_locks
                WHERE object_schema=DATABASE()
                  AND object_name='file_objects'
                  AND index_name='PRIMARY'
                  AND lock_type='RECORD'
                  AND lock_status='GRANTED'
                  AND lock_data=?
                """,
            Int::class.java, fileId.toString()
        )
        return count ?: 0
    }

    private fun runConcurrently(
        first: Callable<ActivityCreateOutcome>,
        second: Callable<ActivityCreateOutcome>
    ): List<ActivityCreateOutcome> {
        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        try {
            val firstFuture = executor.submit<ActivityCreateOutcome> {
                ready.countDown()
                start.await(5, TimeUnit.SECONDS)
                first.call()
            }
            val secondFuture = executor.submit<ActivityCreateOutcome> {
                ready.countDown()
                start.await(5, TimeUnit.SECONDS)
                second.call()
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue()
            start.countDown()
            return listOf(
                firstFuture[20, TimeUnit.SECONDS],
                secondFuture[20, TimeUnit.SECONDS]
            )
        } finally {
            executor.shutdownNow()
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()
        }
    }

    private fun commandService(
        tombstones: ActivityCreateIdempotencyTombstoneMapper,
        clock: Clock
    ): DefaultActivityCommandService {
        return DefaultActivityCommandService(
            activities, tombstones, media, events, organizations, profiles, files,
            completion, regions, redis, participationCancellation, accountStates,
            null, null, clock
        )
    }

    private fun insertProfile(userId: Long) {
        jdbcTemplate.update(
            """
                INSERT INTO user_profiles (
                    user_id, nickname, province_code, province_name, city_code, city_name,
                    district_code, district_name, completed_at, version, created_at, updated_at
                ) VALUES (?, '测试用户', '44', '广东省', '4403', '深圳市',
                    '440305', '南山区', UTC_TIMESTAMP(3), 0,
                    UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """, userId
        )
    }

    private fun insertOrganization() {
        jdbcTemplate.update(
            """
                INSERT INTO organizations (
                    id, name, province_code, province_name, city_code, city_name,
                    district_code, district_name, address_detail, contact_phone_ciphertext,
                    contact_phone_lookup_hash, contact_phone_last_four, status, version,
                    created_at, updated_at
                ) VALUES (?, '测试企业', '44', '广东省', '4403', '深圳市',
                    '440305', '南山区', '企业地址', X'01',
                    UNHEX(SHA2('activity-command-org', 256)), '0001', 1, 0,
                    UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """, ORGANIZATION_ID
        )
    }

    private fun insertOwnerRelation() {
        jdbcTemplate.update(
            """
                INSERT INTO organization_members (
                    id, organization_id, user_id, role_code, status,
                    enabled_at, version, created_at, updated_at
                ) VALUES (?, ?, ?, 1, 1, UTC_TIMESTAMP(3), 0,
                    UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """, ORGANIZATION_MEMBER_ID, ORGANIZATION_ID, USER_ID
        )
    }

    private fun insertTemporaryFile(fileId: Long) {
        jdbcTemplate.update(
            """
                INSERT INTO file_objects (
                    id, uploader_type, uploader_id, purpose, storage_provider, bucket_name,
                    object_key, original_filename, content_type, file_extension,
                    size_bytes, sha256, access_level, scan_status, lifecycle_status,
                    version, created_at, updated_at
                ) VALUES (?, 1, ?, 'ACTIVITY', 'local', 'eligo', ?, 'activity.jpg',
                    'image/jpeg', 'jpg', 1, UNHEX(SHA2(?, 256)),
                    1, 2, 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """, fileId, USER_ID, "activity/command/$fileId", "activity-command-$fileId"
        )
    }

    companion object {
        private val ACTIVE_WINDOW_BASE = Instant.now()
            .plus(1, ChronoUnit.DAYS)
            .truncatedTo(ChronoUnit.SECONDS)
        private val LOCATION_LATITUDE = BigDecimal("22.5430960")
        private val LOCATION_LONGITUDE = BigDecimal("114.0578650")
        private val UPDATED_LATITUDE = BigDecimal("22.5430961")
        private val UPDATED_LONGITUDE = BigDecimal("114.0578651")

        private const val USER_ID = 9970001L
        private const val OTHER_USER_ID = 9970002L
        private const val ORGANIZATION_ID = 9970001L
        private const val ORGANIZATION_MEMBER_ID = 9970001L
        private const val COVER_FILE_ID = 9971001L
        private const val UPDATE_COVER_FILE_ID = 9971002L
        private const val UPDATE_MEDIA_FILE_ID = 9971003L
        private const val DELETE_MEDIA_FILE_ID = 9971004L

        @JvmStatic
        private fun activeAt(hoursFromBase: Long): Instant {
            return ACTIVE_WINDOW_BASE.plus(hoursFromBase, ChronoUnit.HOURS)
        }
    }
}
