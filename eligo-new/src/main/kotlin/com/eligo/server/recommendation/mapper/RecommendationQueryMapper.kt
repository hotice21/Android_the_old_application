package com.eligo.server.recommendation.mapper

import java.time.LocalDateTime
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select

@Mapper
interface RecommendationQueryMapper {

    @Select("""
        <script>
        SELECT post_id,generation
          FROM post_recommendation_index_jobs
         WHERE task_status=3
           AND post_id IN
           <foreach collection='postIds' item='postId' open='(' separator=',' close=')'>
             #{postId}
           </foreach>
        </script>
        """)
    fun findSucceededIndexGenerations(
        @Param("postIds") postIds: List<Long>
    ): List<RecommendationIndexedGeneration>

    @Select("""
        SELECT p.id AS post_id,
               p.author_user_id,
               p.author_organization_id,
               p.operator_user_id,
               p.status,
               p.visibility,
               CASE WHEN p.author_user_id IS NOT NULL
                    THEN (u.status=1 AND up.completed_at IS NOT NULL)
                    ELSE o.status=1 END AS author_publicly_available,
               p.title,
               p.content,
               p.activity_id,
               a.title AS activity_title,
               a.category_code AS activity_category_code,
               p.published_at
          FROM posts p
          LEFT JOIN activities a ON a.id=p.activity_id
          LEFT JOIN users u ON u.id=p.author_user_id
          LEFT JOIN user_profiles up ON up.user_id=p.author_user_id
          LEFT JOIN organizations o ON o.id=p.author_organization_id
         WHERE p.id=#{postId}
        """)
    fun findSourceById(@Param("postId") postId: Long): RecommendationPostSource

    @Select("""
        <script>
        SELECT p.id AS post_id,
               p.author_user_id,
               p.author_organization_id,
               p.operator_user_id,
               p.published_at,
               a.region_code AS activity_region_code
          FROM posts p
          LEFT JOIN activities a ON a.id=p.activity_id
          LEFT JOIN users u ON u.id=p.author_user_id
          LEFT JOIN user_profiles up ON up.user_id=p.author_user_id
          LEFT JOIN organizations o ON o.id=p.author_organization_id
         WHERE p.id IN
           <foreach collection='postIds' item='postId' open='(' separator=',' close=')'>
             #{postId}
           </foreach>
           AND p.status=2
           AND p.visibility=1
           AND (
                (p.author_user_id IS NOT NULL
                 AND u.status=1
                 AND up.completed_at IS NOT NULL)
                OR
                (p.author_organization_id IS NOT NULL
                 AND o.status=1)
           )
           AND (
                #{viewerUserId} IS NULL
                OR (
                    (p.author_user_id IS NULL OR p.author_user_id&lt;&gt;#{viewerUserId})
                    AND (p.operator_user_id IS NULL
                         OR p.operator_user_id&lt;&gt;#{viewerUserId})
                )
           )
        </script>
        """)
    fun findCurrentPublicByIds(
        @Param("postIds") postIds: List<Long>,
        @Param("viewerUserId") viewerUserId: Long?
    ): List<RecommendationCandidateRow>

    @Select("""
        <script>
        SELECT p.id AS post_id,
               p.author_user_id,
               p.author_organization_id,
               p.operator_user_id,
               p.published_at,
               a.region_code AS activity_region_code
          FROM posts p
          LEFT JOIN activities a ON a.id=p.activity_id
          LEFT JOIN users u ON u.id=p.author_user_id
          LEFT JOIN user_profiles up ON up.user_id=p.author_user_id
          LEFT JOIN organizations o ON o.id=p.author_organization_id
         WHERE p.status=2
           AND p.visibility=1
           AND (
                (p.author_user_id IS NOT NULL
                 AND u.status=1
                 AND up.completed_at IS NOT NULL)
                OR
                (p.author_organization_id IS NOT NULL
                 AND o.status=1)
           )
           AND (
                #{viewerUserId} IS NULL
                OR (
                    (p.author_user_id IS NULL OR p.author_user_id&lt;&gt;#{viewerUserId})
                    AND (p.operator_user_id IS NULL
                         OR p.operator_user_id&lt;&gt;#{viewerUserId})
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
           <if test='excludedPostIds != null and excludedPostIds.size() > 0'>
           AND p.id NOT IN
             <foreach collection='excludedPostIds' item='postId'
                      open='(' separator=',' close=')'>
               #{postId}
             </foreach>
           </if>
         ORDER BY p.published_at DESC,p.id DESC
         LIMIT #{limit}
        </script>
        """)
    fun findLatestPublicPage(
        @Param("viewerUserId") viewerUserId: Long?,
        @Param("cursorPublishedAt") cursorPublishedAt: LocalDateTime?,
        @Param("cursorPostId") cursorPostId: Long?,
        @Param("excludedPostIds") excludedPostIds: List<Long>,
        @Param("limit") limit: Int
    ): List<RecommendationCandidateRow>
}
