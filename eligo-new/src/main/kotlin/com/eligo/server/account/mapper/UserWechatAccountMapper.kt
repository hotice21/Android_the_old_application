package com.eligo.server.account.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.eligo.server.account.entity.UserWechatAccountEntity
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import org.apache.ibatis.annotations.Update
import java.time.LocalDateTime
import java.util.Optional

@Mapper
interface UserWechatAccountMapper : BaseMapper<UserWechatAccountEntity> {
    @Select("""
        SELECT * FROM user_wechat_accounts
        WHERE app_id = #{appId} AND openid_lookup_hash = #{hash} AND status = 1
        LIMIT 1
        """)
    fun findActiveByAppIdAndOpenidHash(
        @Param("appId") appId: String?,
        @Param("hash") hash: ByteArray?
    ): Optional<UserWechatAccountEntity>

    @Select("""
        SELECT * FROM user_wechat_accounts
        WHERE app_id = #{appId} AND openid_lookup_hash = #{hash} AND status = 1
        LIMIT 1 FOR UPDATE
        """)
    fun lockActiveByAppIdAndOpenidHash(
        @Param("appId") appId: String?,
        @Param("hash") hash: ByteArray?
    ): Optional<UserWechatAccountEntity>

    @Update("""
        UPDATE user_wechat_accounts
        SET last_login_at = UTC_TIMESTAMP(3), updated_at = UTC_TIMESTAMP(3)
        WHERE id = #{id} AND status = 1
        """)
    fun touchLogin(@Param("id") id: Long): Int

    @Select("""
        SELECT * FROM user_wechat_accounts
        WHERE user_id=#{userId} AND status=1
        ORDER BY id LIMIT 1 FOR UPDATE
        """)
    fun lockActiveByUserId(@Param("userId") userId: Long): Optional<UserWechatAccountEntity>

    @Update("""
        UPDATE user_wechat_accounts
        SET status=2,openid_ciphertext=X'',
            openid_lookup_hash=UNHEX(REPEAT('00',32)),
            unionid_ciphertext=NULL,unionid_lookup_hash=NULL,
            unbound_at=COALESCE(unbound_at,#{now}),updated_at=#{now}
        WHERE user_id=#{userId}
        """)
    fun unbindAllActiveByUserId(
        @Param("userId") userId: Long,
        @Param("now") now: LocalDateTime?
    ): Int
}
