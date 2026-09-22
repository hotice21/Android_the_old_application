package com.eligo.server.agreement.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

@TableName("agreements")
class AgreementEntity {
    @TableId(type = IdType.ASSIGN_ID)
    var id: Long? = null
    var agreementType: Int? = null
    var versionCode: String? = null
    var title: String? = null
    var content: String? = null
    var contentHash: ByteArray? = null
    var status: Int? = null
    var requiresReconsent: Int? = null
    var scheduledAt: LocalDateTime? = null
    var effectiveAt: LocalDateTime? = null
    var retiredAt: LocalDateTime? = null
    var createdByAdminId: Long? = null
    var version: Int? = null
    var createdAt: LocalDateTime? = null
    var updatedAt: LocalDateTime? = null
}
