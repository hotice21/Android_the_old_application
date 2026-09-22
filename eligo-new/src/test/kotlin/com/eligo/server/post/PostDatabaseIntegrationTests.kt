package com.eligo.server.post

import java.util.function.Function

import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.database.Stage2TestDatabaseCleaner
import com.eligo.server.file.entity.FileObjectEntity
import com.eligo.server.file.mapper.FileObjectMapper
import com.eligo.server.integration.wechat.WechatRestClientFactory
import com.eligo.server.post.dto.PostDraftCreateRequest
import com.eligo.server.post.dto.PostDraftReplaceRequest
import com.eligo.server.post.entity.PostEntity
import com.eligo.server.post.error.PostErrorCode
import com.eligo.server.post.service.PostAccountLifecycleService
import com.eligo.server.post.service.PostCommandService
import com.eligo.server.post.service.PostCreateOutcome
import com.eligo.server.post.service.PostReadService
import com.eligo.server.security.UserPrincipal
import java.time.LocalDateTime
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong
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
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@SpringBootTest(properties = [
    "eligo.security.jwt.signing-key-base64=IB8eHRwbGhkYFxYVFBMSERAPDg0MCwoJCAcGBQQDAgE=",
    "eligo.security.sensitive-data.encryption-key-base64=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
    "eligo.security.sensitive-data.lookup-key-base64=Hx4dHBsaGRgXFhUUExIREA8ODQwLCgkIBwYFBAMCAQA="
])
@ActiveProfiles("it")
@EnabledIfSystemProperty(named = "eligo.database.tests", matches = "true")
class PostDatabaseIntegrationTests {

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var commands: PostCommandService

    @Autowired
    lateinit var reads: PostReadService

    @Autowired
    lateinit var lifecycle: PostAccountLifecycleService

    @Autowired
    lateinit var files: FileObjectMapper

    @Autowired
    lateinit var transactions: PlatformTransactionManager

    @MockitoBean
    lateinit var redis: StringRedisTemplate

    @MockitoBean
    lateinit var wechatRestClientFactory: WechatRestClientFactory

    @BeforeEach
    fun cleanDatabase() {
        Stage2TestDatabaseCleaner.cleanAllStage2Tables(jdbcTemplate)
    }

    @Test
    fun draftPublishFollowerReadFeedAndMediaPermissionWorkOnMySql() {
        val authorId = insertCompletedUser("动态作者")
        val followerId = insertCompletedUser("关注者")
        val fileId = insertTemporaryPostFile(authorId)
        val author = UserPrincipal(authorId, "post-author")
        val follower = UserPrincipal(followerId, "post-follower")

        val created = commands.createPersonal(
            author,
            PostDraftCreateRequest(
                "徒步记录", "今天走了十公里", "FOLLOWERS_ONLY",
                null, listOf(fileId)),
            "mysql-post-create")
        val postId = created.view.postId!!.toLong()

        assertThat(created.replayed).isFalse()
        assertThat(jdbcTemplate.queryForObject(
            "SELECT lifecycle_status FROM file_objects WHERE id=?",
            Int::class.java, fileId)).isEqualTo(2)
        assertThat(reads.canReadMedia(author, fileId)).isTrue()
        assertThat(reads.canReadMedia(null, fileId)).isFalse()

        commands.publish(author, postId)
        assertNotFound { reads.getPost(null, postId) }
        assertThat(reads.listFollowingFeed(follower, null, 20).items).isEmpty()

        jdbcTemplate.update(
            """
            INSERT INTO user_follows (
                id,follower_user_id,followed_user_id,followed_at
            ) VALUES (?,?,?,UTC_TIMESTAMP(3))
            """, nextId(), followerId, authorId)

        assertThat(reads.getPost(follower, postId).postId)
            .isEqualTo(postId.toString())
        assertThat(reads.listFollowingFeed(follower, null, 20).items)
            .extracting(Function {  it.postId  })
            .containsExactly(postId.toString())
        assertThat(reads.listUserPosts(follower, authorId, null, 20).items)
            .hasSize(1)
        assertThat(reads.canReadMedia(follower, fileId)).isTrue()

        commands.delete(author, postId)
        assertThat(reads.canReadMedia(author, fileId)).isFalse()
    }

