package com.eligo.server.account.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.eligo.server.account.entity.UserDeviceEntity
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import org.apache.ibatis.annotations.Update
import java.util.Optional

@Mapper
interface UserDeviceMapper : BaseMapper<UserDeviceEntity> {
    @Select("""
        SELECT * FROM user_devices
        WHERE user_id = #{userId} AND installation_id_hash = #{hash}
        LIMIT 1 FOR UPDATE
        """)
    fun findByUserIdAndInstallationHash(
        @Param("userId") userId: Long,
        @Param("hash") hash: ByteArray?
    ): Optional<UserDeviceEntity>

    @Select("""
        SELECT * FROM user_devices
        WHERE id = #{deviceId} AND user_id = #{userId}
        LIMIT 1 FOR UPDATE
        """)
    fun lockOwnedById(
        @Param("deviceId") deviceId: Long,
        @Param("userId") userId: Long
    ): Optional<UserDeviceEntity>

    @Select("""
        SELECT d.* FROM user_devices d
        WHERE d.user_id = #{userId} AND d.status = 1
        AND EXISTS (
            SELECT 1 FROM user_login_sessions s
            WHERE s.device_id = d.id AND s.status = 1
            AND s.expires_at > UTC_TIMESTAMP(3)
        )
        ORDER BY d.last_seen_at, d.id
        FOR UPDATE
        """)
    fun findActiveForUpdate(@Param("userId") userId: Long): List<UserDeviceEntity>

    @Update("""
        UPDATE user_devices SET last_seen_at = UTC_TIMESTAMP(3), updated_at = UTC_TIMESTAMP(3)
        WHERE id = #{deviceId} AND status = 1
        AND (last_seen_at IS NULL OR last_seen_at < UTC_TIMESTAMP(3) - INTERVAL 5 MINUTE)
        """)
    fun touchLastSeenIfStale(@Param("deviceId") deviceId: Long): Int

    @Update("""
        UPDATE user_devices SET status = 3, status_changed_at = UTC_TIMESTAMP(3),
        updated_at = UTC_TIMESTAMP(3) WHERE id = #{deviceId} AND status = 1
        """)
    fun markEvicted(@Param("deviceId") deviceId: Long): Int

    @Update("""
        UPDATE user_devices SET status = 2, status_changed_at = UTC_TIMESTAMP(3),
        updated_at = UTC_TIMESTAMP(3) WHERE id = #{deviceId}
        """)
    fun markUserLoggedOut(@Param("deviceId") deviceId: Long): Int

    @Update("""
        UPDATE user_devices SET device_name = #{deviceName}, platform_code = #{platform},
        os_version = #{osVersion}, app_version = #{appVersion}, region_code = #{regionCode},
        status = 1, last_seen_at = UTC_TIMESTAMP(3), status_changed_at = UTC_TIMESTAMP(3),
        updated_at = UTC_TIMESTAMP(3) WHERE id = #{deviceId}
        """)
    fun activate(
        @Param("deviceId") deviceId: Long,
        @Param("deviceName") deviceName: String?,
        @Param("platform") platform: String?,
        @Param("osVersion") osVersion: String?,
        @Param("appVersion") appVersion: String?,
        @Param("regionCode") regionCode: String?
    ): Int
}
