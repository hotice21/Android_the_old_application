package com.eligo.server.post.mapper

import java.time.LocalDateTime
import java.util.Optional
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select

@Mapper
interface PostQueryMapper {

    @Select("""
        SELECT p.id AS post_id,
               p.author_user_id,
               p.author_organization_id,
               CASE WHEN p.author_user_id IS NOT NULL
                    THEN COALESCE(NULLIF(TRIM(up.nickname), ''), '已注销用户')
                    ELSE o.name END AS author_display_name,
               CASE WHEN p.author_user_id IS NOT NULL
                    THEN up.avatar_file_id ELSE o.avatar_file_id END
                    AS author_avatar_file_id,
               p.status,p.visibility,p.title,p.content,p.activity_id,p.version,
               p.published_at,p.hidden_at,p.created_at,p.updated_at
        FROM posts p
        LEFT JOIN user_profiles up ON up.user_id=p.author_user_id
        LEFT JOIN organizations o ON o.id=p.author_organization_id
        WHERE p.id=#{postId}
        """)
    fun findById(@Param("postId") postId: Long): Optional<PostDetailRow>

    @Select("""
        SELECT p.id AS post_id,
               p.author_user_id,
               p.author_organization_id,
               CASE WHEN p.author_user_id IS NOT NULL
                    THEN COALESCE(NULLIF(TRIM(up.nickname), ''), '已注销用户')
                    ELSE o.name END AS author_display_name,
               CASE WHEN p.author_user_id IS NOT NULL
                    THEN up.avatar_file_id ELSE o.avatar_file_id END
                    AS author_avatar_file_id,
               p.status,p.visibility,p.title,p.content,p.activity_id,p.version,
               p.published_at,p.hidden_at,p.created_at,p.updated_at
        FROM posts p
        LEFT JOIN user_profiles up ON up.user_id=p.author_user_id
        LEFT JOIN organizations o ON o.id=p.author_organization_id
        WHERE p.id=#{postId}
        FOR UPDATE
        """)
    fun findByIdForUpdate(@Param("postId") postId: Long): Optional<PostDetailRow>

    @Select("""
        SELECT p.id AS post_id,
               p.author_user_id,
               p.author_organization_id,
               CASE WHEN p.author_user_id IS NOT NULL
                    THEN COALESCE(NULLIF(TRIM(up.nickname), ''), '已注销用户')
                    ELSE o.name END AS author_display_name,
               CASE WHEN p.author_user_id IS NOT NULL
                    THEN up.avatar_file_id ELSE o.avatar_file_id END
                    AS author_avatar_file_id,
               p.status,p.visibility,p.title,p.content,p.activity_id,p.version,
               p.published_at,p.hidden_at,p.created_at,p.updated_at
        FROM post_media pm
        JOIN posts p ON p.id=pm.post_id
        LEFT JOIN user_profiles up ON up.user_id=p.author_user_id
        LEFT JOIN organizations o ON o.id=p.author_organization_id
        WHERE pm.file_id=#{fileId}
        """)
    fun findByMediaFileId(@Param("fileId") fileId: Long): Optional<PostDetailRow>

    @Select("""
        SELECT EXISTS(
            SELECT 1
            FROM posts p
            LEFT JOIN users u ON u.id=p.author_user_id
            LEFT JOIN user_profiles up ON up.user_id=p.author_user_id
            LEFT JOIN organizations o ON o.id=p.author_organization_id
            WHERE p.id=#{postId}
              AND (
                    (
                        p.author_user_id IS NOT NULL
                        AND u.status=1
                        AND up.completed_at IS NOT NULL
                    )
                    OR
                    (
                        p.author_organization_id IS NOT NULL
                        AND o.status=1
                    )
              )
        )
        """)
    fun isPostAuthorPubliclyAvailable(@Param("postId") postId: Long): Boolean

    @Select("""
        SELECT p.id AS post_id,
               p.author_user_id,
               p.author_organization_id,
               CASE WHEN p.author_user_id IS NOT NULL
                    THEN up.nickname ELSE o.name END AS author_display_name,
               CASE WHEN p.author_user_id IS NOT NULL
                    THEN up.avatar_file_id ELSE o.avatar_file_id END
                    AS author_avatar_file_id,
               p.status,p.visibility,p.title,p.content,p.activity_id,p.version,
               p.published_at,p.hidden_at,p.created_at,p.updated_at
        FROM posts p
        LEFT JOIN users u ON u.id=p.author_user_id
        LEFT JOIN user_profiles up ON up.user_id=p.author_user_id
        LEFT JOIN organizations o ON o.id=p.author_organization_id
        WHERE p.id=#{postId}
          AND p.status=2
          AND (
                (
                    p.author_user_id IS NOT NULL
                    AND u.status=1
                    AND up.completed_at IS NOT NULL
                )
                OR
                (
                    p.author_organization_id IS NOT NULL
                    AND o.status=1
                )
          )
        """)
    fun findPublicCandidateById(
        @Param("postId") postId: Long
    ): Optional<PostDetailRow>

