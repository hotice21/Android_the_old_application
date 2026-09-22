package com.eligo.server.organization.seed

import com.eligo.server.database.Stage2TestDatabaseCleaner
import com.eligo.server.integration.wechat.WechatRestClientFactory
import java.util.concurrent.atomic.AtomicLong
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean

@SpringBootTest(properties = [
    "eligo.security.jwt.signing-key-base64=IB8eHRwbGhkYFxYVFBMSERAPDg0MCwoJCAcGBQQDAgE=",
    "eligo.security.sensitive-data.encryption-key-base64=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
    "eligo.security.sensitive-data.lookup-key-base64=Hx4dHBsaGRgXFhUUExIREA8ODQwLCgkIBwYFBAMCAQA="
])
@ActiveProfiles("it")
@EnabledIfSystemProperty(named = "eligo.database.tests", matches = "true")
class DevOrganizationSeedIntegrationTests {

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var seedService: DevOrganizationSeedService

    @MockitoBean
    lateinit var redis: StringRedisTemplate

    @MockitoBean
    lateinit var wechatRestClientFactory: WechatRestClientFactory

    private val createdUserIds = mutableListOf<Long>()

    @AfterEach
    fun cleanFixture() {
        Stage2TestDatabaseCleaner.cleanAllStage2Tables(jdbcTemplate)
    }

    @Test
    fun seedsStableOrganizationAndUniqueOwner() {
        val ownerUserId = insertCompletedUser()

        seedService.seed(ownerUserId)

        val organization = jdbcTemplate.queryForMap(
            "SELECT id, name, summary, status FROM organizations WHERE id=?",
            ORGANIZATION_ID)
        assertThat((organization["id"] as Number).toLong())
            .isEqualTo(ORGANIZATION_ID)
        assertThat(organization["name"]).isEqualTo("山海户外")
        assertThat(organization["summary"])
            .isEqualTo("仅用于本地开发测试的企业主体")
        assertThat((organization["status"] as Number).toInt()).isEqualTo(1)

        val member = jdbcTemplate.queryForMap(
            "SELECT organization_id, user_id, role_code, status "
                + "FROM organization_members WHERE organization_id=?",
            ORGANIZATION_ID)
        assertThat((member["organization_id"] as Number).toLong())
            .isEqualTo(ORGANIZATION_ID)
        assertThat((member["user_id"] as Number).toLong())
            .isEqualTo(ownerUserId)
        assertThat((member["role_code"] as Number).toInt()).isEqualTo(1)
        assertThat((member["status"] as Number).toInt()).isEqualTo(1)
    }

    @Test
    fun repeatedSeedDoesNotCreateDuplicateRows() {
        val ownerUserId = insertCompletedUser()

        seedService.seed(ownerUserId)
        seedService.seed(ownerUserId)

        assertThat(count("organizations", "id", ORGANIZATION_ID)).isEqualTo(1)
        assertThat(count("organization_members", "organization_id", ORGANIZATION_ID))
            .isEqualTo(1)
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM organization_members WHERE organization_id=?",
            Int::class.java,
            ORGANIZATION_ID)).isEqualTo(1)
    }

    @Test
    fun disabledOwnerRelationIsReenabledBySeed() {
        val ownerUserId = insertCompletedUser()

        seedService.seed(ownerUserId)
        jdbcTemplate.update(
            "UPDATE organization_members "
                + "SET status=2, disabled_at=UTC_TIMESTAMP(3) "
                + "WHERE organization_id=?",
            ORGANIZATION_ID)

        seedService.seed(ownerUserId)

        val member = jdbcTemplate.queryForMap(
            "SELECT status, disabled_at FROM organization_members WHERE organization_id=?",
            ORGANIZATION_ID)
        assertThat((member["status"] as Number).toInt()).isEqualTo(1)
        assertThat(member["disabled_at"]).isNull()
    }

    @Test
    fun incompleteUserCannotBeBoundAsOrganizationOwner() {
        val ownerUserId = insertUser(false)

        assertThatThrownBy { seedService.seed(ownerUserId) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("个人资料未完成")

        assertThat(count("organizations", "id", ORGANIZATION_ID)).isZero()
    }

    @Test
    fun seedDoesNotReplaceAnotherActiveOwner() {
        val firstOwnerUserId = insertCompletedUser()
        val secondOwnerUserId = insertCompletedUser()

        seedService.seed(firstOwnerUserId)

        assertThatThrownBy { seedService.seed(secondOwnerUserId) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("已有其他有效负责人")

        assertThat(jdbcTemplate.queryForObject(
            "SELECT user_id FROM organization_members WHERE organization_id=?",
            Long::class.java,
            ORGANIZATION_ID)).isEqualTo(firstOwnerUserId)
    }

    private fun insertCompletedUser(): Long {
        return insertUser(true)
    }

    private fun insertUser(completed: Boolean): Long {
        val userId = IDENTIFIER_SEQUENCE.incrementAndGet()
        createdUserIds.add(userId)
        jdbcTemplate.update(
            "INSERT INTO users (id, status, version, created_at, updated_at) "
                + "VALUES (?, 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))",
            userId)
        jdbcTemplate.update(
            "INSERT INTO user_profiles "
                + "(user_id, nickname, gender_code, completed_at, version, created_at, updated_at) "
                + "VALUES (?, '测试用户', 1, "
                + (if (completed) "UTC_TIMESTAMP(3)" else "NULL")
                + ", 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))",
            userId)
        return userId
    }

    private fun count(table: String, column: String, value: Long): Int {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM $table WHERE $column=?",
            Int::class.java,
            value)!!
    }

    companion object {
        private const val ORGANIZATION_ID = 1_900_000_000_000_000_401L
        private val IDENTIFIER_SEQUENCE = AtomicLong(
            System.currentTimeMillis() * 1_000L + 700L)
    }
}
