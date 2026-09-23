package com.eligo.server.organization.seed

import com.eligo.server.account.service.AccountStateLockService
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("local", "docker", "it")
class DevOrganizationSeedService(
    private val jdbcTemplate: JdbcTemplate,
    private val properties: DevOrganizationSeedProperties,
    private val accountStates: AccountStateLockService
) {

    @Transactional
    fun seedConfigured() {
        seedInternal(properties.ownerUserId)
    }

    @Transactional
    fun seed(ownerUserId: Long) {
        seedInternal(ownerUserId)
    }

    private fun seedInternal(ownerUserId: Long) {
        val organizationId = properties.organizationId
        val organizationMemberId = properties.organizationMemberId
        requirePositiveIdentifiers(organizationId, organizationMemberId, ownerUserId)
        accountStates.lockActive(ownerUserId)
        requireEligibleOwner(ownerUserId)
        ensureOrganization(organizationId)
        ensureOwnerRelation(organizationId, organizationMemberId, ownerUserId)
    }

    private fun requirePositiveIdentifiers(
        organizationId: Long,
        organizationMemberId: Long,
        ownerUserId: Long
    ) {
        if (organizationId <= 0 || organizationMemberId <= 0 || ownerUserId <= 0) {
            throw IllegalStateException("开发企业种子编号配置无效")
        }
    }

    private fun requireEligibleOwner(ownerUserId: Long) {
        val activeUsers = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE id=? AND status=1",
            Int::class.java,
            ownerUserId
        )
        if (activeUsers == null || activeUsers != 1) {
            throw IllegalStateException("测试负责人用户不存在")
        }

        val completedProfiles = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_profiles WHERE user_id=? AND completed_at IS NOT NULL",
            Int::class.java,
            ownerUserId
        )
        if (completedProfiles == null || completedProfiles != 1) {
            throw IllegalStateException("测试负责人个人资料未完成")
        }
    }

    private fun ensureOrganization(organizationId: Long) {
        val names = jdbcTemplate.query(
            "SELECT name FROM organizations WHERE id=? FOR UPDATE",
            { rs, _ -> rs.getString(1) },
            organizationId
        )
        if (names.isEmpty()) {
            jdbcTemplate.update(
                """
                INSERT INTO organizations (
                    id, name, summary, province_code, province_name,
                    city_code, city_name, district_code, district_name,
                    address_detail, contact_phone_ciphertext,
                    contact_phone_lookup_hash, contact_phone_last_four,
                    status, version, created_at, updated_at
                ) VALUES (
                    ?, ?, ?, '44', '广东省', '4403', '深圳市',
                    '440305', '南山区', '仅用于本地开发测试', X'01',
                    UNHEX(SHA2(?, 256)), '0001', 1, 0,
                    UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
                )
                """,
                organizationId,
                ORGANIZATION_NAME,
                ORGANIZATION_SUMMARY,
                CONTACT_PLACEHOLDER
            )
            return
        }

        if (ORGANIZATION_NAME != names[0]) {
            throw IllegalStateException("固定开发企业编号已被其他数据占用")
        }

        jdbcTemplate.update(
            """
            UPDATE organizations
            SET summary=?, province_code='44', province_name='广东省',
                city_code='4403', city_name='深圳市', district_code='440305',
                district_name='南山区', address_detail='仅用于本地开发测试',
                contact_phone_ciphertext=X'01',
                contact_phone_lookup_hash=UNHEX(SHA2(?, 256)),
                contact_phone_last_four='0001', status=1,
                updated_at=UTC_TIMESTAMP(3)
            WHERE id=?
            """,
            ORGANIZATION_SUMMARY,
            CONTACT_PLACEHOLDER,
            organizationId
        )
    }

    private fun ensureOwnerRelation(
        organizationId: Long,
        organizationMemberId: Long,
        ownerUserId: Long
    ) {
        val activeOwnerIds = jdbcTemplate.query(
            "SELECT user_id FROM organization_members WHERE organization_id=? AND role_code=1 AND status=1 FOR UPDATE",
            { rs, _ -> rs.getLong(1) },
            organizationId
        )
        if (activeOwnerIds.any { userId -> userId != ownerUserId }) {
            throw IllegalStateException("开发企业已有其他有效负责人")
        }

        val activeOrganizationIds = jdbcTemplate.query(
            "SELECT organization_id FROM organization_members WHERE user_id=? AND role_code=1 AND status=1 FOR UPDATE",
            { rs, _ -> rs.getLong(1) },
            ownerUserId
        )
        if (activeOrganizationIds.any { existingId -> existingId != organizationId }) {
            throw IllegalStateException("测试负责人已绑定其他有效企业")
        }

        val existingRelations = jdbcTemplate.queryForList(
            "SELECT id FROM organization_members WHERE organization_id=? AND user_id=? ORDER BY id DESC LIMIT 1",
            organizationId,
            ownerUserId
        )
        if (existingRelations.isNotEmpty()) {
            val relationId = (existingRelations[0]["id"] as Number).toLong()
            jdbcTemplate.update(
                "UPDATE organization_members SET status=1, disabled_at=NULL, updated_at=UTC_TIMESTAMP(3) WHERE id=?",
                relationId
            )
            return
        }

        val occupiedIdentifiers = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM organization_members WHERE id=?",
            Int::class.java,
            organizationMemberId
        )
        if (occupiedIdentifiers != null && occupiedIdentifiers != 0) {
            throw IllegalStateException("固定开发负责人关系编号已被占用")
        }

        jdbcTemplate.update(
            """
            INSERT INTO organization_members (
                id, organization_id, user_id, role_code, status,
                enabled_at, disabled_at, version, created_at, updated_at
            ) VALUES (?, ?, ?, 1, 1, UTC_TIMESTAMP(3), NULL, 0,
                UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """,
            organizationMemberId,
            organizationId,
            ownerUserId
        )
    }

    companion object {
        private const val ORGANIZATION_NAME = "山海户外"
        private const val ORGANIZATION_SUMMARY = "仅用于本地开发测试的企业主体"
        private const val CONTACT_PLACEHOLDER = "eligo-dev-organization-contact"
    }
}
