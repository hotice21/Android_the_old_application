package com.eligo.server.file

import java.util.function.Function

import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.database.Stage2TestDatabaseCleaner
import com.eligo.server.file.mapper.FileObjectMapper
import com.eligo.server.file.service.FileCleanupJob
import com.eligo.server.file.service.FileService
import com.eligo.server.profile.dto.UpdateAvatarRequest
import com.eligo.server.profile.service.ProfileService
import com.eligo.server.security.UserPrincipal
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import javax.imageio.ImageIO
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.support.TransactionTemplate
import com.eligo.server.integration.wechat.WechatRestClientFactory

@SpringBootTest(
    properties = [
        "eligo.security.jwt.signing-key-base64=IB8eHRwbGhkYFxYVFBMSERAPDg0MCwoJCAcGBQQDAgE=",
        "eligo.security.sensitive-data.encryption-key-base64=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
        "eligo.security.sensitive-data.lookup-key-base64=Hx4dHBsaGRgXFhUUExIREA8ODQwLCgkIBwYFBAMCAQA=",
        "eligo.file.storage.local-root=target/stage2-file-it"
    ]
)
@ActiveProfiles("it")
@EnabledIfSystemProperty(named = "eligo.database.tests", matches = "true")
class FilePersistenceMySqlIntegrationTests {
    private val userId = 950001L

    @Autowired
    lateinit var fileService: FileService

    @Autowired
    lateinit var cleanupJob: FileCleanupJob

    @Autowired
    lateinit var fileObjectMapper: FileObjectMapper

    @Autowired
    lateinit var profileService: ProfileService

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var transactions: TransactionTemplate

    @MockitoBean
    lateinit var redis: StringRedisTemplate

    @MockitoBean
    lateinit var wechatRestClientFactory: WechatRestClientFactory

