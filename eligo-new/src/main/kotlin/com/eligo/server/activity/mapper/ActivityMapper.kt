package com.eligo.server.activity.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.eligo.server.activity.entity.ActivityEntity
import java.time.LocalDateTime
import java.util.Optional
import org.apache.ibatis.annotations.Delete
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import org.apache.ibatis.annotations.Update

@Mapper
interface ActivityMapper : BaseMapper<ActivityEntity> {

    @Select("""
            SELECT EXISTS(
                SELECT 1
                FROM activities a
                WHERE (
                    a.owner_user_id=#{userId}
                    OR EXISTS(
                        SELECT 1
                        FROM organization_members m
                        JOIN organizations o ON o.id=m.organization_id
                        WHERE m.organization_id=a.owner_organization_id
                          AND m.user_id=#{userId}
                          AND m.role_code=1
                          AND m.status=1
                          AND o.status=1
                    )
                  )
                  AND a.status IN(2, 5)
                  AND a.ends_at>#{now}
                LIMIT 1
            )
            """)
    fun existsOngoingManagedByUserId(
        @Param("userId") userId: Long,
        @Param("now") now: LocalDateTime
    ): Boolean

    @Select("SELECT * FROM activities WHERE id=#{activityId} FOR UPDATE")
    fun lockById(@Param("activityId") activityId: Long): Optional<ActivityEntity>

    @Update("""
            UPDATE activities
            SET participant_count=participant_count+1,
                updated_at=UTC_TIMESTAMP(3)
            WHERE id=#{activityId}
              AND participant_count<capacity
            """)
    fun incrementParticipantCount(@Param("activityId") activityId: Long): Int

    @Update("""
            UPDATE activities
            SET participant_count=participant_count-1,
                updated_at=UTC_TIMESTAMP(3)
            WHERE id=#{activityId}
              AND participant_count>0
            """)
    fun decrementParticipantCount(@Param("activityId") activityId: Long): Int

    @Select("""
            SELECT * FROM activities
            WHERE create_idempotency_scope=#{scope}
              AND create_idempotency_key=#{key}
            """)
    fun findByCreateIdempotencyScope(
        @Param("scope") scope: String,
        @Param("key") key: String
    ): Optional<ActivityEntity>

    @Update("""
            UPDATE activities
            SET create_idempotency_scope=NULL,
                create_idempotency_key=NULL,
                create_idempotency_fingerprint=NULL
            WHERE id=#{activityId}
              AND create_idempotency_scope=#{scope}
              AND create_idempotency_key=#{key}
              AND created_at<=#{expiresBefore}
            """)
    fun clearExpiredCreateIdempotency(
        @Param("activityId") activityId: Long,
        @Param("scope") scope: String,
        @Param("key") key: String,
        @Param("expiresBefore") expiresBefore: LocalDateTime
    ): Int

    @Select("""
            SELECT * FROM activities
            WHERE create_idempotency_scope=#{scope}
              AND create_idempotency_key=#{key}
            FOR UPDATE
            """)
    fun lockByCreateIdempotencyScope(
        @Param("scope") scope: String,
        @Param("key") key: String
    ): Optional<ActivityEntity>

    @Update("""
            UPDATE activities
            SET status=2,
                published_at=COALESCE(published_at, #{publishedAt}),
                version=version+1,
                updated_at=#{publishedAt}
            WHERE id=#{activityId} AND status=1
            """)
    fun publishById(
        @Param("activityId") activityId: Long,
        @Param("publishedAt") publishedAt: LocalDateTime
    ): Int

    @Update("""
            UPDATE activities
            SET status=3,
                participant_count=0,
                operator_user_id=#{operatorUserId},
                version=version+1,
                updated_at=#{cancelledAt}
            WHERE id=#{activityId} AND status=#{fromStatus}
            """)
    fun cancelById(
        @Param("activityId") activityId: Long,
        @Param("fromStatus") fromStatus: Int,
        @Param("cancelledAt") cancelledAt: LocalDateTime,
        @Param("operatorUserId") operatorUserId: Long
    ): Int

    @Delete("DELETE FROM activities WHERE id=#{activityId} AND status=1")
    fun deleteDraftById(@Param("activityId") activityId: Long): Int

    @Select("""
            SELECT id
            FROM activities
            WHERE status=2 AND ends_at<=#{now}
            ORDER BY ends_at ASC, id ASC
            LIMIT #{limit}
            """)
    fun findPublishedIdsDueToEnd(
        @Param("now") now: LocalDateTime,
        @Param("limit") limit: Int
    ): List<Long>

    @Update("""
            UPDATE activities
            SET status=4,
                version=version+1,
                updated_at=#{now}
            WHERE id=#{activityId}
              AND status=2
              AND ends_at<=#{now}
            """)
    fun endPublishedIfDue(
        @Param("activityId") activityId: Long,
        @Param("now") now: LocalDateTime
    ): Int

    @Update("""
            UPDATE activities
            SET title=#{activity.title},
                category_code=#{activity.categoryCode},
                description=#{activity.description},
                cover_file_id=#{activity.coverFileId},
                registration_starts_at=#{activity.registrationStartsAt},
                registration_ends_at=#{activity.registrationEndsAt},
                starts_at=#{activity.startsAt},
                ends_at=#{activity.endsAt},
                region_code=#{activity.regionCode},
                address_detail=#{activity.addressDetail},
                place_name=#{activity.placeName},
                latitude=#{activity.latitude},
                longitude=#{activity.longitude},
                capacity=#{activity.capacity},
                registration_gender=#{activity.registrationGender},
                organizer_phone_ciphertext=#{activity.organizerPhoneCiphertext},
                organizer_wechat_ciphertext=#{activity.organizerWechatCiphertext},
                organizer_wechat_qr_file_id=#{activity.organizerWechatQrFileId},
                operator_user_id=#{activity.operatorUserId},
                signup_details=#{activity.signupDetails},
                organizer_message=#{activity.organizerMessage},
                version=version+1,
                updated_at=#{updatedAt}
            WHERE id=#{activity.id} AND version=#{version}
            """)
    fun updateContentByIdAndVersion(
        @Param("activity") activity: ActivityEntity,
        @Param("version") version: Int,
        @Param("updatedAt") updatedAt: LocalDateTime
    ): Int
}
