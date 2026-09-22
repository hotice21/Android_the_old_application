package com.eligo.server.follow.mapper

import java.time.LocalDateTime
import java.util.Optional
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select

@Mapper
interface FollowReadMapper {

    @Select(
        """
            <script>
            SELECT f.follow_id,
                   f.target_type,
                   f.target_id,
                   f.display_name,
                   f.avatar_file_id,
                   f.followed_at
            FROM (
                SELECT uf.id AS follow_id,
                       'USER' AS target_type,
                       uf.followed_user_id AS target_id,
                       p.nickname AS display_name,
                       p.avatar_file_id,
                       uf.followed_at
                FROM user_follows uf
                JOIN users u ON u.id=uf.followed_user_id AND u.status=1
                JOIN user_profiles p
                  ON p.user_id=uf.followed_user_id
                 AND p.completed_at IS NOT NULL
                WHERE uf.follower_user_id=#{userId}
                  AND (#{type} IS NULL OR #{type}='USER')
                  AND (#{keyword} IS NULL OR LOCATE(#{keyword}, p.nickname)>0)
                UNION ALL
                SELECT ofl.id AS follow_id,
                       'ORGANIZATION' AS target_type,
                       ofl.organization_id AS target_id,
                       o.name AS display_name,
                       o.avatar_file_id,
                       ofl.followed_at
                FROM organization_follows ofl
                JOIN organizations o
                  ON o.id=ofl.organization_id
                 AND o.status=1
                WHERE ofl.follower_user_id=#{userId}
                  AND (#{type} IS NULL OR #{type}='ORGANIZATION')
                  AND (#{keyword} IS NULL OR LOCATE(#{keyword}, o.name)>0)
            ) f
            WHERE #{cursorFollowedAt} IS NULL
               OR (
                    #{recent}=TRUE
                    AND (
                        f.followed_at&lt;#{cursorFollowedAt}
                        OR (
                            f.followed_at=#{cursorFollowedAt}
                            AND f.follow_id&lt;#{cursorFollowId}
                        )
                    )
               )
               OR (
                    #{recent}=FALSE
                    AND (
                        f.followed_at&gt;#{cursorFollowedAt}
                        OR (
                            f.followed_at=#{cursorFollowedAt}
                            AND f.follow_id&gt;#{cursorFollowId}
                        )
                    )
               )
            <choose>
                <when test="recent">
                    ORDER BY f.followed_at DESC, f.follow_id DESC
                </when>
                <otherwise>
                    ORDER BY f.followed_at ASC, f.follow_id ASC
                </otherwise>
            </choose>
            LIMIT #{limit}
            </script>
            """
    )
    fun findFollowingPage(
        @Param("userId") userId: Long,
        @Param("type") type: String?,
        @Param("keyword") keyword: String?,
        @Param("cursorFollowedAt") cursorFollowedAt: LocalDateTime?,
        @Param("cursorFollowId") cursorFollowId: Long?,
        @Param("recent") recent: Boolean,
        @Param("limit") limit: Int
    ): List<FollowTargetRow>

    @Select(
        """
            <script>
            SELECT uf.id AS follow_id,
                   uf.follower_user_id AS user_id,
                   p.nickname,
                   p.avatar_file_id,
                   uf.followed_at
            FROM user_follows uf
            JOIN users u ON u.id=uf.follower_user_id AND u.status=1
            JOIN user_profiles p
              ON p.user_id=uf.follower_user_id
             AND p.completed_at IS NOT NULL
            WHERE uf.followed_user_id=#{userId}
              AND (#{keyword} IS NULL OR LOCATE(#{keyword}, p.nickname)>0)
              AND (
                    #{cursorFollowedAt} IS NULL
                    OR (
                        #{recent}=TRUE
                        AND (
                            uf.followed_at&lt;#{cursorFollowedAt}
                            OR (
                                uf.followed_at=#{cursorFollowedAt}
                                AND uf.id&lt;#{cursorFollowId}
                            )
                        )
                    )
                    OR (
                        #{recent}=FALSE
                        AND (
                            uf.followed_at&gt;#{cursorFollowedAt}
                            OR (
                                uf.followed_at=#{cursorFollowedAt}
                                AND uf.id&gt;#{cursorFollowId}
                            )
                        )
                    )
              )
            <choose>
                <when test="recent">
                    ORDER BY uf.followed_at DESC, uf.id DESC
                </when>
                <otherwise>
                    ORDER BY uf.followed_at ASC, uf.id ASC
                </otherwise>
            </choose>
            LIMIT #{limit}
            </script>
            """
    )
    fun findFollowerPage(
        @Param("userId") userId: Long,
        @Param("keyword") keyword: String?,
        @Param("cursorFollowedAt") cursorFollowedAt: LocalDateTime?,
        @Param("cursorFollowId") cursorFollowId: Long?,
        @Param("recent") recent: Boolean,
        @Param("limit") limit: Int
    ): List<FollowerRow>

    @Select(
        """
            SELECT p.user_id,p.nickname,p.avatar_file_id,p.bio
            FROM users u
            JOIN user_profiles p ON p.user_id=u.id
            WHERE u.id=#{userId}
              AND u.status=1
              AND p.completed_at IS NOT NULL
            """
    )
    fun findPublicUserProfile(@Param("userId") userId: Long): Optional<PublicUserProfileRow>

    @Select(
        """
            SELECT
                (
                    SELECT COUNT(*)
                    FROM user_follows uf
                    JOIN users u
                      ON u.id=uf.followed_user_id
                     AND u.status=1
                    JOIN user_profiles p
                      ON p.user_id=uf.followed_user_id
                     AND p.completed_at IS NOT NULL
                    WHERE uf.follower_user_id=#{userId}
                )
                +
                (
                    SELECT COUNT(*)
                    FROM organization_follows ofl
                    JOIN organizations o
                      ON o.id=ofl.organization_id
                     AND o.status=1
                    WHERE ofl.follower_user_id=#{userId}
                )
            """
    )
    fun countPublicFollowing(@Param("userId") userId: Long): Long

    @Select(
        """
            SELECT COUNT(*)
            FROM user_follows uf
            JOIN users u
              ON u.id=uf.follower_user_id
             AND u.status=1
            JOIN user_profiles p
              ON p.user_id=uf.follower_user_id
             AND p.completed_at IS NOT NULL
            WHERE uf.followed_user_id=#{userId}
            """
    )
    fun countPublicFollowers(@Param("userId") userId: Long): Long
}
