package com.eligo.server.agreement.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.eligo.server.agreement.entity.AgreementConsentEntity
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import java.util.Optional

@Mapper
interface AgreementConsentMapper : BaseMapper<AgreementConsentEntity> {
    @Select("""
            SELECT * FROM agreement_consents
            WHERE user_id = #{userId} AND agreement_id = #{agreementId}
            """)
    fun findByUserIdAndAgreementId(
        @Param("userId") userId: Long,
        @Param("agreementId") agreementId: Long
    ): Optional<AgreementConsentEntity>

    @Select("""
            SELECT * FROM agreement_consents
            WHERE user_id = #{userId} AND agreement_id = #{agreementId}
            FOR UPDATE
            """)
    fun lockByUserIdAndAgreementId(
        @Param("userId") userId: Long,
        @Param("agreementId") agreementId: Long
    ): Optional<AgreementConsentEntity>

    @Select("""
            SELECT * FROM agreement_consents
            WHERE user_id=#{userId}
              AND (#{afterId} IS NULL OR id>#{afterId})
            ORDER BY id ASC
            LIMIT #{limit}
            """)
    fun findExportPage(
        @Param("userId") userId: Long,
        @Param("afterId") afterId: Long?,
        @Param("limit") limit: Int
    ): List<AgreementConsentEntity>

    @Select("""
            SELECT a.id
            FROM agreements a
            LEFT JOIN agreement_consents c
              ON c.agreement_id = a.id AND c.user_id = #{userId}
            WHERE a.status = 3
              AND a.effective_at <= UTC_TIMESTAMP(3)
              AND a.requires_reconsent = 1
              AND c.id IS NULL
            ORDER BY a.agreement_type ASC, a.effective_at DESC, a.id DESC
            """)
    fun findPendingRequiredAgreementIds(@Param("userId") userId: Long): List<Long>
}
