package com.eligo.server.account.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.eligo.server.account.entity.AccountSecurityEventEntity
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import java.time.LocalDateTime

@Mapper
interface AccountSecurityEventMapper : BaseMapper<AccountSecurityEventEntity> {
    @Select("""
        SELECT * FROM account_security_events
        WHERE user_id=#{userId}
          AND (#{cursorTime} IS NULL OR occurred_at>#{cursorTime}
               OR (occurred_at=#{cursorTime} AND id>#{cursorId}))
        ORDER BY occurred_at ASC,id ASC
        LIMIT #{limit}
        """)
    fun findExportPage(
        @Param("userId") userId: Long,
        @Param("cursorTime") cursorTime: LocalDateTime?,
        @Param("cursorId") cursorId: Long?,
        @Param("limit") limit: Int
    ): List<AccountSecurityEventEntity>

    @Select("""
        SELECT e.* FROM account_security_events e
        WHERE e.user_id=#{userId}
          AND (#{cursorTime} IS NULL OR e.occurred_at<#{cursorTime}
               OR (e.occurred_at=#{cursorTime} AND e.id<#{cursorId}))
        ORDER BY e.occurred_at DESC,e.id DESC LIMIT #{limit}
        """)
    fun findPage(
        @Param("userId") userId: Long,
        @Param("cursorTime") cursorTime: LocalDateTime?,
        @Param("cursorId") cursorId: Long?,
        @Param("limit") limit: Int
    ): List<AccountSecurityEventEntity>
}
