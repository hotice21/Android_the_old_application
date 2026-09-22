package com.eligo.server.activity

import java.util.function.Function

import com.eligo.server.activity.service.ActivityLifecycleProcessor
import com.eligo.server.activity.service.ActivityReadService
import com.eligo.server.activity.vo.ActivityMapView
import com.eligo.server.activity.vo.ManagedActivityDetailView
import com.eligo.server.activity.vo.ManagedActivitySummaryView
import com.eligo.server.activity.vo.PublicActivityDetailView
import com.eligo.server.activity.vo.PublicActivitySummaryView
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.database.Stage2TestDatabaseCleaner
import com.eligo.server.integration.wechat.WechatRestClientFactory
import com.eligo.server.security.UserPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

@SpringBootTest(
    properties = [
        "eligo.security.jwt.signing-key-base64=IB8eHRwbGhkYFxYVFBMSERAPDg0MCwoJCAcGBQQDAgE=",
        "eligo.security.sensitive-data.encryption-key-base64=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
        "eligo.security.sensitive-data.lookup-key-base64=Hx4dHBsaGRgXFhUUExIREA8ODQwLCgkIBwYFBAMCAQA="
    ]
)
@ActiveProfiles("it")
@EnabledIfSystemProperty(named = "eligo.database.tests", matches = "true")
class ActivityReadDatabaseIntegrationTests {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var service: ActivityReadService

    @Autowired
    private lateinit var lifecycleProcessor: ActivityLifecycleProcessor

    @MockitoBean
    private lateinit var redis: StringRedisTemplate

    @MockitoBean
    private lateinit var wechatRestClientFactory: WechatRestClientFactory

    @BeforeEach
    fun prepareDatabase() {
        Stage2TestDatabaseCleaner.cleanAllStage2Tables(jdbcTemplate)
        val personalAvatarFileId = insertFile(9911001L)
        val organizationAvatarFileId = insertFile(9911002L)
        val personalCoverFileId = insertFile(9911003L)
        val organizationCoverFileId = insertFile(9911004L)
        val mediaOneFileId = insertFile(9911005L)
        val mediaTwoFileId = insertFile(9911006L)

        insertUser()
        insertProfile(personalAvatarFileId)
        insertOrganization(organizationAvatarFileId)
        insertOrganizationMember()

        val now = now()
        insertActivity(
            PERSONAL_ACTIVITY_ID, USER_ID, null, 2, "个人公开活动",
            "HIKING", personalCoverFileId, now.plusHours(2), "个人报名说明", "组织者留言"
        )
        insertActivity(
            ORGANIZATION_ACTIVITY_ID, null, ORGANIZATION_ID, 2, "企业公开活动",
            "CAMPING", organizationCoverFileId, now.plusHours(2), null, null
        )
        insertActivity(
            CANCELLED_ACTIVITY_ID, USER_ID, null, 3, "已取消活动",
            "RUNNING", personalCoverFileId, now.plusHours(3), "报名说明", "留言"
        )
        insertActivity(
            DRAFT_ACTIVITY_ID, USER_ID, null, 1, "个人草稿",
            null, null, now.plusHours(1), "草稿说明", "草稿留言"
        )
        insertActivity(
            HIDDEN_ACTIVITY_ID, USER_ID, null, 5, "隐藏活动",
            "MUSIC", personalCoverFileId, now.plusHours(5), "报名说明", "留言"
        )
        insertActivity(
            ENDED_ACTIVITY_ID, USER_ID, null, 4, "已结束活动",
            "FOOD", personalCoverFileId, now.plusHours(6), "报名说明", "留言"
        )
        jdbcTemplate.update(
            """
                UPDATE activities
                SET latitude=?, longitude=?
                WHERE id<>?
                """, LATITUDE, LONGITUDE, ORGANIZATION_ACTIVITY_ID
        )

        insertMedia(PERSONAL_ACTIVITY_ID, mediaTwoFileId, 2)
        insertMedia(PERSONAL_ACTIVITY_ID, mediaOneFileId, 1)
    }

