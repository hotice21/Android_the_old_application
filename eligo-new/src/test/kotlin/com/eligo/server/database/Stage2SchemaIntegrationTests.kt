package com.eligo.server.database

import java.util.function.Consumer

import com.eligo.server.integration.wechat.WechatRestClientFactory
import com.eligo.server.organization.mapper.OrganizationMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.NestedExceptionUtils
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.sql.SQLException
import java.util.concurrent.atomic.AtomicLong
import java.util.stream.Stream

@SpringBootTest(
    properties = [
        "eligo.security.jwt.signing-key-base64=IB8eHRwbGhkYFxYVFBMSERAPDg0MCwoJCAcGBQQDAgE=",
        "eligo.security.sensitive-data.encryption-key-base64=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
        "eligo.security.sensitive-data.lookup-key-base64=Hx4dHBsaGRgXFhUUExIREA8ODQwLCgkIBwYFBAMCAQA="
    ]
)
@ActiveProfiles("it")
@EnabledIfSystemProperty(named = "eligo.database.tests", matches = "true")
class Stage2SchemaIntegrationTests {

    @Suppress("unused")
    private companion object {
        private val EXPECTED_TABLES = setOf(
            "users", "user_wechat_accounts", "user_phone_bindings",
            "user_devices", "user_login_sessions", "agreements",
            "agreement_consents", "user_profiles", "interest_tags",
            "user_interest_tags", "user_profile_change_logs",
            "user_data_requests", "user_data_request_events",
            "account_security_events", "file_objects",
            "organizations", "organization_members",
            "activities", "activity_media", "activity_lifecycle_events",
            "activity_create_idempotency_tombstones", "activity_participations",
            "user_follows", "organization_follows", "posts", "post_media",
            "post_status_events", "post_recommendation_index_jobs"
        )

        private val EXPECTED_UNIQUE_INDEXES = setOf(
            "uk_wechat_active_identity", "uk_wechat_active_user_app",
            "uk_phone_active_user", "uk_phone_active_number",
            "uk_session_key", "uk_session_refresh_hash", "uk_session_active_device",
            "uk_agreement_type_version", "uk_agreement_effective_type",
            "uk_consent_user_agreement", "uk_user_interest",
            "uk_data_request_active", "uk_file_object_key",
            "uk_profile_email_lookup_hash", "uk_organization_active_owner",
            "uk_organization_member_active_user", "uk_activity_media_file",
            "uk_activity_media_sort", "uk_activity_participation_activity_user",
            "uk_user_follow_pair", "uk_organization_follow_pair",
            "uk_post_create_idempotency", "uk_post_media_sort", "uk_post_media_file"
        )

        private val IDENTIFIER_SEQUENCE = AtomicLong(10_000)

        @JvmStatic
        fun invalidStateStatements(): Stream<String> = Stream.of(
            "INSERT INTO users (id, status, version, created_at, updated_at) VALUES (900001, 0, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))",
            "INSERT INTO users (id, status, version, created_at, updated_at) VALUES (900002, 1, -1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))",
            "INSERT INTO agreements (id, agreement_type, version_code, title, content, content_hash, status, requires_reconsent, version, created_at, updated_at) VALUES (900003, 1, 'invalid-status', '标题', '正文', UNHEX(SHA2('agreement-invalid', 256)), 9, 0, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))",
            "INSERT INTO file_objects (id, uploader_type, uploader_id, storage_provider, bucket_name, object_key, original_filename, content_type, file_extension, size_bytes, sha256, access_level, scan_status, lifecycle_status, version, created_at, updated_at) VALUES (900004, 1, 1, 'local', 'eligo', 'invalid-size', 'a.txt', 'text/plain', 'txt', 0, UNHEX(SHA2('file-invalid', 256)), 1, 1, 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))",
            "INSERT INTO file_objects (id, uploader_type, uploader_id, purpose, storage_provider, bucket_name, object_key, original_filename, content_type, file_extension, size_bytes, sha256, access_level, scan_status, lifecycle_status, version, created_at, updated_at) VALUES (900005, 1, 1, 'UNKNOWN', 'local', 'eligo', 'invalid-purpose', 'a.jpg', 'image/jpeg', 'jpg', 1, UNHEX(SHA2('file-invalid-purpose', 256)), 1, 1, 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))"
        )
    }

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var organizationMapper: OrganizationMapper

