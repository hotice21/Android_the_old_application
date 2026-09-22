package com.eligo.server.agreement.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

@TableName("agreement_consents")
class AgreementConsentEntity {
    @TableId(type = IdType.ASSIGN_ID)
    var id: Long? = null
    var userId: Long? = null
    var agreementId: Long? = null
    var agreedAt: LocalDateTime? = null
    var requestId: String? = null
    var ipAddressCiphertext: ByteArray? = null
    var clientVersion: String? = null
    var userAgentSummary: String? = null
    var createdAt: LocalDateTime? = null
}
