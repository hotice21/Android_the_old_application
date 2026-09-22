package com.eligo.server.comment.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.eligo.server.comment.entity.ActivityCommentEntity
import org.apache.ibatis.annotations.Arg
import org.apache.ibatis.annotations.ConstructorArgs
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import org.apache.ibatis.annotations.Update
import java.time.LocalDateTime
import java.util.Optional

@Mapper
interface ActivityCommentMapper : BaseMapper<ActivityCommentEntity> {

    @Select("""
            SELECT id,activity_id,author_user_id,parent_comment_id,status,content,
                   idempotency_key,request_fingerprint,created_at,deleted_at
              FROM activity_comments
             WHERE author_user_id=#{authorUserId}
               AND idempotency_key=#{idempotencyKey}
            """)
    fun findByIdempotency(
        @Param("authorUserId") authorUserId: Long,
        @Param("idempotencyKey") idempotencyKey: String?
    ): Optional<ActivityCommentEntity>

    @Select("""
            SELECT id,activity_id,author_user_id,parent_comment_id,status,content,
                   idempotency_key,request_fingerprint,created_at,deleted_at
              FROM activity_comments
             WHERE author_user_id=#{authorUserId}
               AND idempotency_key=#{idempotencyKey}
             FOR UPDATE
            """)
    fun lockByIdempotency(
        @Param("authorUserId") authorUserId: Long,
        @Param("idempotencyKey") idempotencyKey: String?
    ): Optional<ActivityCommentEntity>

    @Select("""
            SELECT id,activity_id,author_user_id,parent_comment_id,status,content,
                   idempotency_key,request_fingerprint,created_at,deleted_at
              FROM activity_comments
             WHERE id=#{commentId}
            """)
    fun findById(@Param("commentId") commentId: Long): Optional<ActivityCommentEntity>

    @Select("""
            SELECT c.id AS comment_id,c.activity_id,c.author_user_id,
                   c.parent_comment_id,c.status,c.content,
                   COALESCE(NULLIF(TRIM(p.nickname), ''), '已注销用户') AS author_nickname,
                   p.avatar_file_id AS author_avatar_file_id,
                   c.created_at,c.deleted_at
              FROM activity_comments c
              LEFT JOIN user_profiles p ON p.user_id=c.author_user_id
             WHERE c.id=#{commentId}
            """)
    @ConstructorArgs(
        Arg(column = "comment_id", javaType = Long::class),
        Arg(column = "activity_id", javaType = Long::class),
        Arg(column = "author_user_id", javaType = Long::class),
        Arg(column = "parent_comment_id", javaType = Long::class),
        Arg(column = "status", javaType = Int::class),
        Arg(column = "content", javaType = String::class),
        Arg(column = "author_nickname", javaType = String::class),
        Arg(column = "author_avatar_file_id", javaType = Long::class),
        Arg(column = "created_at", javaType = LocalDateTime::class),
        Arg(column = "deleted_at", javaType = LocalDateTime::class)
    )
    fun findRowById(@Param("commentId") commentId: Long): ActivityCommentRow?

    @Select("""
            SELECT c.id AS comment_id,c.activity_id,c.author_user_id,
                   c.parent_comment_id,c.status,c.content,
                   COALESCE(NULLIF(TRIM(p.nickname), ''), '已注销用户') AS author_nickname,
                   p.avatar_file_id AS author_avatar_file_id,
                   c.created_at,c.deleted_at
              FROM activity_comments c
              LEFT JOIN user_profiles p ON p.user_id=c.author_user_id
             WHERE c.id=#{commentId}
             FOR UPDATE
            """)
    @ConstructorArgs(
        Arg(column = "comment_id", javaType = Long::class),
        Arg(column = "activity_id", javaType = Long::class),
        Arg(column = "author_user_id", javaType = Long::class),
        Arg(column = "parent_comment_id", javaType = Long::class),
        Arg(column = "status", javaType = Int::class),
        Arg(column = "content", javaType = String::class),
        Arg(column = "author_nickname", javaType = String::class),
        Arg(column = "author_avatar_file_id", javaType = Long::class),
        Arg(column = "created_at", javaType = LocalDateTime::class),
        Arg(column = "deleted_at", javaType = LocalDateTime::class)
    )
    fun findRowByIdForUpdate(@Param("commentId") commentId: Long): ActivityCommentRow?

    @Select("""
            SELECT c.id AS comment_id,c.activity_id,c.author_user_id,
                   c.parent_comment_id,c.status,c.content,
                   COALESCE(NULLIF(TRIM(p.nickname), ''), '已注销用户') AS author_nickname,
                   p.avatar_file_id AS author_avatar_file_id,
                   c.created_at,c.deleted_at
              FROM activity_comments c
              LEFT JOIN user_profiles p ON p.user_id=c.author_user_id
             WHERE c.activity_id=#{activityId}
               AND (
                    #{cursorCreatedAt} IS NULL
                    OR c.created_at>#{cursorCreatedAt}
                    OR (
                        c.created_at=#{cursorCreatedAt}
                        AND c.id>#{cursorCommentId}
                    )
               )
             ORDER BY c.created_at ASC,c.id ASC
             LIMIT #{limit}
            """)
    @ConstructorArgs(
        Arg(column = "comment_id", javaType = Long::class),
        Arg(column = "activity_id", javaType = Long::class),
        Arg(column = "author_user_id", javaType = Long::class),
        Arg(column = "parent_comment_id", javaType = Long::class),
        Arg(column = "status", javaType = Int::class),
        Arg(column = "content", javaType = String::class),
        Arg(column = "author_nickname", javaType = String::class),
        Arg(column = "author_avatar_file_id", javaType = Long::class),
        Arg(column = "created_at", javaType = LocalDateTime::class),
        Arg(column = "deleted_at", javaType = LocalDateTime::class)
    )
    fun findPage(
        @Param("activityId") activityId: Long,
        @Param("cursorCreatedAt") cursorCreatedAt: LocalDateTime?,
        @Param("cursorCommentId") cursorCommentId: Long?,
        @Param("limit") limit: Int
    ): List<ActivityCommentRow>

    @Update("""
            UPDATE activity_comments
               SET status=2,content=NULL,deleted_at=#{deletedAt}
             WHERE id=#{commentId}
               AND status=1
            """)
    fun softDelete(
        @Param("commentId") commentId: Long,
        @Param("deletedAt") deletedAt: LocalDateTime?
    ): Int

    @Update("""
            UPDATE activity_comments
               SET status=2,content=NULL,deleted_at=#{deletedAt}
             WHERE author_user_id=#{authorUserId}
               AND status=1
            """)
    fun softDeleteByAuthor(
        @Param("authorUserId") authorUserId: Long,
        @Param("deletedAt") deletedAt: LocalDateTime?
    ): Int
}