    @Test
    fun replayReplaceAndPostOrphanCleanupUseRealConstraints() {
        val authorId = insertCompletedUser("草稿作者")
        val firstFileId = insertTemporaryPostFile(authorId)
        val secondFileId = insertTemporaryPostFile(authorId)
        val author = UserPrincipal(authorId, "post-replace")
        val request = PostDraftCreateRequest(
            "草稿标题", "草稿正文", "PUBLIC", null, listOf(firstFileId))

        val first = commands.createPersonal(author, request, "replace-key")
        val replay = commands.createPersonal(author, request, "replace-key")
        val postId = first.view.postId!!.toLong()

        assertThat(replay.replayed).isTrue()
        assertThat(replay.view.postId).isEqualTo(first.view.postId)

        val replaced = commands.replacePersonal(
            author,
            postId,
            PostDraftReplaceRequest(
                first.view.version, "新标题", "新正文", "PRIVATE",
                null, listOf(secondFileId)))

        assertThat(replaced.version).isEqualTo(first.view.version + 1)
        assertThat(replaced.media).extracting(Function {  it.fileId  })
            .containsExactly(secondFileId.toString())
        assertThat(files.findOrphanedActivePostFiles(100))
            .extracting(Function { it.id })
            .contains(firstFileId)
            .doesNotContain(secondFileId)
        val orphan = files.findById(firstFileId).orElseThrow()
        assertThat(files.markOrphanedPostDeleted(
            firstFileId, orphan.version!!, LocalDateTime.now())).isEqualTo(1)
    }

