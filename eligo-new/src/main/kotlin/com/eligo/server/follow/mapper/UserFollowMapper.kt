package com.eligo.server.follow.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.eligo.server.follow.entity.UserFollowEntity
import java.util.Optional
import org.apache.ibatis.annotations.Delete
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select

@Mapper
interface UserFollowMapper : BaseMapper<UserFollowEntity> {

    @Select(
        """
            SELECT id,follower_user_id,followed_user_id,followed_at
            FROM user_follows
            WHERE follower_user_id=#{followerUserId}
              AND followed_user_id=#{followedUserId}
            """
    )
    fun find(
        @Param("followerUserId") followerUserId: Long,
        @Param("followedUserId") followedUserId: Long
    ): Optional<UserFollowEntity>

    @Select(
        """
            <script>
            SELECT followed_user_id
              FROM user_follows
             WHERE follower_user_id=#{followerUserId}
               AND followed_user_id IN
               <foreach collection='followedUserIds' item='followedUserId'
                        open='(' separator=',' close=')'>
                 #{followedUserId}
               </foreach>
            </script>
            """
    )
    fun findFollowedUserIds(
        @Param("followerUserId") followerUserId: Long,
        @Param("followedUserIds") followedUserIds: List<Long>
    ): List<Long>

    @Select(
        """
            SELECT id,follower_user_id,followed_user_id,followed_at
            FROM user_follows
            WHERE follower_user_id=#{followerUserId}
              AND followed_user_id=#{followedUserId}
            FOR UPDATE
            """
    )
    fun lockRelation(
        @Param("followerUserId") followerUserId: Long,
        @Param("followedUserId") followedUserId: Long
    ): Optional<UserFollowEntity>

    @Delete(
        """
            DELETE FROM user_follows
            WHERE follower_user_id=#{followerUserId}
              AND followed_user_id=#{followedUserId}
            """
    )
    fun deleteRelation(
        @Param("followerUserId") followerUserId: Long,
        @Param("followedUserId") followedUserId: Long
    ): Int

    @Delete(
        """
            DELETE FROM user_follows
            WHERE follower_user_id=#{userId}
               OR followed_user_id=#{userId}
            """
    )
    fun deleteAllForUser(@Param("userId") userId: Long): Int

    @Select(
        """
            SELECT id,follower_user_id,followed_user_id,followed_at
            FROM user_follows
            WHERE follower_user_id=#{userId}
              AND (#{afterId} IS NULL OR id>#{afterId})
            ORDER BY id
            LIMIT #{limit}
            """
    )
    fun findFollowingExportPage(
        @Param("userId") userId: Long,
        @Param("afterId") afterId: Long?,
        @Param("limit") limit: Int
    ): List<UserFollowEntity>

    @Select(
        """
            SELECT id,follower_user_id,followed_user_id,followed_at
            FROM user_follows
            WHERE followed_user_id=#{userId}
              AND (#{afterId} IS NULL OR id>#{afterId})
            ORDER BY id
            LIMIT #{limit}
            """
    )
    fun findFollowerExportPage(
        @Param("userId") userId: Long,
        @Param("afterId") afterId: Long?,
        @Param("limit") limit: Int
    ): List<UserFollowEntity>
}