    @Select("""
        <script>
        SELECT p.id AS post_id,
               p.author_user_id,
               p.author_organization_id,
               CASE WHEN p.author_user_id IS NOT NULL
                    THEN COALESCE(NULLIF(TRIM(up.nickname), ''), '已注销用户')
                    ELSE o.name END AS author_display_name,
               CASE WHEN p.author_user_id IS NOT NULL
                    THEN up.avatar_file_id ELSE o.avatar_file_id END
                    AS author_avatar_file_id,
               p.status,p.visibility,p.title,p.content,p.activity_id,p.version,
               p.published_at,p.hidden_at,p.created_at,p.updated_at
        FROM posts p
        LEFT JOIN user_profiles up ON up.user_id=p.author_user_id
        LEFT JOIN organizations o ON o.id=p.author_organization_id
        WHERE p.status&lt;&gt;3
          AND (
                p.author_user_id=#{userId}
                OR (
                    p.author_organization_id IS NOT NULL
                    AND EXISTS(
                        SELECT 1
                        FROM organization_members owner_match
                        WHERE owner_match.organization_id=p.author_organization_id
                          AND owner_match.user_id=#{userId}
                          AND owner_match.role_code=1
                          AND owner_match.status=1
                    )
                    AND (
                        SELECT COUNT(*)
                        FROM organization_members owner_count
                        WHERE owner_count.organization_id=p.author_organization_id
                          AND owner_count.role_code=1
                          AND owner_count.status=1
                    )=1
                )
          )
          AND (#{status} IS NULL OR p.status=#{status})
          AND (
                #{authorType} IS NULL
                OR (#{authorType}='USER' AND p.author_user_id IS NOT NULL)
                OR (
                    #{authorType}='ORGANIZATION'
                    AND p.author_organization_id IS NOT NULL
                )
          )
          AND (
                #{cursorUpdatedAt} IS NULL
                OR p.updated_at&lt;#{cursorUpdatedAt}
                OR (
                    p.updated_at=#{cursorUpdatedAt}
                    AND p.id&lt;#{cursorPostId}
                )
          )
        ORDER BY p.updated_at DESC,p.id DESC
        LIMIT #{limit}
        </script>
        """)
    fun findManagedPage(
        @Param("userId") userId: Long,
        @Param("status") status: Int?,
        @Param("authorType") authorType: String?,
        @Param("cursorUpdatedAt") cursorUpdatedAt: LocalDateTime?,
        @Param("cursorPostId") cursorPostId: Long?,
        @Param("limit") limit: Int
    ): List<PostDetailRow>

    @Select("""
        SELECT p.id AS post_id,
               p.author_user_id,
               p.author_organization_id,
               CASE WHEN p.author_user_id IS NOT NULL
                    THEN up.nickname ELSE o.name END AS author_display_name,
               CASE WHEN p.author_user_id IS NOT NULL
                    THEN up.avatar_file_id ELSE o.avatar_file_id END
                    AS author_avatar_file_id,
               p.status,p.visibility,p.title,p.content,p.activity_id,p.version,
               p.published_at,p.hidden_at,p.created_at,p.updated_at
        FROM posts p
        LEFT JOIN users u ON u.id=p.author_user_id
        LEFT JOIN user_profiles up ON up.user_id=p.author_user_id
        LEFT JOIN organizations o ON o.id=p.author_organization_id
        WHERE p.status=2
          AND p.visibility=1
          AND (
                (
                    p.author_user_id IS NOT NULL
                    AND u.status=1
                    AND up.completed_at IS NOT NULL
                )
                OR
                (
                    p.author_organization_id IS NOT NULL
                    AND o.status=1
                )
          )
          AND (
                #{cursorPublishedAt} IS NULL
                OR p.published_at<#{cursorPublishedAt}
                OR (
                    p.published_at=#{cursorPublishedAt}
                    AND p.id<#{cursorPostId}
                )
          )
        ORDER BY p.published_at DESC,p.id DESC
        LIMIT #{limit}
        """)
    fun findPublicPage(
        @Param("cursorPublishedAt") cursorPublishedAt: LocalDateTime?,
        @Param("cursorPostId") cursorPostId: Long?,
        @Param("limit") limit: Int
    ): List<PostDetailRow>

