package com.eligo.server.account.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.eligo.server.account.entity.UserEntity
import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import org.apache.ibatis.annotations.Update
import java.time.LocalDateTime
import java.util.Optional

@Mapper
interface UserMapper : BaseMapper<UserEntity> {
    @Select("SELECT * FROM users WHERE id = #{userId} FOR UPDATE")
    fun lockById(@Param("userId") userId: Long): Optional<UserEntity>

    @Insert("""
        INSERT INTO user_profiles (user_id, version, created_at, updated_at)
        VALUES (#{userId}, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
        """)
    fun insertEmptyProfile(@Param("userId") userId: Long): Int

    @Update("UPDATE users SET last_login_at = UTC_TIMESTAMP(3), updated_at = UTC_TIMESTAMP(3) WHERE id = #{userId}")
    fun touchLogin(@Param("userId") userId: Long): Int

    @Update("""
        UPDATE users SET status=2,version=version+1,updated_at=#{now}
        WHERE id=#{userId} AND status=1 AND version=#{version}
        """)
    fun markDeactivationPending(
        @Param("userId") userId: Long,
        @Param("version") version: Int,
        @Param("now") now: LocalDateTime?
    ): Int

    @Update("""
        UPDATE users SET status=1,deactivated_at=NULL,version=version+1,updated_at=#{now}
        WHERE id=#{userId} AND status=2 AND version=#{version}
        """)
    fun restoreActive(
        @Param("userId") userId: Long,
        @Param("version") version: Int,
        @Param("now") now: LocalDateTime?
    ): Int

    @Update("""
        UPDATE users SET status=3,deactivated_at=#{now},version=version+1,updated_at=#{now}
        WHERE id=#{userId} AND status=2 AND version=#{version}
        """)
    fun markDeactivated(
        @Param("userId") userId: Long,
        @Param("version") version: Int,
        @Param("now") now: LocalDateTime?
    ): Int
}