    @Test
    fun realQueriesApplyPublicStatusFilterStableCursorAndOwnerMapping() {
        val firstPage = service.listPublicActivities(null, 1, null, null, "440305")

        assertThat(firstPage.items).extracting(Function {  it.activityId  })
            .containsExactly(PERSONAL_ACTIVITY_ID.toString())
        assertThat(firstPage.items[0].owner.displayName).isEqualTo("测试用户")
        assertThat(firstPage.items[0].latitude).isEqualByComparingTo(LATITUDE)
        assertThat(firstPage.items[0].longitude).isEqualByComparingTo(LONGITUDE)
        assertThat(firstPage.nextCursor).isNotBlank()

        val secondPage = service.listPublicActivities(
            firstPage.nextCursor, 1, "PUBLISHED", "CAMPING", "440305"
        )
        assertThat(secondPage.items).extracting(Function {  it.activityId  })
            .containsExactly(ORGANIZATION_ACTIVITY_ID.toString())
        assertThat(secondPage.items[0].owner.ownerType).isEqualTo("ORGANIZATION")
        assertThat(secondPage.items[0].owner.displayName).isEqualTo("测试企业")
        assertThat(secondPage.items[0].latitude).isNull()
        assertThat(secondPage.items[0].longitude).isNull()

        assertThat(service.listPublicActivities(null, 50, null, null, null).items)
            .extracting(Function {  it.activityId  })
            .containsExactly(PERSONAL_ACTIVITY_ID.toString(), ORGANIZATION_ACTIVITY_ID.toString())

        assertThat(service.listPublicActivities(null, 50, "CANCELLED", null, null).items)
            .extracting(Function {  it.activityId  })
            .containsExactly(CANCELLED_ACTIVITY_ID.toString())
    }

    @Test
    fun realMapQueryFiltersBoundsLifecycleCoordinatesCategoryAndUsesStableOrder() {
        val sharedStartsAt = jdbcTemplate.queryForObject(
            "SELECT starts_at FROM activities WHERE id=?",
            java.sql.Timestamp::class.java,
            PERSONAL_ACTIVITY_ID
        )!!.toLocalDateTime()
        insertActivity(
            MAP_SECOND_ACTIVITY_ID, USER_ID, null, 2, "地图活动二",
            "HIKING", 9911003L, sharedStartsAt, "报名说明", "留言"
        )
        insertActivity(
            MAP_OTHER_CATEGORY_ACTIVITY_ID, USER_ID, null, 2, "地图露营活动",
            "CAMPING", 9911003L, sharedStartsAt, "报名说明", "留言"
        )
        insertActivity(
            MAP_EXPIRED_ACTIVITY_ID, USER_ID, null, 2, "已过期地图活动",
            "HIKING", 9911003L, sharedStartsAt, "报名说明", "留言"
        )
        insertActivity(
            MAP_OUTSIDE_ACTIVITY_ID, USER_ID, null, 2, "视野外地图活动",
            "HIKING", 9911003L, sharedStartsAt, "报名说明", "留言"
        )
        jdbcTemplate.update(
            "UPDATE activities SET latitude=?, longitude=? WHERE id IN (?, ?)",
            BigDecimal("22.5500000"), BigDecimal("114.0600000"),
            MAP_SECOND_ACTIVITY_ID, MAP_OTHER_CATEGORY_ACTIVITY_ID
        )
        jdbcTemplate.update(
            "UPDATE activities SET latitude=23.0000000, longitude=114.0600000 WHERE id=?",
            MAP_OUTSIDE_ACTIVITY_ID
        )
        val now = now()
        jdbcTemplate.update(
            """
                UPDATE activities
                SET latitude=22.5600000, longitude=114.0700000,
                    registration_starts_at=?, registration_ends_at=?,
                    starts_at=?, ends_at=?
                WHERE id=?
                """,
            now.minusHours(4), now.minusHours(3),
            now.minusHours(2), now.minusHours(1), MAP_EXPIRED_ACTIVITY_ID
        )

        val all = service.listActivitiesOnMap(
            BigDecimal("22.5000000"), BigDecimal("22.6000000"),
            BigDecimal("114.0000000"), BigDecimal("114.1000000"),
            null, 200
        )

        assertThat(all.items).extracting(Function {  it.activityId  })
            .containsExactly(
                PERSONAL_ACTIVITY_ID.toString(),
                MAP_SECOND_ACTIVITY_ID.toString(),
                MAP_OTHER_CATEGORY_ACTIVITY_ID.toString()
            )
        assertThat(all.truncated).isFalse()
        assertThat(all.items[0].latitude).isEqualByComparingTo(LATITUDE)
        assertThat(all.items[0].longitude).isEqualByComparingTo(LONGITUDE)

        val hiking = service.listActivitiesOnMap(
            BigDecimal("22.5000000"), BigDecimal("22.6000000"),
            BigDecimal("114.0000000"), BigDecimal("114.1000000"),
            "hiking", 200
        )
        assertThat(hiking.items).extracting(Function {  it.activityId  })
            .containsExactly(PERSONAL_ACTIVITY_ID.toString(), MAP_SECOND_ACTIVITY_ID.toString())

        val truncated = service.listActivitiesOnMap(
            BigDecimal("22.5000000"), BigDecimal("22.6000000"),
            BigDecimal("114.0000000"), BigDecimal("114.1000000"),
            "HIKING", 1
        )
        assertThat(truncated.items).extracting(Function {  it.activityId  })
            .containsExactly(PERSONAL_ACTIVITY_ID.toString())
        assertThat(truncated.truncated).isTrue()
    }

