package com.eligo.server.activity.mapper

import com.eligo.server.activity.entity.ActivityCreateIdempotencyTombstoneEntity
import java.time.LocalDateTime
import java.util.Optional
import org.apache.ibatis.annotations.Delete
import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select

@Mapper
interface ActivityCreateIdempotencyTombstoneMapper {

    @Insert("""
            INSERT INTO activity_create_idempotency_tombstones (
                create_idempotency_scope,
                create_idempotency_key,
                create_idempotency_fingerprint,
                activity_id,
                deleted_at,
                expires_at
            ) VALUES (
                #{item.createIdempotencyScope},
                #{item.createIdempotencyKey},
                #{item.createIdempotencyFingerprint},
                #{item.activityId},
                #{item.deletedAt},
                #{item.expiresAt}
            )
            """)
    fun insert(@Param("item") item: ActivityCreateIdempotencyTombstoneEntity): Int

    @Select("""
            SELECT *
            FROM activity_create_idempotency_tombstones
            WHERE create_idempotency_scope=#{scope}
              AND create_idempotency_key=#{key}
            """)
    fun findByScopeAndKey(
        @Param("scope") scope: String,
        @Param("key") key: String
    ): Optional<ActivityCreateIdempotencyTombstoneEntity>

    @Delete("""
            DELETE FROM activity_create_idempotency_tombstones
            WHERE create_idempotency_scope=#{scope}
              AND create_idempotency_key=#{key}
              AND expires_at<=#{now}
            """)
    fun deleteExpiredByScopeAndKey(
        @Param("scope") scope: String,
        @Param("key") key: String,
        @Param("now") now: LocalDateTime
    ): Int

    @Delete("""
            DELETE FROM activity_create_idempotency_tombstones
            WHERE expires_at<=#{now}
            LIMIT #{limit}
            """)
    fun deleteExpired(
        @Param("now") now: LocalDateTime,
        @Param("limit") limit: Int
    ): Int
}
