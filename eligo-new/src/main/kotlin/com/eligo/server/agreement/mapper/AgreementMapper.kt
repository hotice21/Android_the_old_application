package com.eligo.server.agreement.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.eligo.server.agreement.entity.AgreementEntity
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select

@Mapper
interface AgreementMapper : BaseMapper<AgreementEntity> {
    @Select("""
            <script>
            SELECT * FROM agreements
            WHERE status = 3
              AND effective_at &lt;= UTC_TIMESTAMP(3)
            <if test="agreementType != null">
              AND agreement_type = #{agreementType}
            </if>
            ORDER BY agreement_type ASC, effective_at DESC, id DESC
            </script>
            """)
    fun findCurrent(@Param("agreementType") agreementType: Int?): List<AgreementEntity>
}
