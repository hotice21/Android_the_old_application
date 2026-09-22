package com.eligo.server.follow

import java.util.function.Function
import java.util.function.Consumer

import com.eligo.server.common.api.CursorPage
import com.eligo.server.database.Stage2TestDatabaseCleaner
import com.eligo.server.follow.service.FollowReadService
import com.eligo.server.follow.vo.FollowTargetSummaryView
import com.eligo.server.follow.vo.FollowerSummaryView
import com.eligo.server.integration.wechat.WechatRestClientFactory
import com.eligo.server.security.UserPrincipal
import java.sql.SQLException
import java.util.concurrent.atomic.AtomicLong
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.NestedExceptionUtils
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean

@SpringBootTest(
    properties = [
        "eligo.security.jwt.signing-key-base64=IB8eHRwbGhkYFxYVFBMSERAPDg0MCwoJCAcGBQQDAgE=",
        "eligo.security.sensitive-data.encryption-key-base64=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
        "eligo.security.sensitive-data.lookup-key-base64=Hx4dHBsaGRgXFhUUExIREA8ODQwLCgkIBwYFBAMCAQA="
    ]
)
@ActiveProfiles("it")
@EnabledIfSystemProperty(named = "eligo.database.tests", matches = "true")
class FollowSchemaIntegrationTests {

    private val ids = AtomicLong(30_000L)

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var followReads: FollowReadService

    @MockitoBean
    lateinit var redis: StringRedisTemplate

    @MockitoBean
    lateinit var wechatRestClientFactory: WechatRestClientFactory

    @BeforeEach
    fun cleanDatabase() {
        Stage2TestDatabaseCleaner.cleanAllStage2Tables(jdbcTemplate)
    }

    @Test
    fun userFollowsRejectDuplicatesAndSelfRelationships() {
        val firstUserId = insertUser()
        val secondUserId = insertUser()
        insertUserFollow(firstUserId, secondUserId)

        assertUniqueViolation { insertUserFollow(firstUserId, secondUserId) }
        assertCheckViolation { insertUserFollow(firstUserId, firstUserId) }
    }

    @Test
    fun organizationFollowsRejectDuplicatesAndRestrictReferencedDeletion() {
        val userId = insertUser()
        val organizationId = insertOrganization()
        insertOrganizationFollow(userId, organizationId)

        assertUniqueViolation { insertOrganizationFollow(userId, organizationId) }
        assertForeignKeyViolation {
            jdbcTemplate.update(
                "DELETE FROM organizations WHERE id=?", organizationId
            )
        }
    }

    @Test
    fun postsRequireExactlyOneAuthorAndConsistentEventActor() {
        val operatorId = insertUser()
        val organizationId = insertOrganization()

        assertCheckViolation {
            jdbcTemplate.update(
                """
                INSERT INTO posts (
                    id, author_user_id, author_organization_id, operator_user_id,
                    status, visibility, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 1, 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """, nextId(), operatorId, organizationId, operatorId
            )
        }

        val postId = nextId()
        jdbcTemplate.update(
            """
            INSERT INTO posts (
                id, author_user_id, operator_user_id, status, visibility,
                version, created_at, updated_at
            ) VALUES (?, ?, ?, 1, 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """, postId, operatorId, operatorId
        )

        assertCheckViolation {
            jdbcTemplate.update(
                """
                INSERT INTO post_status_events (
                    id, post_id, from_status, to_status, actor_type,
                    actor_user_id, created_at
                ) VALUES (?, ?, 1, 2, 1, NULL, UTC_TIMESTAMP(3))
                """, nextId(), postId
            )
        }

        assertCheckViolation {
            jdbcTemplate.update(
                """
                INSERT INTO post_status_events (
                    id, post_id, from_status, to_status, actor_type,
                    actor_user_id, created_at
                ) VALUES (?, ?, 1, 4, 1, ?, UTC_TIMESTAMP(3))
                """, nextId(), postId, operatorId
            )
        }

        assertCheckViolation {
            jdbcTemplate.update(
                """
                INSERT INTO post_status_events (
                    id, post_id, from_status, to_status, actor_type,
                    actor_user_id, created_at
                ) VALUES (?, ?, 3, 2, 1, ?, UTC_TIMESTAMP(3))
                """, nextId(), postId, operatorId
            )
        }

        assertCheckViolation {
            jdbcTemplate.update(
                """
                INSERT INTO posts (
                    id, author_user_id, operator_user_id, status, visibility,
                    hidden_at, deleted_at, version, created_at, updated_at
                ) VALUES (?, ?, ?, 3, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3),
                    0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """, nextId(), operatorId, operatorId
            )
        }
    }