    @BeforeEach
    fun prepareUserAndProfile() {
        Stage2TestDatabaseCleaner.cleanStage2Database(jdbcTemplate) {
            jdbcTemplate.update(
                """
                DELETE FROM activity_media
                WHERE activity_id IN (
                    SELECT id FROM activities
                    WHERE owner_user_id = ? OR operator_user_id = ?
                )
                """, userId, userId
            )
            jdbcTemplate.update(
                """
                DELETE FROM activity_lifecycle_events
                WHERE activity_id IN (
                    SELECT id FROM activities
                    WHERE owner_user_id = ? OR operator_user_id = ?
                )
                """, userId, userId
            )
            jdbcTemplate.update(
                "DELETE FROM activities WHERE owner_user_id = ? OR operator_user_id = ?",
                userId, userId
            )
            jdbcTemplate.update("DELETE FROM user_profiles WHERE user_id = ?", userId)
            jdbcTemplate.update(
                "DELETE FROM file_objects WHERE uploader_type = 1 AND uploader_id = ?",
                userId
            )
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId)
        }
        jdbcTemplate.update(
            """
            INSERT INTO users (id, status, version, created_at, updated_at)
            VALUES (?, 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """, userId
        )
        jdbcTemplate.update(
            """
            INSERT INTO user_profiles (user_id, version, created_at, updated_at)
            VALUES (?, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """, userId
        )
    }

    @Test
    fun uploadPersistsRequiredTimestampsThroughRealMapper() {
        val result = fileService.uploadAvatarImage(
            UserPrincipal(userId, "file-it"), image()
        )

        val row = jdbcTemplate.queryForMap(
            """
            SELECT created_at, updated_at, lifecycle_status, scan_status, purpose
            FROM file_objects WHERE id = ?
            """, result.fileId.toLong()
        )
        assertThat(row["created_at"]).isNotNull()
        assertThat(row["updated_at"]).isNotNull()
        assertThat(row["lifecycle_status"]).isEqualTo(1)
        assertThat(row["scan_status"]).isEqualTo(2)
        assertThat(row["purpose"]).isEqualTo("AVATAR")
    }

    @Test
    fun activityImageRequiresPublicActivityReferenceForAnonymousRead() {
        val principal = UserPrincipal(userId, "activity-file-it")
        val result = fileService.uploadImage(principal, image(), "ACTIVITY")
        val fileId = result.fileId.toLong()

        jdbcTemplate.update(
            """
            UPDATE file_objects
               SET lifecycle_status=2, activated_at=UTC_TIMESTAMP(3), expires_at=NULL
             WHERE id=?
            """, fileId
        )

        assertThat(result.purpose).isEqualTo("ACTIVITY")
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT purpose FROM file_objects WHERE id = ?", String::class.java, fileId
            )
        ).isEqualTo("ACTIVITY")
        assertAnonymousContentNotFound(fileId)

        jdbcTemplate.update(
            """
            INSERT INTO activities (
                id, owner_user_id, operator_user_id, status, title, category_code,
                description, cover_file_id, registration_starts_at,
                registration_ends_at, starts_at, ends_at, region_code,
                address_detail, capacity, participant_count, version,
                created_at, updated_at
            ) VALUES (950012, ?, ?, 1, '草稿活动封面', 'HIKING',
                '活动介绍', ?, '2026-08-10 01:00:00.000',
                '2026-08-10 02:00:00.000', '2026-08-10 02:00:00.000',
                '2026-08-10 03:00:00.000', '440305', '深圳市南山区活动地址',
                20, 0, 0,
                UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """, userId, userId, fileId
        )
        assertAnonymousContentNotFound(fileId)

        jdbcTemplate.update(
            """
            UPDATE activities
            SET status=2, published_at=UTC_TIMESTAMP(3)
            WHERE id=950012
            """
        )
        assertAnonymousContentIsPublic(fileId)

        jdbcTemplate.update("UPDATE activities SET status=5 WHERE id=950012")
        assertAnonymousContentNotFound(fileId)

        jdbcTemplate.update("UPDATE activities SET status=3 WHERE id=950012")
        assertAnonymousContentIsPublic(fileId)

        jdbcTemplate.update("UPDATE activities SET status=4 WHERE id=950012")
        assertAnonymousContentIsPublic(fileId)
    }

    @Test
    fun activityMediaReferenceAlsoAllowsAnonymousReadForPublicActivity() {
        val cover = fileService.uploadImage(
            UserPrincipal(userId, "activity-media-cover-it"),
            image(),
            "ACTIVITY"
        )
        val media = fileService.uploadImage(
            UserPrincipal(userId, "activity-media-it"),
            image(),
            "ACTIVITY"
        )
        val coverFileId = cover.fileId.toLong()
        val mediaFileId = media.fileId.toLong()
        jdbcTemplate.update(
            """
            UPDATE file_objects
               SET lifecycle_status=2, activated_at=UTC_TIMESTAMP(3), expires_at=NULL
             WHERE id IN (?, ?)
            """, coverFileId, mediaFileId
        )
        jdbcTemplate.update(
            """
            INSERT INTO activities (
                id, owner_user_id, operator_user_id, status, title, category_code,
                description, cover_file_id, registration_starts_at,
                registration_ends_at, starts_at, ends_at, region_code,
                address_detail, capacity, participant_count, version,
                published_at, created_at, updated_at
            ) VALUES (950013, ?, ?, 2, '公开活动轮播图', 'HIKING',
                '活动介绍', ?, '2026-08-10 01:00:00.000',
                '2026-08-10 02:00:00.000', '2026-08-10 02:00:00.000',
                '2026-08-10 03:00:00.000', '440305', '深圳市南山区活动地址',
                20, 0, 0, UTC_TIMESTAMP(3),
                UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """, userId, userId, coverFileId
        )
        jdbcTemplate.update(
            """
            INSERT INTO activity_media (id, activity_id, file_id, sort_order, created_at)
            VALUES (950013, 950013, ?, 1, UTC_TIMESTAMP(3))
            """, mediaFileId
        )

        assertAnonymousContentIsPublic(mediaFileId)
    }

    @Test
    fun enclosingRollbackRestoresBothFileAndProfileState() {
        val principal = UserPrincipal(userId, "avatar-it")
        val fileId = fileService.uploadAvatarImage(principal, image()).fileId.toLong()

        assertThatThrownBy {
            transactions.executeWithoutResult {
                profileService.updateAvatar(principal, UpdateAvatarRequest(fileId.toString()))
                throw IllegalStateException("触发事务回滚")
            }
        }.isInstanceOf(IllegalStateException::class.java)

        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT lifecycle_status FROM file_objects WHERE id = ?", Int::class.java, fileId
            )
        ).isEqualTo(1)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT avatar_file_id FROM user_profiles WHERE user_id = ?", Long::class.java, userId
            )
        ).isNull()
    }

    @Test
    fun cleanupMarksUnreferencedActivityImageButKeepsReferencedImageActive() {
        val orphan = fileService.uploadImage(
            UserPrincipal(userId, "activity-orphan-it"), image(), "ACTIVITY"
        )
        val referenced = fileService.uploadImage(
            UserPrincipal(userId, "activity-referenced-it"), image(), "ACTIVITY"
        )
        val orphanId = orphan.fileId.toLong()
        val referencedId = referenced.fileId.toLong()
        jdbcTemplate.update(
            """
            UPDATE file_objects
               SET lifecycle_status=2, activated_at=UTC_TIMESTAMP(3), expires_at=NULL
             WHERE id IN (?, ?)
            """, orphanId, referencedId
        )
        jdbcTemplate.update(
            """
            INSERT INTO activities (
                id, owner_user_id, operator_user_id, status, title,
                cover_file_id, participant_count, version, created_at, updated_at
            ) VALUES (950010, ?, ?, 1, '引用活动封面', ?, 0, 0,
                UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """, userId, userId, referencedId
        )

        cleanupJob.cleanupExpiredAndOrphanedObjects()

        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT lifecycle_status FROM file_objects WHERE id=?",
                Int::class.java,
                orphanId
            )
        ).isEqualTo(3)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT lifecycle_status FROM file_objects WHERE id=?",
                Int::class.java,
                referencedId
            )
        ).isEqualTo(2)
    }

    @Test
    fun staleOrphanCandidateCannotBeDeletedAfterActivityReferencesIt() {
        val uploaded = fileService.uploadImage(
            UserPrincipal(userId, "activity-race-it"), image(), "ACTIVITY"
        )
        val fileId = uploaded.fileId.toLong()
        jdbcTemplate.update(
            """
            UPDATE file_objects
               SET lifecycle_status=2, activated_at=UTC_TIMESTAMP(3), expires_at=NULL
             WHERE id=?
            """, fileId
        )
        jdbcTemplate.update(
            """
            INSERT INTO activities (
                id, owner_user_id, operator_user_id, status, title,
                cover_file_id, participant_count, version, created_at, updated_at
            ) VALUES (950011, ?, ?, 1, '竞态引用活动', ?, 0, 0,
                UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """, userId, userId, fileId
        )

        val changed = fileObjectMapper.markOrphanedDeleted(
            fileId, 0, LocalDateTime.parse("2026-07-22T08:00:00")
        )

        assertThat(changed).isZero()
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT lifecycle_status FROM file_objects WHERE id=?",
                Int::class.java,
                fileId
            )
        ).isEqualTo(2)
    }

    private fun assertAnonymousContentNotFound(fileId: Long) {
        assertThatThrownBy { fileService.openContent(null, fileId) }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND)
    }

    private fun assertAnonymousContentIsPublic(fileId: Long) {
        val content = fileService.openContent(null, fileId)
        assertThat(content.cacheControl).isEqualTo("no-store")
        assertThat(content.input.readAllBytes()).isNotEmpty()
    }

    private fun image(): MockMultipartFile {
        val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
        val output = ByteArrayOutputStream()
        ImageIO.write(image, "png", output)
        return MockMultipartFile("file", "avatar.png", "image/png", output.toByteArray())
    }
}
