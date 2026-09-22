package com.eligo.server.account.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.eligo.server.account.entity.UserPhoneBindingEntity
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import org.apache.ibatis.annotations.Update
import java.time.LocalDateTime
import java.util.Optional

@Mapper
interface UserPhoneBindingMapper : BaseMapper<UserPhoneBindingEntity> {
    @Select("""
        SELECT * FROM user_phone_bindings
        WHERE user_id = #{userId} AND status = 1
        LIMIT 1
        """)
    fun findActiveByUserId(@Param("userId") userId: Long): Optional<UserPhoneBindingEntity>

    @Select("""
        SELECT * FROM user_phone_bindings
        WHERE user_id = #{userId} AND status = 1
        LIMIT 1 FOR UPDATE
        """)
    fun lockActiveByUserId(@Param("userId") userId: Long): Optional<UserPhoneBindingEntity>

    @Select("""
        SELECT * FROM user_phone_bindings
        WHERE country_code = #{countryCode}
          AND phone_lookup_hash = #{lookupHash}
          AND status = 1
        LIMIT 1
        """)
    fun findActiveByCountryCodeAndLookupHash(
        @Param("countryCode") countryCode: String?,
        @Param("lookupHash") lookupHash: ByteArray?
    ): Optional<UserPhoneBindingEntity>

    @Update("""
        UPDATE user_phone_bindings
        SET status = 2,
            unbound_at = #{unboundAt},
            unbind_reason = #{reason},
            updated_at = #{unboundAt}
        WHERE id = #{id} AND status = 1
        """)
    fun markUnbound(
        @Param("id") id: Long,
        @Param("reason") reason: String?,
        @Param("unboundAt") unboundAt: LocalDateTime?
    ): Int

    @Update("""
        UPDATE user_phone_bindings
        SET status=2,country_code='',phone_ciphertext=X'',
            phone_lookup_hash=UNHEX(REPEAT('00',32)),phone_last_four='',
            unbound_at=COALESCE(unbound_at,#{now}),
            unbind_reason=COALESCE(unbind_reason,#{reason}),updated_at=#{now}
        WHERE user_id=#{userId}
        """)
    fun anonymizeAndUnbindAllActiveByUserId(
        @Param("userId") userId: Long,
        @Param("now") now: LocalDateTime?,
        @Param("reason") reason: String?
    ): Int
}