    @MockitoBean
    private lateinit var redis: StringRedisTemplate

    @MockitoBean
    private lateinit var wechatRestClientFactory: WechatRestClientFactory

    @BeforeEach
    fun cleanDatabase() {
        Stage2TestDatabaseCleaner.cleanAllStage2Tables(jdbcTemplate)
    }

    @Test
    fun flywayCreatesExactlyTheStage2Tables() {
        val tables = jdbcTemplate.queryForList(
            """
            SELECT TABLE_NAME
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME <> 'flyway_schema_history'
            """, String::class.java
        )

        assertThat(HashSet(tables)).containsExactlyInAnyOrderElementsOf(EXPECTED_TABLES)
    }

    @Test
    fun activeWechatPhoneSessionAgreementAndRequestConstraintsAreUnique() {
        val indexes = jdbcTemplate.queryForList(
            """
            SELECT DISTINCT INDEX_NAME
            FROM INFORMATION_SCHEMA.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND NON_UNIQUE = 0
              AND INDEX_NAME <> 'PRIMARY'
            """, String::class.java
        )

        assertThat(indexes).containsAll(EXPECTED_UNIQUE_INDEXES)
    }

    @Test
    fun checkConstraintsArePresent() {
        val checkCount = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = DATABASE()
              AND CONSTRAINT_TYPE = 'CHECK'
            """, Int::class.java
        )

        assertThat(checkCount).isNotNull.isGreaterThanOrEqualTo(15)
    }

    @Test
    fun migrationsSeedTenEnabledInterestTags() {
        val tags =
            jdbcTemplate.queryForList(
                """
                SELECT CONCAT(tag_code, ':', tag_name)
                FROM interest_tags
                WHERE status=1
                ORDER BY sort_order, id
                """,
                String::class.java
            )

        assertThat(tags)
            .containsExactly(
                "OUTDOOR:户外",
                "SPORTS:运动",
                "FOOD:美食",
                "TRAVEL:旅行",
                "MUSIC:音乐",
                "MOVIE:电影",
                "READING:阅读",
                "PHOTOGRAPHY:摄影",
                "GAMING:游戏",
                "PETS:宠物"
            )
    }

    @ParameterizedTest
    @MethodSource("invalidStateStatements")
    fun invalidStatesAndVersionsAreRejected(statement: String) {
        assertThatThrownBy { jdbcTemplate.update(statement) }
            .satisfies(Consumer {  throwable ->
                val rootCause = NestedExceptionUtils.getMostSpecificCause(throwable)
                assertThat(rootCause).isInstanceOf(SQLException::class.java)

                val sqlException = rootCause as SQLException
                assertThat(sqlException.errorCode).isEqualTo(3819)
                assertThat(sqlException.sqlState).isEqualTo("HY000")
             })
    }

    @Test
    fun twoActiveBindingsForTheSamePhoneAreRejected() {
        val firstUserId = insertUser()
        val secondUserId = insertUser()
        val phoneHash = hashFor(101)

        insertPhoneBinding(firstUserId, phoneHash)

        assertUniqueConstraintViolation { insertPhoneBinding(secondUserId, phoneHash) }
    }

    @Test
    fun twoProfilesCannotUseTheSameNormalizedEmailHash() {
        val firstUserId = insertUser()
        val secondUserId = insertUser()
        val emailHash = hashFor(201)

        insertProfileEmail(firstUserId, emailHash)

        assertUniqueConstraintViolation { insertProfileEmail(secondUserId, emailHash) }
    }

    @Test
    fun organizationMapperReturnsOnlyActiveOwnerOfActiveOrganization() {
        val activeUserId = insertUser()
        val activeOrganizationId = insertOrganization()
        insertOrganizationMember(activeOrganizationId, activeUserId, 1)

        assertThat(organizationMapper.findActiveOwnedByUserId(activeUserId))
            .singleElement()
            .satisfies(Consumer {  organization ->
                assertThat(organization.id)
                    .isEqualTo(activeOrganizationId)
                assertThat(organization.name)
                    .isEqualTo("测试企业$activeOrganizationId")
             })

        jdbcTemplate.update(
            "UPDATE organizations SET status=2 WHERE id=?",
            activeOrganizationId
        )
        assertThat(organizationMapper.findActiveOwnedByUserId(activeUserId))
            .isEmpty()

        val disabledUserId = insertUser()
        val disabledOrganizationId = insertOrganization()
        insertOrganizationMember(disabledOrganizationId, disabledUserId, 2)
        assertThat(organizationMapper.findActiveOwnedByUserId(disabledUserId))
            .isEmpty()
    }

    @Test
    fun oneOrganizationCannotHaveTwoActiveOwners() {
        val firstUserId = insertUser()
        val secondUserId = insertUser()
        val organizationId = insertOrganization()

        insertOrganizationMember(organizationId, firstUserId, 1)

        assertUniqueConstraintViolation(
            { insertOrganizationMember(organizationId, secondUserId, 1) }
        )
    }

    @Test
    fun oneUserCannotOwnTwoActiveOrganizations() {
        val userId = insertUser()
        val firstOrganizationId = insertOrganization()
        val secondOrganizationId = insertOrganization()

        insertOrganizationMember(firstOrganizationId, userId, 1)

        assertUniqueConstraintViolation(
            { insertOrganizationMember(secondOrganizationId, userId, 1) }
        )
    }

    @Test
    fun disabledOwnerRelationDoesNotConsumeActiveUniqueness() {
        val firstUserId = insertUser()
        val secondUserId = insertUser()
        val organizationId = insertOrganization()

        insertOrganizationMember(organizationId, firstUserId, 2)

        val inserted = insertOrganizationMember(organizationId, secondUserId, 1)
        assertThat(inserted).isEqualTo(1)
    }

    @Test
    fun twoActiveSessionsForTheSameDeviceAreRejected() {
        val userId = insertUser()
        val deviceId = insertDevice(userId)

        insertSession(userId, deviceId)

        assertUniqueConstraintViolation { insertSession(userId, deviceId) }
    }

    @Test
    fun twoEffectiveAgreementsOfTheSameTypeAreRejected() {
        insertAgreement(1)

        assertUniqueConstraintViolation { insertAgreement(1) }
    }

    @Test
    fun retiredAgreementKeepsItsOriginalEffectiveTime() {
        val agreementId = insertAgreementAndReturnId(2)

        jdbcTemplate.update(
            """
            UPDATE agreements
            SET status = 4,
                retired_at = UTC_TIMESTAMP(3),
                updated_at = UTC_TIMESTAMP(3)
            WHERE id = ?
            """, agreementId
        )

        val retained = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM agreements
            WHERE id = ? AND status = 4 AND effective_at IS NOT NULL
            """, Int::class.java, agreementId
        )
        assertThat(retained).isEqualTo(1)
    }

    @Test
    fun twoUnfinishedRequestsOfTheSameTypeForOneUserAreRejected() {
        val userId = insertUser()

        insertDataRequest(userId, 2)

        assertUniqueConstraintViolation { insertDataRequest(userId, 2) }
    }

    private fun assertUniqueConstraintViolation(operation: Runnable) {
        assertThatThrownBy { operation.run() }
            .satisfies(Consumer {  throwable ->
                assertThat(throwable).isInstanceOf(DataIntegrityViolationException::class.java)

                val rootCause = NestedExceptionUtils.getMostSpecificCause(throwable)
                assertThat(rootCause).isInstanceOf(SQLException::class.java)

                val sqlException = rootCause as SQLException
                assertThat(sqlException.errorCode).isEqualTo(1062)
                assertThat(sqlException.sqlState).isEqualTo("23000")
             })
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

    private fun insertDevice(userId: Long): Long {
        val id = nextIdentifier()
        jdbcTemplate.update(
            """
            INSERT INTO user_devices (
                id, user_id, installation_id_hash, device_name, platform_code,
                status, first_seen_at, last_seen_at, status_changed_at, created_at, updated_at
            ) VALUES (?, ?, UNHEX(SHA2(?, 256)), '测试设备', 'ios',
                1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """, id, userId, "device-$id"
        )
        return id
    }

    private fun insertPhoneBinding(userId: Long, phoneHash: String) {
        val id = nextIdentifier()
        jdbcTemplate.update(
            """
            INSERT INTO user_phone_bindings (
                id, user_id, country_code, phone_ciphertext, phone_lookup_hash,
                phone_last_four, status, bound_at, created_at, updated_at
            ) VALUES (?, ?, '+86', X'01', UNHEX(?), '0001', 1,
                UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """, id, userId, phoneHash
        )
    }

    private fun insertProfileEmail(userId: Long, emailHash: String) {
        jdbcTemplate.update(
            """
            INSERT INTO user_profiles (
                user_id, email_ciphertext, email_lookup_hash,
                version, created_at, updated_at
            ) VALUES (?, X'01', UNHEX(?), 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """, userId, emailHash
        )
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
                '440305', '南山区', '测试地址',
                X'01', UNHEX(SHA2(?, 256)), '0001',
                1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """, id, "测试企业$id", "organization-phone-$id"
        )
        return id
    }

    private fun insertOrganizationMember(organizationId: Long, userId: Long, status: Int): Int {
        val id = nextIdentifier()
        if (status == 1) {
            return jdbcTemplate.update(
                """
                INSERT INTO organization_members (
                    id, organization_id, user_id, role_code, status,
                    enabled_at, version, created_at, updated_at
                ) VALUES (?, ?, ?, 1, 1,
                    UTC_TIMESTAMP(3), 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """, id, organizationId, userId
            )
        }
        return jdbcTemplate.update(
            """
            INSERT INTO organization_members (
                id, organization_id, user_id, role_code, status,
                enabled_at, disabled_at, version, created_at, updated_at
            ) VALUES (?, ?, ?, 1, 2,
                UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0,
                UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """, id, organizationId, userId
        )
    }

    private fun insertSession(userId: Long, deviceId: Long) {
        val id = nextIdentifier()
        jdbcTemplate.update(
            """
            INSERT INTO user_login_sessions (
                id, user_id, device_id, session_key, refresh_token_hash,
                refresh_token_version, status, expires_at, version, created_at, updated_at
            ) VALUES (?, ?, ?, ?, UNHEX(SHA2(?, 256)), 0, 1,
                DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 1 DAY), 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """, id, userId, deviceId, "session-$id", "refresh-$id"
        )
    }

    private fun insertAgreement(agreementType: Int) {
        insertAgreementAndReturnId(agreementType)
    }

    private fun insertAgreementAndReturnId(agreementType: Int): Long {
        val id = nextIdentifier()
        jdbcTemplate.update(
            """
            INSERT INTO agreements (
                id, agreement_type, version_code, title, content, content_hash,
                status, requires_reconsent, effective_at, version, created_at, updated_at
            ) VALUES (?, ?, ?, '用户协议', '协议正文', UNHEX(SHA2(?, 256)),
                3, 1, UTC_TIMESTAMP(3), 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """, id, agreementType, "v$id", "agreement-$id"
        )
        return id
    }

    private fun insertDataRequest(userId: Long, requestType: Int) {
        val id = nextIdentifier()
        jdbcTemplate.update(
            """
            INSERT INTO user_data_requests (
                id, user_id, request_type, status, requested_at, execute_after,
                retry_count, version, created_at, updated_at
            ) VALUES (?, ?, ?, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3),
                0, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """, id, userId, requestType
        )
    }

    private fun nextIdentifier(): Long {
        return IDENTIFIER_SEQUENCE.incrementAndGet()
    }

    private fun hashFor(value: Long): String {
        return String.format("%064x", value)
    }
}
