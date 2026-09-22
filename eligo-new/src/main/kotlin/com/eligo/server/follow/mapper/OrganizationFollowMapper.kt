package com.eligo.server.follow.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.eligo.server.follow.entity.OrganizationFollowEntity
import java.util.Optional
import org.apache.ibatis.annotations.Delete
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select

@Mapper
interface OrganizationFollowMapper : BaseMapper<OrganizationFollowEntity> {

    @Select(
        """
            SELECT id,follower_user_id,organization_id,followed_at
            FROM organization_follows
            WHERE follower_user_id=#{followerUserId}
              AND organization_id=#{organizationId}
            """
    )
    fun find(
        @Param("followerUserId") followerUserId: Long,
        @Param("organizationId") organizationId: Long
    ): Optional<OrganizationFollowEntity>

    @Select(
        """
            <script>
            SELECT organization_id
              FROM organization_follows
             WHERE follower_user_id=#{followerUserId}
               AND organization_id IN
               <foreach collection='organizationIds' item='organizationId'
                        open='(' separator=',' close=')'>
                 #{organizationId}
               </foreach>
            </script>
            """
    )
    fun findFollowedOrganizationIds(
        @Param("followerUserId") followerUserId: Long,
        @Param("organizationIds") organizationIds: List<Long>
    ): List<Long>

    @Select(
        """
            SELECT id,follower_user_id,organization_id,followed_at
            FROM organization_follows
            WHERE follower_user_id=#{followerUserId}
              AND organization_id=#{organizationId}
            FOR UPDATE
            """
    )
    fun lockRelation(
        @Param("followerUserId") followerUserId: Long,
        @Param("organizationId") organizationId: Long
    ): Optional<OrganizationFollowEntity>

    @Delete(
        """
            DELETE FROM organization_follows
            WHERE follower_user_id=#{followerUserId}
              AND organization_id=#{organizationId}
            """
    )
    fun deleteRelation(
        @Param("followerUserId") followerUserId: Long,
        @Param("organizationId") organizationId: Long
    ): Int

    @Delete("DELETE FROM organization_follows WHERE follower_user_id=#{userId}")
    fun deleteAllByFollower(@Param("userId") userId: Long): Int

    @Select(
        """
            SELECT id,follower_user_id,organization_id,followed_at
            FROM organization_follows
            WHERE follower_user_id=#{userId}
              AND (#{afterId} IS NULL OR id>#{afterId})
            ORDER BY id
            LIMIT #{limit}
            """
    )
    fun findExportPage(
        @Param("userId") userId: Long,
        @Param("afterId") afterId: Long?,
        @Param("limit") limit: Int
    ): List<OrganizationFollowEntity>

    @Select(
        """
            SELECT COUNT(*)
            FROM organization_follows ofl
            JOIN users u
              ON u.id=ofl.follower_user_id
             AND u.status=1
            JOIN user_profiles p
              ON p.user_id=ofl.follower_user_id
             AND p.completed_at IS NOT NULL
            WHERE ofl.organization_id=#{organizationId}
            """
    )
    fun countPublicFollowers(@Param("organizationId") organizationId: Long): Long
}
