package com.eligo.server.favorite.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.eligo.server.favorite.entity.ActivityFavoriteEntity
import org.apache.ibatis.annotations.Arg
import org.apache.ibatis.annotations.ConstructorArgs
import org.apache.ibatis.annotations.Delete
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import java.time.LocalDateTime
import java.util.Optional

@Mapper
interface ActivityFavoriteMapper : BaseMapper<ActivityFavoriteEntity> {

    @Select("""
            SELECT id,user_id,activity_id,favorited_at
              FROM activity_favorites
             WHERE user_id=#{userId}
               AND activity_id=#{activityId}
            """)
    fun find(
        @Param("userId") userId: Long,
        @Param("activityId") activityId: Long
    ): Optional<ActivityFavoriteEntity>

    @Select("""
            SELECT id,user_id,activity_id,favorited_at
              FROM activity_favorites
             WHERE user_id=#{userId}
               AND activity_id=#{activityId}
             FOR UPDATE
            """)
    fun lockRelation(
        @Param("userId") userId: Long,
        @Param("activityId") activityId: Long
    ): Optional<ActivityFavoriteEntity>

    @Delete("""
            DELETE FROM activity_favorites
             WHERE user_id=#{userId}
               AND activity_id=#{activityId}
            """)
    fun deleteRelation(
        @Param("userId") userId: Long,
        @Param("activityId") activityId: Long
    ): Int

    @Select("""
            SELECT id AS favorite_id,activity_id,favorited_at
              FROM activity_favorites
             WHERE user_id=#{userId}
               AND (
                    #{cursorFavoritedAt} IS NULL
                    OR favorited_at<#{cursorFavoritedAt}
                    OR (
                        favorited_at=#{cursorFavoritedAt}
                        AND id<#{cursorFavoriteId}
                    )
               )
             ORDER BY favorited_at DESC,id DESC
             LIMIT #{limit}
            """)
    @ConstructorArgs(
        Arg(column = "favorite_id", javaType = Long::class),
        Arg(column = "activity_id", javaType = Long::class),
        Arg(column = "favorited_at", javaType = LocalDateTime::class)
    )
    fun findPage(
        @Param("userId") userId: Long,
        @Param("cursorFavoritedAt") cursorFavoritedAt: LocalDateTime?,
        @Param("cursorFavoriteId") cursorFavoriteId: Long?,
        @Param("limit") limit: Int
    ): List<ActivityFavoriteRow>

    @Delete("DELETE FROM activity_favorites WHERE user_id=#{userId}")
    fun deleteByUserId(@Param("userId") userId: Long): Int
}
