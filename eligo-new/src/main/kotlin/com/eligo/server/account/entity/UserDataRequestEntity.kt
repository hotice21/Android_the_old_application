package com.eligo.server.account.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

@TableName("user_data_requests")
class UserDataRequestEntity {
    @TableId(type = IdType.ASSIGN_ID)
    var id: Long? = null
    var userId: Long? = null
    var requestType: Int? = null
    var status: Int? = null
    var requestedAt: LocalDateTime? = null
    var executeAfter: LocalDateTime? = null
    var resultFileId: Long? = null
    var resultExpiresAt: LocalDateTime? = null
    var retryCount: Int? = null
    var failureCode: String? = null
    var lastErrorSummary: String? = null
    var processedAt: LocalDateTime? = null
    var version: Int? = null
    var createdAt: LocalDateTime? = null
    var updatedAt: LocalDateTime? = null
    @TableField(exist = false)
    var activeRequestType: Int? = null

    companion object {
        const val TYPE_DEACTIVATION = 1
        const val TYPE_EXPORT = 2
        const val STATUS_REQUESTED = 1
        const val STATUS_WAITING_EXECUTION = 2
        const val STATUS_PROCESSING = 3
        const val STATUS_COMPLETED = 4
        const val STATUS_CANCELLED = 5
        const val STATUS_FAILED = 6
    }
}