    @Test
    fun personalPostRequiresAuthorToBeOperator() {
        val authorId = insertUser()
        val operatorId = insertUser()

        assertCheckViolation {
            jdbcTemplate.update(
                """
                INSERT INTO posts (
                    id, author_user_id, operator_user_id, status, visibility,
                    version, created_at, updated_at
                ) VALUES (?, ?, ?, 1, 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """, nextId(), authorId, operatorId
            )
        }
    }

    @Test
    fun realFollowQueriesFilterUnavailableTargetsAndUseStableBoundCursors() {
        val followerId = insertUser()
        val targetUserId = insertUser()
        val unavailableUserId = insertUser()
        insertCompletedProfile(followerId, "关注者")
        insertCompletedProfile(targetUserId, "徒步者")
        insertCompletedProfile(unavailableUserId, "已停用者")
        val organizationId = insertOrganization()
        val unavailableOrganizationId = insertOrganization()

        val userFollowId = insertUserFollowAt(
            followerId, targetUserId, "2026-08-16 01:02:03.000"
        )
        insertUserFollowAt(
            followerId, unavailableUserId, "2026-08-16 01:02:02.000"
        )
        val organizationFollowId = insertOrganizationFollowAt(
            followerId, organizationId, "2026-08-16 01:02:01.000"
        )
        insertOrganizationFollowAt(
            followerId, unavailableOrganizationId,
            "2026-08-16 01:02:00.000"
        )
        jdbcTemplate.update("UPDATE users SET status=2 WHERE id=?", unavailableUserId)
        jdbcTemplate.update(
            "UPDATE organizations SET status=2 WHERE id=?",
            unavailableOrganizationId
        )

        val principal = UserPrincipal(followerId, "follow-db-it")
        val first = followReads.listFollowing(
            principal, null, 1, null, null, "RECENT"
        )
        val second = followReads.listFollowing(
            principal, first.nextCursor, 1, null, null, "RECENT"
        )

        assertThat(first.items).extracting(Function {  it.followId  })
            .containsExactly(userFollowId.toString())
        assertThat(first.items[0].targetType).isEqualTo("USER")
        assertThat(second.items).extracting(Function {  it.followId  })
            .containsExactly(organizationFollowId.toString())
        assertThat(second.items[0].targetType).isEqualTo("ORGANIZATION")
        assertThat(followReads.getPublicUserProfile(followerId).followingCount)
            .isEqualTo(2L)

        assertThat(
            followReads.listFollowing(
                principal, null, 20, "ORGANIZATION", "测试企业", "EARLIEST"
            ).items
        ).singleElement()
            .extracting(Function {  it.targetId  })
            .isEqualTo(organizationId.toString())
    }