    @Select("""
        <script>
        SELECT p.id AS post_id,
               p.author_user_id,
               p.author_organization_id,
               CASE WHEN p.author_user_id IS NOT NULL
                    THEN up.nickname ELSE o.name END AS author_display_name,
               CASE WHEN p.author_user_id IS NOT NULL
                    THEN up.avatar_file_id ELSE o.avatar_file_id END
                    AS author_avatar_file_id,
               p.status,p.visibility,p.title,p.content,p.activity_id,p.version,
               p.published_at,p.hidden_at,p.created_at,p.updated_at
        FROM posts p
        LEFT JOIN users u ON u.id=p.author_user_id
        LEFT JOIN user_profiles up ON up.user_id=p.author_user_id
        LEFT JOIN organizations o ON o.id=p.author_organization_id
        WHERE p.status=2
          AND p.visibility&lt;=#{maxVisibility}
          AND (
                (#{authorType}='USER' AND p.author_user_id=#{authorId})
                OR (
                    #{authorType}='ORGANIZATION'
                    AND p.author_organization_id=#{authorId}
                )
          )
          AND (
                (
                    p.author_user_id IS NOT NULL
                    AND u.status=1
                    AND up.completed_at IS NOT NULL
                )
                OR
                (
                    p.author_organization_id IS NOT NULL
                    AND o.status=1
                )
          )
          AND (
                #{cursorPublishedAt} IS NULL
                OR p.published_at&lt;#{cursorPublishedAt}
                OR (
                    p.published_at=#{cursorPublishedAt}
                    AND p.id&lt;#{cursorPostId}
                )
          )
        ORDER BY p.published_at DESC,p.id DESC
        LIMIT #{limit}
        </script>
        """)
    fun findAuthorPage(
        @Param("authorType") authorType: String,
        @Param("authorId") authorId: Long,
        @Param("maxVisibility") maxVisibility: Int,
        @Param("cursorPublishedAt") cursorPublishedAt: LocalDateTime?,
        @Param("cursorPostId") cursorPostId: Long?,
        @Param("limit") limit: Int
    ): List<PostDetailRow>

    @Select("""
        SELECT p.id AS post_id,
               p.author_user_id,
               p.author_organization_id,
               CASE WHEN p.author_user_id IS NOT NULL
                    THEN up.nickname ELSE o.name END AS author_display_name,
               CASE WHEN p.author_user_id IS NOT NULL
                    THEN up.avatar_file_id ELSE o.avatar_file_id END
                    AS author_avatar_file_id,
               p.status,p.visibility,p.title,p.content,p.activity_id,p.version,
               p.published_at,p.hidden_at,p.created_at,p.updated_at
        FROM posts p
        LEFT JOIN users u ON u.id=p.author_user_id
        LEFT JOIN user_profiles up ON up.user_id=p.author_user_id
        LEFT JOIN organizations o ON o.id=p.author_organization_id
        WHERE p.status=2
          AND p.visibility IN(1,2)
          AND (
                (
                    p.author_user_id IS NOT NULL
                    AND EXISTS(
                        SELECT 1 FROM user_follows uf
                        WHERE uf.follower_user_id=#{userId}
                          AND uf.followed_user_id=p.author_user_id
                    )
                    AND u.status=1
                    AND up.completed_at IS NOT NULL
                )
                OR
                (
                    p.author_organization_id IS NOT NULL
                    AND EXISTS(
                        SELECT 1 FROM organization_follows ofl
                        WHERE ofl.follower_user_id=#{userId}
                          AND ofl.organization_id=p.author_organization_id
                    )
                    AND o.status=1
                )
          )
          AND (
                #{cursorPublishedAt} IS NULL
                OR p.published_at<#{cursorPublishedAt}
                OR (
                    p.published_at=#{cursorPublishedAt}
                    AND p.id<#{cursorPostId}
                )
          )
        ORDER BY p.published_at DESC,p.id DESC
        LIMIT #{limit}
        """)
    fun findFollowingFeedPage(
        @Param("userId") userId: Long,
        @Param("cursorPublishedAt") cursorPublishedAt: LocalDateTime?,
        @Param("cursorPostId") cursorPostId: Long?,
        @Param("limit") limit: Int
    ): List<PostDetailRow>

    @Select("""
        SELECT file_id
        FROM post_media
        WHERE post_id=#{postId}
        ORDER BY sort_order,id
        """)
    fun findMediaFileIds(@Param("postId") postId: Long): List<Long>

    @Select("""
        SELECT file_id
        FROM post_media
        WHERE post_id=#{postId}
        ORDER BY sort_order,id
        FOR UPDATE
        """)
    fun findMediaFileIdsForUpdate(@Param("postId") postId: Long): List<Long>

    @Select("""
        SELECT a.id AS activity_id,
               CASE WHEN a.status=2 AND a.ends_at<=UTC_TIMESTAMP(3)
                    THEN 4 ELSE a.status END AS status,
               a.title,a.cover_file_id,a.starts_at,a.ends_at
        FROM activities a
        LEFT JOIN users u ON u.id=a.owner_user_id
        LEFT JOIN user_profiles up ON up.user_id=a.owner_user_id
        LEFT JOIN organizations o ON o.id=a.owner_organization_id
        WHERE a.id=#{activityId}
          AND a.status IN(2,3,4)
          AND (
                (
                    a.owner_user_id IS NOT NULL
                    AND u.status=1
                    AND up.completed_at IS NOT NULL
                )
                OR
                (
                    a.owner_organization_id IS NOT NULL
                    AND o.status=1
                )
          )
        """)
    fun findPublicActivityCard(
        @Param("activityId") activityId: Long
    ): Optional<PostActivityRow>
}
