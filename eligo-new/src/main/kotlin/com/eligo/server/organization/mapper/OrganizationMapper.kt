package com.eligo.server.organization.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.eligo.server.organization.entity.OrganizationEntity
import java.util.Optional
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select

@Mapper
interface OrganizationMapper : BaseMapper<OrganizationEntity> {

    @Select(
        """
            SELECT o.id,o.name,o.avatar_file_id,o.status
            FROM organizations o
            JOIN organization_members m ON m.organization_id=o.id
            WHERE m.user_id=#{userId}
              AND m.role_code=1
              AND m.status=1
              AND o.status=1
            ORDER BY o.id
            LIMIT 1
            """
    )
    fun findActiveOwnedByUserId(@Param("userId") userId: Long): List<OrganizationEntity>

    @Select(
        """
            SELECT o.id,o.name,o.avatar_file_id,o.summary,
                   o.province_code,o.province_name,
                   o.city_code,o.city_name,o.district_code,o.district_name,
                   o.address_detail
            FROM organizations o
            WHERE o.id=#{organizationId}
              AND o.status=1
            """
    )
    fun findActivePublicById(@Param("organizationId") organizationId: Long): Optional<OrganizationEntity>

    @Select(
        """
            SELECT (
                o.status=1
                AND (
                    SELECT COUNT(*)
                    FROM organization_members owner_count
                    WHERE owner_count.organization_id=o.id
                      AND owner_count.role_code=1
                      AND owner_count.status=1
                )=1
                AND EXISTS(
                    SELECT 1
                    FROM organization_members owner_match
                    WHERE owner_match.organization_id=o.id
                      AND owner_match.user_id=#{userId}
                      AND owner_match.role_code=1
                      AND owner_match.status=1
                )
            )
            FROM organizations o
            WHERE o.id=#{organizationId}
            """
    )
    fun isActiveSoleOwner(
        @Param("userId") userId: Long,
        @Param("organizationId") organizationId: Long
    ): Boolean

    @Select(
        """
            SELECT (
                (
                    SELECT COUNT(*)
                    FROM organization_members owner_count
                    WHERE owner_count.organization_id=o.id
                      AND owner_count.role_code=1
                      AND owner_count.status=1
                )=1
                AND EXISTS(
                    SELECT 1
                    FROM organization_members owner_match
                    WHERE owner_match.organization_id=o.id
                      AND owner_match.user_id=#{userId}
                      AND owner_match.role_code=1
                      AND owner_match.status=1
                )
            )
            FROM organizations o
            WHERE o.id=#{organizationId}
            """
    )
    fun isSoleOwner(
        @Param("userId") userId: Long,
        @Param("organizationId") organizationId: Long
    ): Boolean
}