    @Test
    fun realFollowerQueriesAndPublicCountsExcludeUnavailableFollowers() {
        val followerId = insertUser()
        val targetUserId = insertUser()
        insertCompletedProfile(followerId, "山友甲")
        insertCompletedProfile(targetUserId, "山友乙")
        insertUserFollowAt(followerId, targetUserId, "2026-08-16 01:02:03.000")

        val target = UserPrincipal(targetUserId, "follower-db-it")
        val visible = followReads.listFollowers(
            target, null, 20, "山友", "RECENT"
        )
        assertThat(visible.items).singleElement()
            .extracting(Function {  it.userId  })
            .isEqualTo(followerId.toString())
        assertThat(followReads.getPublicUserProfile(targetUserId).followerCount)
            .isEqualTo(1L)

        jdbcTemplate.update("UPDATE users SET status=2 WHERE id=?", followerId)

        assertThat(
            followReads.listFollowers(target, null, 20, null, "RECENT").items
        ).isEmpty()
        assertThat(followReads.getPublicUserProfile(targetUserId).followerCount)
            .isZero()
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_follows WHERE follower_user_id=?",
                Long::class.java,
                followerId
            )
        ).isEqualTo(1L)
    }

    private fun insertUser(): Long {
        val id = nextId()
        jdbcTemplate.update(
            """
            INSERT INTO users (id, status, version, created_at, updated_at)
            VALUES (?, 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """, id
        )
        return id
    }

    private fun insertOrganization(): Long {
        val id = nextId()
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

    private fun insertCompletedProfile(userId: Long, nickname: String) {
        jdbcTemplate.update(
            """
            INSERT INTO user_profiles (
                user_id, nickname, completed_at, version, created_at, updated_at
            ) VALUES (?, ?, UTC_TIMESTAMP(3), 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """, userId, nickname
        )
    }

    private fun insertUserFollow(followerUserId: Long, followedUserId: Long) {
        jdbcTemplate.update(
            """
            INSERT INTO user_follows (
                id, follower_user_id, followed_user_id, followed_at
            ) VALUES (?, ?, ?, UTC_TIMESTAMP(3))
            """, nextId(), followerUserId, followedUserId
        )
    }

    private fun insertOrganizationFollow(followerUserId: Long, organizationId: Long) {
        jdbcTemplate.update(
            """
            INSERT INTO organization_follows (
                id, follower_user_id, organization_id, followed_at
            ) VALUES (?, ?, ?, UTC_TIMESTAMP(3))
            """, nextId(), followerUserId, organizationId
        )
    }

    private fun insertUserFollowAt(
        followerUserId: Long,
        followedUserId: Long,
        followedAt: String
    ): Long {
        val id = nextId()
        jdbcTemplate.update(
            """
            INSERT INTO user_follows (
                id, follower_user_id, followed_user_id, followed_at
            ) VALUES (?, ?, ?, ?)
            """, id, followerUserId, followedUserId, followedAt
        )
        return id
    }

    private fun insertOrganizationFollowAt(
        followerUserId: Long,
        organizationId: Long,
        followedAt: String
    ): Long {
        val id = nextId()
        jdbcTemplate.update(
            """
            INSERT INTO organization_follows (
                id, follower_user_id, organization_id, followed_at
            ) VALUES (?, ?, ?, ?)
            """, id, followerUserId, organizationId, followedAt
        )
        return id
    }

    private fun assertUniqueViolation(operation: () -> Unit) {
        assertSqlViolation(operation, 1062, "23000")
    }

    private fun assertCheckViolation(operation: () -> Unit) {
        assertSqlViolation(operation, 3819, "HY000")
    }

    private fun assertForeignKeyViolation(operation: () -> Unit) {
        assertSqlViolation(operation, 1451, "23000")
    }

    private fun assertSqlViolation(
        operation: () -> Unit,
        expectedErrorCode: Int,
        expectedSqlState: String
    ) {
        assertThatThrownBy(operation)
            .satisfies(Consumer {  throwable ->
                val rootCause = NestedExceptionUtils.getMostSpecificCause(throwable)
                assertThat(rootCause).isInstanceOf(SQLException::class.java)
                val sqlException = rootCause as SQLException
                assertThat(sqlException.errorCode).isEqualTo(expectedErrorCode)
                assertThat(sqlException.sqlState).isEqualTo(expectedSqlState)
             })
    }

    private fun nextId(): Long = ids.incrementAndGet()
}
