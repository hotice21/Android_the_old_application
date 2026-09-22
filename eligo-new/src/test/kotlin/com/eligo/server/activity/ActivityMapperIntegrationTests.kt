package com.eligo.server.activity

import java.util.function.Consumer

import com.eligo.server.activity.entity.ActivityCreateIdempotencyTombstoneEntity
import com.eligo.server.activity.entity.ActivityEntity
import com.eligo.server.activity.entity.ActivityLifecycleEventEntity
import com.eligo.server.activity.entity.ActivityMediaEntity
import com.eligo.server.activity.mapper.ActivityCreateIdempotencyTombstoneMapper
import com.eligo.server.activity.mapper.ActivityLifecycleEventMapper
import com.eligo.server.activity.mapper.ActivityMapper
import com.eligo.server.activity.mapper.ActivityMediaMapper
import com.eligo.server.integration.wechat.WechatRestClientFactory
import org.assertj.core.api.Assertions.assertThat
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
class ActivityMapperIntegrationTests {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var activityMapper: ActivityMapper

    @Autowired
    private lateinit var idempotencyTombstoneMapper: ActivityCreateIdempotencyTombstoneMapper

    @Autowired
    private lateinit var activityMediaMapper: ActivityMediaMapper

    @Autowired
    private lateinit var activityLifecycleEventMapper: ActivityLifecycleEventMapper

    @MockitoBean
    private lateinit var redis: StringRedisTemplate

    @MockitoBean
    private lateinit var wechatRestClientFactory: WechatRestClientFactory

    @Test
    fun allThreeMappersInsertAndReadApprovedFields() {
        val userId = insertUser()
        val fileId = insertFile(userId)
        val now = LocalDateTime.now(ZoneOffset.UTC)
            .truncatedTo(ChronoUnit.MILLIS)

        val activity = ActivityEntity()
        activity.ownerUserId = userId
        activity.operatorUserId = userId
        activity.status = 1
        activity.title = "Mapper 草稿"
        activity.latitude = BigDecimal("22.5430960")
        activity.longitude = BigDecimal("114.0578650")
        activity.participantCount = 0
        activity.version = 0
        activity.createdAt = now
        activity.updatedAt = now

        assertThat(activityMapper.insert(activity)).isEqualTo(1)
        assertThat(activity.id).isNotNull()
        assertThat(activityMapper.selectById(activity.id))
            .satisfies(Consumer {  saved ->
                assertThat(saved.ownerUserId).isEqualTo(userId)
                assertThat(saved.status).isEqualTo(1)
                assertThat(saved.title).isEqualTo("Mapper 草稿")
                assertThat(saved.latitude).isEqualByComparingTo("22.5430960")
                assertThat(saved.longitude).isEqualByComparingTo("114.0578650")
                assertThat(saved.participantCount).isZero()
             })

        val media = ActivityMediaEntity()
        media.activityId = activity.id
        media.fileId = fileId
        media.sortOrder = 1
        media.createdAt = now

        assertThat(activityMediaMapper.insert(media)).isEqualTo(1)
        assertThat(media.id).isNotNull()
        assertThat(activityMediaMapper.selectById(media.id))
            .satisfies(Consumer {  saved ->
                assertThat(saved.activityId).isEqualTo(activity.id)
                assertThat(saved.fileId).isEqualTo(fileId)
                assertThat(saved.sortOrder).isEqualTo(1)
             })

        val event = ActivityLifecycleEventEntity()
        event.activityId = activity.id
        event.toStatus = 1
        event.operatorUserId = userId
        event.createdAt = now

        assertThat(activityLifecycleEventMapper.insert(event)).isEqualTo(1)
        assertThat(event.id).isNotNull()
        assertThat(activityLifecycleEventMapper.selectById(event.id))
            .satisfies(Consumer {  saved ->
                assertThat(saved.activityId).isEqualTo(activity.id)
                assertThat(saved.fromStatus).isNull()
                assertThat(saved.toStatus).isEqualTo(1)
                assertThat(saved.operatorUserId).isEqualTo(userId)
             })
    }

    @Test
    fun deletedDraftIdempotencyMapperPersistsAndCleansExpiredTombstones() {
        val sequence = IDENTIFIER_SEQUENCE.incrementAndGet()
        val now = LocalDateTime.now(ZoneOffset.UTC)
            .truncatedTo(ChronoUnit.MILLIS)
        val scope = "MAPPER:$sequence"
        val key = "mapper-tombstone-$sequence"
        val tombstone = ActivityCreateIdempotencyTombstoneEntity()
        tombstone.createIdempotencyScope = scope
        tombstone.createIdempotencyKey = key
        tombstone.createIdempotencyFingerprint =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        tombstone.activityId = sequence
        tombstone.deletedAt = now.minusHours(25)
        tombstone.expiresAt = now.minusHours(1)

        assertThat(idempotencyTombstoneMapper.insert(tombstone)).isEqualTo(1)
        assertThat(idempotencyTombstoneMapper.findByScopeAndKey(scope, key)).isPresent

        assertThat(idempotencyTombstoneMapper.deleteExpired(now, 100)).isPositive
        assertThat(idempotencyTombstoneMapper.findByScopeAndKey(scope, key)).isEmpty
    }

    private fun insertUser(): Long {
        val id = IDENTIFIER_SEQUENCE.incrementAndGet()
        jdbcTemplate.update(
            """
                INSERT INTO users (id, status, version, created_at, updated_at)
                VALUES (?, 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """, id
        )
        return id
    }

    private fun insertFile(userId: Long): Long {
        val id = IDENTIFIER_SEQUENCE.incrementAndGet()
        jdbcTemplate.update(
            """
                INSERT INTO file_objects (
                    id, uploader_type, uploader_id, purpose, storage_provider, bucket_name,
                    object_key, original_filename, content_type, file_extension,
                    size_bytes, sha256, access_level, scan_status, lifecycle_status,
                    version, created_at, updated_at
                ) VALUES (?, 1, ?, 'ACTIVITY', 'local', 'eligo', ?, 'mapper.jpg',
                    'image/jpeg', 'jpg', 1, UNHEX(SHA2(?, 256)),
                    1, 2, 2, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """,
            id, userId, "activity/mapper/$id", "mapper-file-$id"
        )
        return id
    }

    companion object {
        private val IDENTIFIER_SEQUENCE = AtomicLong(
            System.currentTimeMillis() * 10_000L + 4_000L
        )
    }
}
