package com.eligo.server.participation.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.eligo.server.participation.entity.ActivityParticipationEntity
import java.time.LocalDateTime
import java.util.Optional
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import org.apache.ibatis.annotations.Update

@Mapper
interface ActivityParticipationMapper : BaseMapper<ActivityParticipationEntity> {

    @Select("""
        SELECT *
        FROM activity_participations
        WHERE activity_id=#{activityId} AND user_id=#{userId}
        FOR UPDATE
        """)
    fun lockByActivityAndUser(
        @Param("activityId") activityId: Long,
        @Param("userId") userId: Long
    ): Optional<ActivityParticipationEntity>

    @Select("""
        SELECT *
        FROM activity_participations
        WHERE activity_id=#{activityId} AND user_id=#{userId}
        """)
    fun findByActivityAndUser(
        @Param("activityId") activityId: Long,
        @Param("userId") userId: Long
    ): Optional<ActivityParticipationEntity>

    @Select("""
        SELECT EXISTS(
            SELECT 1
            FROM activity_participations ap
            JOIN activities a ON a.id=ap.activity_id
            WHERE ap.user_id=#{userId}
              AND ap.status=1
              AND a.status IN(2, 5)
              AND a.ends_at>#{now}
            LIMIT 1
        )
        """)
    fun existsActiveInOngoingActivityByUserId(
        @Param("userId") userId: Long,
        @Param("now") now: LocalDateTime
    ): Boolean

    @Update("""
        UPDATE activity_participations
        SET status=1,
            joined_at=#{now},
            cancelled_at=NULL,
            terminated_at=NULL,
            termination_reason=NULL,
            version=version+1,
            updated_at=#{now}
        WHERE id=#{participationId} AND status=2
        """)
    fun reactivate(
        @Param("participationId") participationId: Long,
        @Param("now") now: LocalDateTime
    ): Int

    @Update("""
        UPDATE activity_participations
        SET status=2,
            cancelled_at=#{now},
            terminated_at=NULL,
            termination_reason=NULL,
            version=version+1,
            updated_at=#{now}
        WHERE id=#{participationId} AND status=1
        """)
    fun cancelActive(
        @Param("participationId") participationId: Long,
        @Param("now") now: LocalDateTime
    ): Int

    @Update("""
        UPDATE activity_participations
        SET status=3,
            cancelled_at=NULL,
            terminated_at=#{now},
            termination_reason=2,
            version=version+1,
            updated_at=#{now}
        WHERE id=#{participationId} AND status=1
        """)
    fun terminateByOwner(
        @Param("participationId") participationId: Long,
        @Param("now") now: LocalDateTime
    ): Int

    @Update("""
        UPDATE activity_participations
        SET status=3,
            cancelled_at=NULL,
            terminated_at=#{now},
            termination_reason=1,
            version=version+1,
            updated_at=#{now}
        WHERE activity_id=#{activityId} AND status=1
        """)
    fun terminateActiveForActivityCancellation(
        @Param("activityId") activityId: Long,
        @Param("now") now: LocalDateTime
    ): Int

    @Select("""
        SELECT ap.user_id,
               COALESCE(NULLIF(TRIM(p.nickname), ''), '已注销用户') AS nickname,
               p.avatar_file_id,
               ap.joined_at
        FROM activity_participations ap
        LEFT JOIN user_profiles p ON p.user_id=ap.user_id
        WHERE ap.activity_id=#{activityId}
          AND ap.status=1
          AND (
              #{cursorJoinedAt} IS NULL
              OR ap.joined_at>#{cursorJoinedAt}
              OR (ap.joined_at=#{cursorJoinedAt} AND ap.user_id>#{cursorUserId})
          )
        ORDER BY ap.joined_at ASC, ap.user_id ASC
        LIMIT #{limit}
        """)
    fun findActiveParticipantPage(
        @Param("activityId") activityId: Long,
        @Param("cursorJoinedAt") cursorJoinedAt: LocalDateTime?,
        @Param("cursorUserId") cursorUserId: Long?,
        @Param("limit") limit: Int
    ): List<ActivityParticipantRow>

    @Select("""
        SELECT ap.id AS participation_id,
               ap.user_id,
               ap.status AS participation_status,
               ap.joined_at,
               ap.cancelled_at,
               ap.terminated_at,
               ap.termination_reason,
               a.id AS activity_id,
               a.status AS activity_status,
               a.title,
               a.category_code,
               a.cover_file_id,
               CASE WHEN a.owner_user_id IS NOT NULL
                    THEN 'USER' ELSE 'ORGANIZATION' END AS owner_type,
               COALESCE(a.owner_user_id, a.owner_organization_id) AS owner_id,
               CASE WHEN a.owner_user_id IS NOT NULL
                    THEN COALESCE(NULLIF(TRIM(owner_profile.nickname), ''), '已注销用户')
                    ELSE o.name END AS owner_display_name,
               CASE WHEN a.owner_user_id IS NOT NULL
                    THEN owner_profile.avatar_file_id ELSE o.avatar_file_id END
                    AS owner_avatar_file_id,
               a.registration_starts_at,
               a.registration_ends_at,
               a.starts_at,
               a.ends_at,
               a.region_code,
               a.address_detail,
               a.latitude,
               a.longitude,
               a.capacity,
               a.participant_count
        FROM activity_participations ap
        JOIN activities a ON a.id=ap.activity_id
        LEFT JOIN user_profiles owner_profile ON owner_profile.user_id=a.owner_user_id
        LEFT JOIN organizations o ON o.id=a.owner_organization_id
        WHERE ap.user_id=#{userId}
          AND (#{status} IS NULL OR ap.status=#{status})
          AND (#{keyword} IS NULL OR LOCATE(#{keyword}, a.title)>0)
          AND (
              #{cursorJoinedAt} IS NULL
              OR ap.joined_at<#{cursorJoinedAt}
              OR (ap.joined_at=#{cursorJoinedAt} AND ap.id<#{cursorParticipationId})
          )
        ORDER BY ap.joined_at DESC, ap.id DESC
        LIMIT #{limit}
        """)
    fun findMyParticipationPage(
        @Param("userId") userId: Long,
        @Param("status") status: Int?,
        @Param("keyword") keyword: String?,
        @Param("cursorJoinedAt") cursorJoinedAt: LocalDateTime?,
        @Param("cursorParticipationId") cursorParticipationId: Long?,
        @Param("limit") limit: Int
    ): List<MyParticipationRow>
}