    @Test
    fun concurrentSameKeyCreatesOneDraftAndReturnsOneResult() {
        val authorId = insertCompletedUser("并发作者")
        val author = UserPrincipal(authorId, "post-concurrency")
        val request = PostDraftCreateRequest(
            "并发草稿", "同一个请求", "PUBLIC", null, emptyList())
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val operation = Callable {
                ready.countDown()
                start.await(5, TimeUnit.SECONDS)
                commands.createPersonal(
                    author, request, "mysql-concurrent-key")
            }
            val first: Future<PostCreateOutcome> = executor.submit(operation)
            val second: Future<PostCreateOutcome> = executor.submit(operation)
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue()
            start.countDown()

            val firstResult = first.get(20, TimeUnit.SECONDS)
            val secondResult = second.get(20, TimeUnit.SECONDS)
            assertThat(firstResult.view.postId)
                .isEqualTo(secondResult.view.postId)
            assertThat(listOf(firstResult.replayed, secondResult.replayed))
                .containsExactlyInAnyOrder(false, true)
            assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM posts
                WHERE create_idempotency_key='mysql-concurrent-key'
                """, Long::class.java)).isEqualTo(1L)
        } finally {
            executor.shutdownNow()
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()
        }
    }

    @Test
    fun replayWaitsForConcurrentDeleteAndReturnsDeletedResultError() {
        val authorId = insertCompletedUser("重放删除作者")
        val author = UserPrincipal(authorId, "post-replay-delete")
        val request = PostDraftCreateRequest(
            "并发删除草稿", "等待删除提交", "PUBLIC", null, emptyList())
        val created = commands.createPersonal(
            author, request, "mysql-replay-delete-key")
        val postId = created.view.postId!!.toLong()
        val deletePending = CountDownLatch(1)
        val allowDeleteCommit = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val delete = executor.submit {
                TransactionTemplate(transactions).executeWithoutResult {
                    jdbcTemplate.update(
                        """
                        UPDATE posts
                        SET status=3,deleted_at=UTC_TIMESTAMP(3),
                            version=version+1,updated_at=UTC_TIMESTAMP(3)
                        WHERE id=? AND status=1
                        """, postId)
                    deletePending.countDown()
                    try {
                        if (!allowDeleteCommit.await(5, TimeUnit.SECONDS)) {
                            throw IllegalStateException("等待允许删除提交超时")
                        }
                    } catch (exception: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw IllegalStateException("等待删除提交被中断", exception)
                    }
                }
            }
            assertThat(deletePending.await(5, TimeUnit.SECONDS)).isTrue()

            val replay = executor.submit {
                commands.createPersonal(
                    author, request, "mysql-replay-delete-key")
            }
            assertThatThrownBy { replay.get(300, TimeUnit.MILLISECONDS) }
                .isInstanceOf(TimeoutException::class.java)

            allowDeleteCommit.countDown()
            delete.get(10, TimeUnit.SECONDS)
            assertThatThrownBy { replay.get(10, TimeUnit.SECONDS) }
                .isInstanceOfSatisfying(ExecutionException::class.java) { exception ->
                    assertThat(exception.cause)
                        .isInstanceOfSatisfying(BusinessException::class.java) { business ->
                            assertThat(business.errorCode)
                                .isSameAs(PostErrorCode.IDEMPOTENCY_RESULT_DELETED)
                        }
                }
        } finally {
            allowDeleteCommit.countDown()
            executor.shutdownNow()
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()
        }
    }

    @Test
    fun deactivationRemovesGraphAndClosesPersonalButNotOrganizationPosts() {
        val userId = insertCompletedUser("注销用户")
        val otherId = insertCompletedUser("其他用户")
        val organizationId = insertOrganization()
        jdbcTemplate.update(
            """
            INSERT INTO user_follows (
                id,follower_user_id,followed_user_id,followed_at
            ) VALUES (?,?,?,UTC_TIMESTAMP(3)),(?,?,?,UTC_TIMESTAMP(3))
            """, nextId(), userId, otherId, nextId(), otherId, userId)
        jdbcTemplate.update(
            """
            INSERT INTO organization_follows (
                id,follower_user_id,organization_id,followed_at
            ) VALUES (?,?,?,UTC_TIMESTAMP(3))
            """, nextId(), userId, organizationId)
        val draftId = insertPersonalPost(userId, PostEntity.STATUS_DRAFT)
        val publishedId = insertPersonalPost(userId, PostEntity.STATUS_PUBLISHED)
        val hiddenId = insertPersonalPost(userId, PostEntity.STATUS_HIDDEN)
        val organizationPostId = insertOrganizationPost(
            userId, organizationId, PostEntity.STATUS_PUBLISHED)
        val now = LocalDateTime.parse("2026-08-16T09:00:00")

        TransactionTemplate(transactions).executeWithoutResult {
            lifecycle.applyDeactivation(userId, now)
        }

        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_follows WHERE follower_user_id=? OR followed_user_id=?",
            Long::class.java, userId, userId)).isZero()
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM organization_follows WHERE follower_user_id=?",
            Long::class.java, userId)).isZero()
        assertThat(status(draftId)).isEqualTo(PostEntity.STATUS_DELETED)
        assertThat(status(publishedId)).isEqualTo(PostEntity.STATUS_HIDDEN)
        assertThat(status(hiddenId)).isEqualTo(PostEntity.STATUS_HIDDEN)
        assertThat(status(organizationPostId)).isEqualTo(PostEntity.STATUS_PUBLISHED)
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM post_status_events WHERE reason_code='ACCOUNT_DEACTIVATED'",
            Long::class.java)).isEqualTo(2L)
    }

    private fun insertCompletedUser(nickname: String): Long {
        val id = nextId()
        jdbcTemplate.update(
            """
            INSERT INTO users (id,status,version,created_at,updated_at)
            VALUES (?,1,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))
            """, id)
        jdbcTemplate.update(
            """
            INSERT INTO user_profiles (
                user_id,nickname,completed_at,version,created_at,updated_at
            ) VALUES (?,?,UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))
            """, id, nickname)
        return id
    }

    private fun insertOrganization(): Long {
        val id = nextId()
        jdbcTemplate.update(
            """
            INSERT INTO organizations (
                id,name,province_code,province_name,city_code,city_name,
                district_code,district_name,address_detail,
                contact_phone_ciphertext,contact_phone_lookup_hash,
                contact_phone_last_four,status,version,created_at,updated_at
            ) VALUES (?,?,'44','广东省','4403','深圳市','440305','南山区',
                '测试地址',X'01',UNHEX(SHA2(?,256)),'0001',1,0,
                UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))
            """, id, "动态测试企业$id", "post-org-$id")
        return id
    }

    private fun insertTemporaryPostFile(uploaderId: Long): Long {
        val id = nextId()
        jdbcTemplate.update(
            """
            INSERT INTO file_objects (
                id,uploader_type,uploader_id,purpose,storage_provider,bucket_name,
                object_key,original_filename,content_type,file_extension,size_bytes,
                sha256,access_level,scan_status,lifecycle_status,expires_at,version,
                created_at,updated_at
            ) VALUES (?,1,?,'POST','LOCAL','local',?,?,'image/png','png',68,
                UNHEX(SHA2(?,256)),1,2,1,'2099-01-01 00:00:00',0,
                UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))
            """, id, uploaderId, "posts/test-$id.png",
            "test-$id.png", "post-file-$id")
        return id
    }

    private fun insertPersonalPost(userId: Long, status: Int): Long {
        val id = nextId()
        jdbcTemplate.update(
            """
            INSERT INTO posts (
                id,author_user_id,operator_user_id,status,visibility,title,content,
                published_at,hidden_at,version,created_at,updated_at
            ) VALUES (?,?,?, ?,1,'状态测试','正文',
                IF(? IN(2,4),UTC_TIMESTAMP(3),NULL),
                IF(?=4,UTC_TIMESTAMP(3),NULL),0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))
            """, id, userId, userId, status, status, status)
        return id
    }

    private fun insertOrganizationPost(operatorId: Long, organizationId: Long, status: Int): Long {
        val id = nextId()
        jdbcTemplate.update(
            """
            INSERT INTO posts (
                id,author_organization_id,operator_user_id,status,visibility,
                title,content,published_at,version,created_at,updated_at
            ) VALUES (?,?,?, ?,1,'企业状态测试','正文',UTC_TIMESTAMP(3),0,
                UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))
            """, id, organizationId, operatorId, status)
        return id
    }

    private fun status(postId: Long): Int {
        return jdbcTemplate.queryForObject(
            "SELECT status FROM posts WHERE id=?", Int::class.java, postId)!!
    }

    private fun assertNotFound(operation: () -> Unit) {
        assertThatThrownBy(operation)
            .isInstanceOfSatisfying(BusinessException::class.java) { exception ->
                assertThat(exception.errorCode)
                    .isSameAs(CommonErrorCode.RESOURCE_NOT_FOUND)
            }
    }

    companion object {
        private val IDS = AtomicLong(50_000)

        private fun nextId(): Long {
            return IDS.incrementAndGet()
        }
    }
}
