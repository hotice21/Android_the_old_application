package com.eligo.server.account.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.eligo.server.account.entity.UserLoginSessionEntity
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import org.apache.ibatis.annotations.Update
import java.time.LocalDateTime
import java.util.Optional

@Mapper
interface UserLoginSessionMapper : BaseMapper<UserLoginSessionEntity> {
    @Select("SELECT * FROM user_login_sessions WHERE refresh_token_hash = #{hash} LIMIT 1 FOR UPDATE")
    fun lockByRefreshTokenHash(@Param("hash") hash: ByteArray?): Optional<UserLoginSessionEntity>

    @Select("SELECT * FROM user_login_sessions WHERE refresh_token_hash = #{hash} LIMIT 1")
    fun findByRefreshTokenHash(@Param("hash") hash: ByteArray?): Optional<UserLoginSessionEntity>

    @Select("""
        SELECT * FROM user_login_sessions
        WHERE id = #{sessionId} AND refresh_token_hash = #{hash}
        LIMIT 1 FOR UPDATE
        """)
    fun lockByIdAndRefreshTokenHash(
        @Param("sessionId") sessionId: Long,
        @Param("hash") hash: ByteArray?
    ): Optional<UserLoginSessionEntity>

    @Select("""
        SELECT * FROM user_login_sessions
        WHERE session_key = #{sessionKey} AND status = 1 AND expires_at > UTC_TIMESTAMP(3)
        LIMIT 1
        """)
    fun findActiveBySessionKey(@Param("sessionKey") sessionKey: String?): Optional<UserLoginSessionEntity>

    @Select("SELECT * FROM user_login_sessions WHERE session_key = #{sessionKey} LIMIT 1")
    fun findBySessionKey(@Param("sessionKey") sessionKey: String?): Optional<UserLoginSessionEntity>

    @Select("SELECT * FROM user_login_sessions WHERE session_key = #{sessionKey} LIMIT 1 FOR UPDATE")
    fun lockBySessionKey(@Param("sessionKey") sessionKey: String?): Optional<UserLoginSessionEntity>

    @Select("""
        SELECT * FROM user_login_sessions
        WHERE user_id = #{userId} AND status = 1 AND expires_at > UTC_TIMESTAMP(3)
        ORDER BY created_at DESC, id DESC
        """)
    fun findActiveByUserId(@Param("userId") userId: Long): List<UserLoginSessionEntity>

    @Select("""
        SELECT * FROM user_login_sessions
        WHERE id = #{sessionId} AND user_id = #{userId} LIMIT 1 FOR UPDATE
        """)
    fun lockOwnedById(
        @Param("sessionId") sessionId: Long,
        @Param("userId") userId: Long
    ): Optional<UserLoginSessionEntity>

    @Select("""
        SELECT * FROM user_login_sessions
        WHERE id = #{sessionId} AND user_id = #{userId} LIMIT 1
        """)
    fun findOwnedById(
        @Param("sessionId") sessionId: Long,
        @Param("userId") userId: Long
    ): Optional<UserLoginSessionEntity>

    @Update("""
        UPDATE user_login_sessions SET status = 2, revoked_at = UTC_TIMESTAMP(3),
        revoke_reason = #{reason}, updated_at = UTC_TIMESTAMP(3), version = version + 1
        WHERE device_id = #{deviceId} AND status = 1
        """)
    fun revokeActiveByDevice(
        @Param("deviceId") deviceId: Long,
        @Param("reason") reason: String?
    ): Int

    @Update("""
        UPDATE user_login_sessions SET status = 2, revoked_at = UTC_TIMESTAMP(3),
        revoke_reason = #{reason}, updated_at = UTC_TIMESTAMP(3), version = version + 1
        WHERE id = #{sessionId} AND status = 1
        """)
    fun revokeById(
        @Param("sessionId") sessionId: Long,
        @Param("reason") reason: String?
    ): Int

    @Update("""
        UPDATE user_login_sessions SET refresh_token_hash = #{hash},
        refresh_token_version = refresh_token_version + 1, expires_at = #{expiresAt},
        last_used_at = UTC_TIMESTAMP(3), updated_at = UTC_TIMESTAMP(3), version = version + 1
        WHERE id = #{sessionId} AND status = 1 AND refresh_token_version = #{expectedVersion}
        """)
    fun rotateRefreshToken(
        @Param("sessionId") sessionId: Long,
        @Param("expectedVersion") expectedVersion: Int,
        @Param("hash") hash: ByteArray?,
        @Param("expiresAt") expiresAt: LocalDateTime?
    ): Int

    @Update("""
        UPDATE user_login_sessions
        SET status=2,revoked_at=#{now},revoke_reason=#{reason},
            version=version+1,updated_at=#{now}
        WHERE user_id=#{userId} AND status=1
        """)
    fun revokeAllActiveByUserId(
        @Param("userId") userId: Long,
        @Param("reason") reason: String?,
        @Param("now") now: LocalDateTime?
    ): Int
}
