package com.eligo.server.post.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.eligo.server.post.entity.PostEntity
import java.time.LocalDateTime
import java.util.Optional
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import org.apache.ibatis.annotations.Update

@Mapper
interface PostMapper : BaseMapper<PostEntity> {

    @Select("SELECT * FROM posts WHERE id=#{postId} FOR UPDATE")
    fun lockById(@Param("postId") postId: Long): Optional<PostEntity>

    @Select("""
        SELECT * FROM posts
        WHERE create_idempotency_scope=#{scope}
          AND create_idempotency_key=#{key}
        """)
    fun findByCreateIdempotency(
        @Param("scope") scope: String,
        @Param("key") key: String
    ): Optional<PostEntity>

    @Select("""
        SELECT * FROM posts
        WHERE create_idempotency_scope=#{scope}
          AND create_idempotency_key=#{key}
        FOR UPDATE
        """)
    fun lockByCreateIdempotency(
        @Param("scope") scope: String,
        @Param("key") key: String
    ): Optional<PostEntity>

    @Update("""
        UPDATE posts
        SET create_idempotency_scope=NULL,
            create_idempotency_key=NULL,
            create_request_fingerprint=NULL,
            create_idempotency_expires_at=NULL,
            version=version+1,
            updated_at=#{now}
        WHERE id=#{postId}
          AND create_idempotency_scope=#{scope}
          AND create_idempotency_key=#{key}
          AND create_idempotency_expires_at<=#{now}
        """)
    fun clearExpiredCreateIdempotency(
        @Param("postId") postId: Long,
        @Param("scope") scope: String,
        @Param("key") key: String,
        @Param("now") now: LocalDateTime
    ): Int

    @Update("""
        UPDATE posts
        SET visibility=#{post.visibility},
            title=#{post.title},
            content=#{post.content},
            activity_id=#{post.activityId},
            operator_user_id=#{post.operatorUserId},
            version=version+1,
            updated_at=#{now}
        WHERE id=#{post.id}
          AND status=1
          AND version=#{version}
        """)
    fun replaceDraft(
        @Param("post") post: PostEntity,
        @Param("version") version: Int,
        @Param("now") now: LocalDateTime
    ): Int

    @Update("""
        UPDATE posts
        SET status=2,
            published_at=#{now},
            version=version+1,
            updated_at=#{now}
        WHERE id=#{postId} AND status=1
        """)
    fun publishById(
        @Param("postId") postId: Long,
        @Param("now") now: LocalDateTime
    ): Int

    @Update("""
        UPDATE posts
        SET status=3,
            deleted_at=#{now},
            version=version+1,
            updated_at=#{now}
        WHERE id=#{postId}
          AND status=#{fromStatus}
        """)
    fun softDeleteById(
        @Param("postId") postId: Long,
        @Param("fromStatus") fromStatus: Int,
        @Param("now") now: LocalDateTime
    ): Int

    @Select("""
        SELECT * FROM posts
        WHERE author_user_id=#{userId}
          AND status IN(1, 2, 4)
        ORDER BY id
        FOR UPDATE
        """)
    fun lockPersonalPostsForDeactivation(@Param("userId") userId: Long): List<PostEntity>

    @Update("""
        UPDATE posts
        SET status=4,
            hidden_at=#{now},
            version=version+1,
            updated_at=#{now}
        WHERE id=#{postId} AND status=2
        """)
    fun hidePublishedById(
        @Param("postId") postId: Long,
        @Param("now") now: LocalDateTime
    ): Int

    @Select("""
        SELECT * FROM posts
        WHERE author_user_id=#{userId}
          AND (#{afterId} IS NULL OR id>#{afterId})
        ORDER BY id
        LIMIT #{limit}
        """)
    fun findPersonalExportPage(
        @Param("userId") userId: Long,
        @Param("afterId") afterId: Long?,
        @Param("limit") limit: Int
    ): List<PostEntity>

    @Select("""
        SELECT EXISTS(
            SELECT 1
            FROM activities a
            LEFT JOIN users u ON u.id=a.owner_user_id
            LEFT JOIN user_profiles p ON p.user_id=a.owner_user_id
            LEFT JOIN organizations o ON o.id=a.owner_organization_id
            WHERE a.id=#{activityId}
              AND a.status IN(2, 3, 4)
              AND (
                (
                    a.owner_user_id IS NOT NULL
                    AND u.status=1
                    AND p.completed_at IS NOT NULL
                )
                OR
                (
                    a.owner_organization_id IS NOT NULL
                    AND o.status=1
                )
              )
        )
        """)
    fun existsPublicActivityReference(@Param("activityId") activityId: Long): Boolean
}
