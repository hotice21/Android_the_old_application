package com.eligo.server.database

import java.util.function.Consumer

import com.eligo.server.integration.wechat.WechatRestClientFactory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.NestedExceptionUtils
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.sql.SQLException
import java.util.concurrent.atomic.AtomicLong

@SpringBootTest(
    properties = [
        "eligo.security.jwt.signing-key-base64=IB8eHRwbGhkYFxYVFBMSERAPDg0MCwoJCAcGBQQDAgE=",
        "eligo.security.sensitive-data.encryption-key-base64=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
        "eligo.security.sensitive-data.lookup-key-base64=Hx4dHBsaGRgXFhUUExIREA8ODQwLCgkIBwYFBAMCAQA="
    ]
)
@ActiveProfiles("it")
@EnabledIfSystemProperty(named = "eligo.database.tests", matches = "true")
class ActivitySchemaIntegrationTests {

    private companion object {
        val IDENTIFIER_SEQUENCE = AtomicLong(
            System.currentTimeMillis() * 10_000L + 3_000L
        )
    }

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @MockitoBean
    private lateinit var redis: StringRedisTemplate

    @MockitoBean
    private lateinit var wechatRestClientFactory: WechatRestClientFactory

    @Test
    fun migrationCreatesActivityTablesAndIndexes() {
        val tables = jdbcTemplate.queryForList(
            """
            SELECT TABLE_NAME
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_SCHEMA=DATABASE()
              AND TABLE_NAME IN (
                  'activities','activity_media','activity_lifecycle_events',
                  'activity_create_idempotency_tombstones','activity_participations'
              )
            ORDER BY TABLE_NAME
            """, String::class.java
        )

        assertThat(tables).containsExactly(
            "activities", "activity_create_idempotency_tombstones",
            "activity_lifecycle_events", "activity_media",
            "activity_participations"
        )

        val indexes = HashSet(
            jdbcTemplate.queryForList(
                """
                SELECT DISTINCT INDEX_NAME
                FROM INFORMATION_SCHEMA.STATISTICS
                WHERE TABLE_SCHEMA=DATABASE()
                  AND TABLE_NAME IN (
                      'activities','activity_media','activity_lifecycle_events',
                      'activity_create_idempotency_tombstones','activity_participations'
                  )
                """, String::class.java
            )
        )

        assertThat(indexes).contains(
            "idx_activity_public_start",
            "idx_activity_public_category_start",
            "idx_activity_public_region_start",
            "idx_activity_owner_user_created",
            "idx_activity_owner_organization_created",
            "idx_activity_owner_user_updated",
            "idx_activity_owner_organization_updated",
            "uk_activity_media_file",
            "uk_activity_media_sort",
            "idx_activity_media_file",
            "uk_activity_create_idempotency",
            "idx_activity_due_end",
            "idx_activity_create_tombstone_expires",
            "idx_activity_event_activity_created",
            "uk_activity_participation_activity_user",
            "idx_activity_participant_page",
            "idx_user_participation_page",
            "idx_activity_contact_qr_file"
        )

        assertThat(indexColumns("activities", "idx_activity_public_region_start"))
            .containsExactly("status", "region_code", "starts_at", "id")
        assertThat(indexColumns("activities", "idx_activity_owner_user_updated"))
            .containsExactly("owner_user_id", "updated_at", "id")
        assertThat(indexColumns("activities", "idx_activity_owner_organization_updated"))
            .containsExactly("owner_organization_id", "updated_at", "id")
        assertThat(indexColumns("activity_participations", "idx_activity_participant_page"))
            .containsExactly("activity_id", "status", "joined_at", "user_id")
        assertThat(indexColumns("activity_participations", "idx_user_participation_page"))
            .containsExactly("user_id", "status", "joined_at", "id")

        val purposeColumnType = jdbcTemplate.queryForObject(
            """
            SELECT COLUMN_TYPE
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA=DATABASE()
              AND TABLE_NAME='file_objects'
              AND COLUMN_NAME='purpose'
            """, String::class.java
        )
        assertThat(purposeColumnType).isEqualTo("varchar(32)")

        val restrictForeignKeys = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA=DATABASE()
              AND TABLE_NAME IN (
                  'activities','activity_media','activity_lifecycle_events',
                  'activity_participations'
              )
              AND DELETE_RULE='RESTRICT'
            """, Int::class.java
        )

        assertThat(restrictForeignKeys).isEqualTo(11)
    }

    @Test
    fun activityContactFieldsAllowPrivateQrAndOnlyThreeRegistrationGenders() {
        val userId = insertUser()
        val coverFileId = insertFile(userId)
        val activityId = insertPublishedActivity(userId, coverFileId)
        val qrFileId = insertContactQrFile(userId)

        jdbcTemplate.update(
            """
            UPDATE activities
            SET registration_gender=3,
                organizer_phone_ciphertext=?,
                organizer_wechat_ciphertext=?,
                organizer_wechat_qr_file_id=?
            WHERE id=?
            """, byteArrayOf(1, 2), byteArrayOf(3, 4), qrFileId, activityId
        )

        val registrationGender = jdbcTemplate.queryForObject(
            "SELECT registration_gender FROM activities WHERE id=?",
            Int::class.java,
            activityId
        )
        val qrAccessLevel = jdbcTemplate.queryForObject(
            "SELECT access_level FROM file_objects WHERE id=?",
            Int::class.java,
            qrFileId
        )
        assertThat(registrationGender).isEqualTo(3)
        assertThat(qrAccessLevel).isEqualTo(2)

        assertCheckViolation {
            jdbcTemplate.update(
                "UPDATE activities SET registration_gender=4 WHERE id=?",
                activityId
            )
        }
    }

    @Test
    fun participationRowsEnforceUniqueUserAndStateShape() {
        val ownerUserId = insertUser()
        val participantUserId = insertUser()
        val fileId = insertFile(ownerUserId)
        val activityId = insertPublishedActivity(ownerUserId, fileId)
        val participationId = nextIdentifier()

        jdbcTemplate.update(
            """
            INSERT INTO activity_participations (
                id, activity_id, user_id, status, joined_at,
                version, created_at, updated_at
            ) VALUES (?, ?, ?, 1, UTC_TIMESTAMP(3),
                0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """, participationId, activityId, participantUserId
        )

        assertUniqueViolation {
            jdbcTemplate.update(
                """
                INSERT INTO activity_participations (
                    id, activity_id, user_id, status, joined_at,
                    version, created_at, updated_at
                ) VALUES (?, ?, ?, 1, UTC_TIMESTAMP(3),
                    0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """, nextIdentifier(), activityId, participantUserId
            )
        }

        assertCheckViolation {
            jdbcTemplate.update(
                """
                UPDATE activity_participations
                SET status=2, cancelled_at=NULL
                WHERE id=?
                """, participationId
            )
        }
        assertCheckViolation {
            jdbcTemplate.update(
                """
                UPDATE activity_participations
                SET status=3, cancelled_at=NULL,
                    terminated_at=UTC_TIMESTAMP(3), termination_reason=NULL
                WHERE id=?
                """, participationId
            )
        }
        assertCheckViolation {
            jdbcTemplate.update(
                """
                UPDATE activity_participations
                SET status=1, termination_reason=2
                WHERE id=?
                """, participationId
            )
        }
    }

    @Test
    fun draftRequiresExactlyOneOwnerMatchingPersonalOperatorAndNonBlankTitle() {
        val ownerUserId = insertUser()
        val otherUserId = insertUser()
        val organizationId = insertOrganization()

        assertCheckViolation {
            jdbcTemplate.update(
                """
                INSERT INTO activities (
                    id, operator_user_id, status, title,
                    participant_count, version, created_at, updated_at
                ) VALUES (?, ?, 1, '草稿', 0, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """, nextIdentifier(), ownerUserId
            )
        }

        assertCheckViolation {
            jdbcTemplate.update(
                """
                INSERT INTO activities (
                    id, owner_user_id, owner_organization_id, operator_user_id,
                    status, title, participant_count, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 1, '草稿', 0, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """, nextIdentifier(), ownerUserId, organizationId, ownerUserId
            )
        }

        assertCheckViolation {
            jdbcTemplate.update(
                """
                INSERT INTO activities (
                    id, owner_user_id, operator_user_id, status, title,
                    participant_count, version, created_at, updated_at
                ) VALUES (?, ?, ?, 1, '草稿', 0, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """, nextIdentifier(), ownerUserId, otherUserId
            )
        }

        assertCheckViolation {
            jdbcTemplate.update(
                """
                INSERT INTO activities (
                    id, owner_user_id, operator_user_id, status, title,
                    participant_count, version, created_at, updated_at
                ) VALUES (?, ?, ?, 1, '   ', 0, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """, nextIdentifier(), ownerUserId, ownerUserId
            )
        }

        val validDraftId = insertDraft(ownerUserId)
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM activities WHERE id=? AND status=1",
            Int::class.java,
            validDraftId
        )
        assertThat(count).isEqualTo(1)
    }

    @Test
    fun publishedActivityRequiresCompleteFieldsAndOrderedTimes() {
        val userId = insertUser()
        val fileId = insertFile(userId)

        assertCheckViolation {
            jdbcTemplate.update(
                """
                INSERT INTO activities (
                    id, owner_user_id, operator_user_id, status, title,
                    participant_count, published_at, version, created_at, updated_at
                ) VALUES (?, ?, ?, 2, '缺字段活动', 0,
                    UTC_TIMESTAMP(3), 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """, nextIdentifier(), userId, userId
            )
        }

        val activityId = insertPublishedActivity(userId, fileId)

        assertCheckViolation {
            jdbcTemplate.update(
                """
                UPDATE activities
                SET registration_ends_at=registration_starts_at
                WHERE id=?
                """, activityId
            )
        }

        assertCheckViolation {
            jdbcTemplate.update(
                "UPDATE activities SET published_at=NULL WHERE id=?", activityId
            )
        }

        val draftId = insertDraft(userId)
        assertCheckViolation {
            jdbcTemplate.update(
                "UPDATE activities SET published_at=UTC_TIMESTAMP(3) WHERE id=?", draftId
            )
        }
    }

    @Test
    fun capacityAndOrganizationSpecificFieldsAreConstrained() {
        val userId = insertUser()
        val organizationId = insertOrganization()
        val fileId = insertFile(userId)
        val activityId = insertPublishedActivity(userId, fileId)

        assertCheckViolation {
            jdbcTemplate.update(
                "UPDATE activities SET participant_count=capacity+1 WHERE id=?", activityId
            )
        }
        assertCheckViolation {
            jdbcTemplate.update(
                "UPDATE activities SET capacity=0 WHERE id=?", activityId
            )
        }

        assertCheckViolation {
            jdbcTemplate.update(
                """
                INSERT INTO activities (
                    id, owner_organization_id, operator_user_id, status, title,
                    signup_details, participant_count, version, created_at, updated_at
                ) VALUES (?, ?, ?, 1, '企业草稿', '个人报名说明',
                    0, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """, nextIdentifier(), organizationId, userId
            )
        }
    }

    @Test
    fun statusVersionAndContentLengthsAreConstrained() {
        val userId = insertUser()

        assertCheckViolation {
            jdbcTemplate.update(
                """
                INSERT INTO activities (
                    id, owner_user_id, operator_user_id, status, title,
                    participant_count, version, created_at, updated_at
                ) VALUES (?, ?, ?, 6, '错误状态', 0, 0,
                    UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """, nextIdentifier(), userId, userId
            )
        }

        assertCheckViolation {
            jdbcTemplate.update(
                """
                INSERT INTO activities (
                    id, owner_user_id, operator_user_id, status, title,
                    participant_count, version, created_at, updated_at
                ) VALUES (?, ?, ?, 1, '错误版本', 0, -1,
                    UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """, nextIdentifier(), userId, userId
            )
        }

        assertDataTooLong {
            jdbcTemplate.update(
                """
                INSERT INTO activities (
                    id, owner_user_id, operator_user_id, status, title,
                    participant_count, version, created_at, updated_at
                ) VALUES (?, ?, ?, 1, ?, 0, 0,
                    UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """, nextIdentifier(), userId, userId, "题".repeat(21)
            )
        }

        val draftId = insertDraft(userId)
        assertCheckViolation {
            jdbcTemplate.update(
                "UPDATE activities SET description=? WHERE id=?",
                "介绍".repeat(2_501),
                draftId
            )
        }
    }

    @Test
    fun mediaRowsEnforceFileSortAndFiveImageLimit() {
        val userId = insertUser()
        val activityId = insertDraft(userId)
        val firstFileId = insertFile(userId)
        val secondFileId = insertFile(userId)

        insertMedia(activityId, firstFileId, 1)

        assertUniqueViolation { insertMedia(activityId, firstFileId, 2) }
        assertUniqueViolation { insertMedia(activityId, secondFileId, 1) }
        assertCheckViolation { insertMedia(activityId, secondFileId, 6) }
    }

    @Test
    fun createIdempotencyKeyIsUniqueWithinItsScope() {
        val userId = insertUser()
        val scope = "USER:" + userId + ":" + userId
        val firstId = insertDraft(userId)
        jdbcTemplate.update(
            """
            UPDATE activities
            SET create_idempotency_scope=?, create_idempotency_key=?,
                create_idempotency_fingerprint=?
            WHERE id=?
            """, scope, "schema-key", "schema-fingerprint", firstId
        )

        assertUniqueViolation {
            jdbcTemplate.update(
                """
                INSERT INTO activities (
                    id, owner_user_id, operator_user_id, status, title,
                    participant_count, version, create_idempotency_scope,
                    create_idempotency_key, create_idempotency_fingerprint,
                    created_at, updated_at
                ) VALUES (?, ?, ?, 1, '重复幂等键', 0, 0, ?, ?, ?,
                    UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """, nextIdentifier(), userId, userId, scope,
                "schema-key", "another-fingerprint"
            )
        }
    }

    @Test
    fun lifecycleEventsAllowCreationButRejectInvalidOrUnchangedStatus() {
        val userId = insertUser()
        val activityId = insertDraft(userId)

        jdbcTemplate.update(
            """
            INSERT INTO activity_lifecycle_events (
                id, activity_id, from_status, to_status, operator_user_id, created_at
            ) VALUES (?, ?, NULL, 1, ?, UTC_TIMESTAMP(3))
            """, nextIdentifier(), activityId, userId
        )

        assertCheckViolation {
            jdbcTemplate.update(
                """
                INSERT INTO activity_lifecycle_events (
                    id, activity_id, from_status, to_status, operator_user_id, created_at
                ) VALUES (?, ?, 1, 1, ?, UTC_TIMESTAMP(3))
                """, nextIdentifier(), activityId, userId
            )
        }

        assertCheckViolation {
            jdbcTemplate.update(
                """
                INSERT INTO activity_lifecycle_events (
                    id, activity_id, from_status, to_status, created_at
                ) VALUES (?, ?, 1, 6, UTC_TIMESTAMP(3))
                """, nextIdentifier(), activityId
            )
        }
    }

    private fun insertUser(): Long {
        val id = nextIdentifier()
        jdbcTemplate.update(
            """
            INSERT INTO users (id, status, version, created_at, updated_at)
            VALUES (?, 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """, id
        )
        return id
    }

    private fun insertOrganization(): Long {
        val id = nextIdentifier()
        jdbcTemplate.update(
            """
            INSERT INTO organizations (
                id, name, province_code, province_name, city_code, city_name,
                district_code, district_name, address_detail,
                contact_phone_ciphertext, contact_phone_lookup_hash,
                contact_phone_last_four, status, version, created_at, updated_at
            ) VALUES (?, ?, '44', '广东省', '4403', '深圳市',
                '440305', '南山区', '测试地址', X'01',
                UNHEX(SHA2(?, 256)), '0001', 1, 0,
                UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """, id, "测试企业$id", "organization-phone-$id"
        )
        return id
    }

    private fun insertFile(userId: Long): Long {
        val id = nextIdentifier()
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
            """, id, userId, "activity/$id", "activity-file-$id"
        )
        return id
    }

    private fun insertContactQrFile(userId: Long): Long {
        val id = nextIdentifier()
        jdbcTemplate.update(
            """
            INSERT INTO file_objects (
                id, uploader_type, uploader_id, purpose, storage_provider, bucket_name,
                object_key, original_filename, content_type, file_extension,
                size_bytes, sha256, access_level, scan_status, lifecycle_status,
                version, created_at, updated_at
            ) VALUES (?, 1, ?, 'ACTIVITY_CONTACT_QR', 'local', 'eligo', ?, 'qr.png',
                'image/png', 'png', 1, UNHEX(SHA2(?, 256)),
                2, 2, 2, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """, id, userId, "activity-contact-qrs/$id", "contact-qr-$id"
        )
        return id
    }

    private fun insertDraft(userId: Long): Long {
        val id = nextIdentifier()
        jdbcTemplate.update(
            """
            INSERT INTO activities (
                id, owner_user_id, operator_user_id, status, title,
                participant_count, version, created_at, updated_at
            ) VALUES (?, ?, ?, 1, ?, 0, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """, id, userId, userId, "草稿$id"
        )
        return id
    }

    private fun insertPublishedActivity(userId: Long, fileId: Long): Long {
        val id = nextIdentifier()
        jdbcTemplate.update(
            """
            INSERT INTO activities (
                id, owner_user_id, operator_user_id, status, title,
                category_code, description, cover_file_id,
                registration_starts_at, registration_ends_at, starts_at, ends_at,
                region_code, address_detail, capacity, participant_count,
                published_at, version, created_at, updated_at
            ) VALUES (?, ?, ?, 2, ?, 'OUTDOOR', '活动介绍', ?,
                DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 1 HOUR),
                DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 2 HOUR),
                DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 3 HOUR),
                DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 4 HOUR),
                '440305', '深圳市南山区测试地址', 20, 0,
                UTC_TIMESTAMP(3), 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """, id, userId, userId, "发布" + (id % 1_000_000L), fileId
        )
        return id
    }

    private fun insertMedia(activityId: Long, fileId: Long, sortOrder: Int) {
        jdbcTemplate.update(
            """
            INSERT INTO activity_media (id, activity_id, file_id, sort_order, created_at)
            VALUES (?, ?, ?, ?, UTC_TIMESTAMP(3))
            """, nextIdentifier(), activityId, fileId, sortOrder
        )
    }

    private fun assertCheckViolation(operation: Runnable) {
        assertSqlViolation(operation, 3819, "HY000")
    }

    private fun assertUniqueViolation(operation: Runnable) {
        assertSqlViolation(operation, 1062, "23000")
    }

    private fun assertDataTooLong(operation: Runnable) {
        assertSqlViolation(operation, 1406, "22001")
    }

    private fun assertSqlViolation(operation: Runnable, errorCode: Int, sqlState: String) {
        assertThatThrownBy { operation.run() }
            .satisfies(Consumer {  throwable ->
                val rootCause = NestedExceptionUtils.getMostSpecificCause(throwable)
                assertThat(rootCause).isInstanceOf(SQLException::class.java)
                val sqlException = rootCause as SQLException
                assertThat(sqlException.errorCode).isEqualTo(errorCode)
                assertThat(sqlException.sqlState).isEqualTo(sqlState)
             })
    }

    private fun indexColumns(tableName: String, indexName: String): List<String> {
        return jdbcTemplate.queryForList(
            """
            SELECT COLUMN_NAME
            FROM INFORMATION_SCHEMA.STATISTICS
            WHERE TABLE_SCHEMA=DATABASE()
              AND TABLE_NAME=?
              AND INDEX_NAME=?
            ORDER BY SEQ_IN_INDEX
            """, String::class.java, tableName, indexName
        )
    }

    private fun nextIdentifier(): Long {
        return IDENTIFIER_SEQUENCE.incrementAndGet()
    }
}