    @Test
    fun expiredPublishedActivityImmediatelyMovesFromPublishedToEndedPublicView() {
        val now = now()
        jdbcTemplate.update(
            """
                UPDATE activities
                SET registration_starts_at=?, registration_ends_at=?,
                    starts_at=?, ends_at=?
                WHERE id=?
                """,
            now.minusHours(4), now.minusHours(3),
            now.minusHours(2), now.minusHours(1), PERSONAL_ACTIVITY_ID
        )

        assertThat(service.listPublicActivities(null, 50, null, null, null).items)
            .extracting(Function {  it.activityId  })
            .doesNotContain(PERSONAL_ACTIVITY_ID.toString())

        val ended = service.listPublicActivities(null, 50, "ENDED", null, null)
            .items
            .filter { it.activityId == PERSONAL_ACTIVITY_ID.toString() }
            .first()
        assertThat(ended.status).isEqualTo("ENDED")
        assertThat(ended.registrationStatus).isEqualTo("ENDED")

        val detail = service.getPublicActivity(PERSONAL_ACTIVITY_ID)
        assertThat(detail.status).isEqualTo("ENDED")
        assertThat(detail.registrationStatus).isEqualTo("ENDED")
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM activities WHERE id=?",
                Int::class.java, PERSONAL_ACTIVITY_ID
            )
        ).isEqualTo(2)
    }

    @Test
    fun lifecycleProcessorPersistsEndedStatusVersionAndOneSystemEvent() {
        val now = now()
        jdbcTemplate.update(
            """
                UPDATE activities
                SET registration_starts_at=?, registration_ends_at=?,
                    starts_at=?, ends_at=?
                WHERE id=?
                """,
            now.minusHours(4), now.minusHours(3),
            now.minusHours(2), now.minusHours(1), PERSONAL_ACTIVITY_ID
        )

        assertThat(lifecycleProcessor.endIfDue(PERSONAL_ACTIVITY_ID, now)).isTrue()
        assertThat(lifecycleProcessor.endIfDue(PERSONAL_ACTIVITY_ID, now)).isFalse()

        assertThat(
            jdbcTemplate.queryForMap(
                "SELECT status, version FROM activities WHERE id=?",
                PERSONAL_ACTIVITY_ID
            )
        ).containsEntry("status", 4).containsEntry("version", 1)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_lifecycle_events " +
                    "WHERE activity_id=? AND from_status=2 AND to_status=4 " +
                    "AND operator_user_id IS NULL",
                Int::class.java, PERSONAL_ACTIVITY_ID
            )
        ).isEqualTo(1)
    }

    @Test
    fun realDetailHidesNonPublicActivitiesAndOrganizationPersonalFields() {
        val personalDetail = service.getPublicActivity(PERSONAL_ACTIVITY_ID)
        assertThat(personalDetail.media).extracting(Function {  it.fileId  })
            .containsExactly("9911005", "9911006")
        assertThat(personalDetail.signupDetails).isEqualTo("个人报名说明")
        assertThat(personalDetail.latitude).isEqualByComparingTo(LATITUDE)
        assertThat(personalDetail.longitude).isEqualByComparingTo(LONGITUDE)

        val detail = service.getPublicActivity(ORGANIZATION_ACTIVITY_ID)

        assertThat(detail.owner.displayName).isEqualTo("测试企业")
        assertThat(detail.signupDetails).isNull()
        assertThat(detail.organizerMessage).isNull()
        assertThat(detail.media).isEmpty()
        assertThat(detail.latitude).isNull()
        assertThat(detail.longitude).isNull()

        assertNotPublic(DRAFT_ACTIVITY_ID)
        assertNotPublic(HIDDEN_ACTIVITY_ID)
    }

    @Test
    fun realManagedQueryIncludesOwnedOrganizationAndUsesStatusOwnerAndCursorFilters() {
        val principal = UserPrincipal(USER_ID, "activity-read-managed-it")

        val allActivities = service.listManagedActivities(principal, null, 50, null, null)
        assertThat(allActivities.items).extracting(Function {  it.activityId  })
            .contains(PERSONAL_ACTIVITY_ID.toString(), ORGANIZATION_ACTIVITY_ID.toString())

        val firstPage = service.listManagedActivities(principal, null, 2, null, null)
        assertThat(firstPage.items).hasSize(2)
        assertThat(firstPage.nextCursor).isNotBlank()

        val secondPage = service.listManagedActivities(principal, firstPage.nextCursor, 2, null, null)
        val pageIds = mutableListOf<String>()
        pageIds.addAll(firstPage.items.map { it.activityId })
        pageIds.addAll(secondPage.items.map { it.activityId })
        assertThat(pageIds).doesNotHaveDuplicates()

        assertThat(service.listManagedActivities(principal, null, 50, "DRAFT", null).items)
            .extracting(Function {  it.activityId  })
            .containsExactly(DRAFT_ACTIVITY_ID.toString())
        assertThat(service.listManagedActivities(principal, null, 50, null, "ORGANIZATION").items)
            .extracting(Function {  it.activityId  })
            .containsExactly(ORGANIZATION_ACTIVITY_ID.toString())
    }

    @Test
    fun realManagedDetailReturnsFullOwnerScopedDetailsAcrossActivityStatuses() {
        val principal = UserPrincipal(USER_ID, "activity-read-managed-detail-it")

        val personal = service.getManagedActivity(principal, PERSONAL_ACTIVITY_ID)
        assertThat(personal.status).isEqualTo("PUBLISHED")
        assertThat(personal.owner.ownerType).isEqualTo("USER")
        assertThat(personal.owner.displayName).isEqualTo("测试用户")
        assertThat(personal.description).isEqualTo("活动介绍")
        assertThat(personal.media).extracting(Function {  it.fileId  })
            .containsExactly("9911005", "9911006")
        assertThat(personal.signupDetails).isEqualTo("个人报名说明")
        assertThat(personal.organizerMessage).isEqualTo("组织者留言")
        assertThat(personal.latitude).isEqualByComparingTo(LATITUDE)
        assertThat(personal.longitude).isEqualByComparingTo(LONGITUDE)
        assertThat(personal.version).isZero()

        val organization = service.getManagedActivity(principal, ORGANIZATION_ACTIVITY_ID)
        assertThat(organization.owner.ownerType).isEqualTo("ORGANIZATION")
        assertThat(organization.owner.displayName).isEqualTo("测试企业")
        assertThat(organization.signupDetails).isNull()
        assertThat(organization.organizerMessage).isNull()
        assertThat(organization.latitude).isNull()
        assertThat(organization.longitude).isNull()

        assertThat(service.getManagedActivity(principal, DRAFT_ACTIVITY_ID).status)
            .isEqualTo("DRAFT")
        assertThat(service.getManagedActivity(principal, HIDDEN_ACTIVITY_ID).status)
            .isEqualTo("HIDDEN")
        assertThat(service.getManagedActivity(principal, CANCELLED_ACTIVITY_ID).status)
            .isEqualTo("CANCELLED")
        assertThat(service.getManagedActivity(principal, ENDED_ACTIVITY_ID).status)
            .isEqualTo("ENDED")

        assertThatThrownBy {
            service.getManagedActivity(UserPrincipal(USER_ID + 1, "other-user"), PERSONAL_ACTIVITY_ID)
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND)
        assertThatThrownBy {
            service.getManagedActivity(UserPrincipal(USER_ID + 1, "other-user"), ORGANIZATION_ACTIVITY_ID)
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND)

        jdbcTemplate.update(
            "UPDATE organization_members " +
                "SET status=2, disabled_at=UTC_TIMESTAMP(3) WHERE id=?",
            ORGANIZATION_MEMBER_ID
        )
        assertThatThrownBy { service.getManagedActivity(principal, ORGANIZATION_ACTIVITY_ID) }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND)

        jdbcTemplate.update(
            "UPDATE organization_members SET status=1, disabled_at=NULL WHERE id=?",
            ORGANIZATION_MEMBER_ID
        )
        jdbcTemplate.update("UPDATE organizations SET status=2 WHERE id=?", ORGANIZATION_ID)
        assertThatThrownBy { service.getManagedActivity(principal, ORGANIZATION_ACTIVITY_ID) }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND)
    }

    private fun assertNotPublic(activityId: Long) {
        assertThatThrownBy { service.getPublicActivity(activityId) }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND)
    }

    private fun insertUser() {
        jdbcTemplate.update(
            """
                INSERT INTO users (id, status, version, created_at, updated_at)
                VALUES (?, 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """, USER_ID
        )
    }

    private fun insertProfile(avatarFileId: Long) {
        jdbcTemplate.update(
            """
                INSERT INTO user_profiles (
                    user_id, nickname, avatar_file_id, province_code, province_name,
                    city_code, city_name, district_code, district_name,
                    version, created_at, updated_at
                ) VALUES (?, '测试用户', ?, '44', '广东省', '4403', '深圳市',
                    '440305', '南山区', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """, USER_ID, avatarFileId
        )
    }

    private fun insertOrganization(avatarFileId: Long) {
        jdbcTemplate.update(
            """
                INSERT INTO organizations (
                    id, name, avatar_file_id, province_code, province_name,
                    city_code, city_name, district_code, district_name,
                    address_detail, contact_phone_ciphertext, contact_phone_lookup_hash,
                    contact_phone_last_four, status, version, created_at, updated_at
                ) VALUES (?, '测试企业', ?, '44', '广东省', '4403', '深圳市',
                    '440305', '南山区', '企业地址', X'01',
                    UNHEX(SHA2('activity-read-phone', 256)), '0001', 1, 0,
                    UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """, ORGANIZATION_ID, avatarFileId
        )
    }

    private fun insertOrganizationMember() {
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

    private fun insertFile(fileId: Long): Long {
        jdbcTemplate.update(
            """
                INSERT INTO file_objects (
                    id, uploader_type, uploader_id, purpose, storage_provider, bucket_name,
                    object_key, original_filename, content_type, file_extension,
                    size_bytes, sha256, access_level, scan_status, lifecycle_status,
                    version, created_at, updated_at
                ) VALUES (?, 1, ?, 'ACTIVITY', 'local', 'eligo', ?, 'activity.jpg',
                    'image/jpeg', 'jpg', 1, UNHEX(SHA2(?, 256)),
                    1, 2, 2, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """, fileId, USER_ID, "activity/read/$fileId", "activity-read-$fileId"
        )
        return fileId
    }

    private fun insertActivity(
        activityId: Long,
        ownerUserId: Long?,
        ownerOrganizationId: Long?,
        status: Int,
        title: String,
        category: String?,
        coverFileId: Long?,
        startsAt: LocalDateTime,
        signupDetails: String?,
        organizerMessage: String?
    ) {
        val now = now()
        jdbcTemplate.update(
            """
                INSERT INTO activities (
                    id, owner_user_id, owner_organization_id, operator_user_id, status,
                    title, category_code, description, cover_file_id,
                    registration_starts_at, registration_ends_at, starts_at, ends_at,
                    region_code, address_detail, capacity, participant_count,
                    signup_details, organizer_message, published_at, version,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    '440305', '深圳市南山区活动地址', 20, 2, ?, ?, ?, 0, ?, ?)
                """,
            activityId, ownerUserId, ownerOrganizationId, USER_ID, status,
            title, category, if (status == 1) null else "活动介绍", coverFileId,
            if (status == 1) null else now.minusHours(1),
            if (status == 1) null else now.plusHours(1),
            if (status == 1) null else startsAt,
            if (status == 1) null else startsAt.plusHours(1),
            signupDetails, organizerMessage,
            if (status == 1) null else now, now, now
        )
    }

    private fun insertMedia(activityId: Long, fileId: Long, sortOrder: Int) {
        jdbcTemplate.update(
            """
                INSERT INTO activity_media (id, activity_id, file_id, sort_order, created_at)
                VALUES (?, ?, ?, ?, UTC_TIMESTAMP(3))
                """, fileId + 100000L, activityId, fileId, sortOrder
        )
    }

    private fun now(): LocalDateTime {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS)
    }

    companion object {
        private const val USER_ID = 9910001L
        private const val ORGANIZATION_ID = 9920001L
        private const val PERSONAL_ACTIVITY_ID = 9930001L
        private const val ORGANIZATION_ACTIVITY_ID = 9930002L
        private const val CANCELLED_ACTIVITY_ID = 9930003L
        private const val DRAFT_ACTIVITY_ID = 9930004L
        private const val HIDDEN_ACTIVITY_ID = 9930005L
        private const val ENDED_ACTIVITY_ID = 9930006L
        private const val MAP_SECOND_ACTIVITY_ID = 9930007L
        private const val MAP_OTHER_CATEGORY_ACTIVITY_ID = 9930008L
        private const val MAP_EXPIRED_ACTIVITY_ID = 9930009L
        private const val MAP_OUTSIDE_ACTIVITY_ID = 9930010L
        private const val ORGANIZATION_MEMBER_ID = 9921001L
        private val LATITUDE = BigDecimal("22.5430960")
        private val LONGITUDE = BigDecimal("114.0578650")
    }
}
