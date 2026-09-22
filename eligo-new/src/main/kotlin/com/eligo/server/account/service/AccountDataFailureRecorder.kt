package com.eligo.server.account.service

import com.eligo.server.account.entity.UserDataRequestEntity
import com.eligo.server.account.entity.UserDataRequestEventEntity
import com.eligo.server.account.mapper.UserDataRequestEventMapper
import com.eligo.server.account.mapper.UserDataRequestMapper
import java.time.LocalDateTime
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test")
class AccountDataFailureRecorder(
    private val requests: UserDataRequestMapper,
    private val events: UserDataRequestEventMapper
) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun record(requestId: Long, requestType: Int, failure: RuntimeException, now: LocalDateTime) {
        val request = requests.lockById(requestId).orElse(null) ?: return
        if (request.requestType != requestType ||
            request.status == UserDataRequestEntity.STATUS_COMPLETED ||
            request.status == UserDataRequestEntity.STATUS_CANCELLED
        ) {
            return
        }
        val failureCode = failureCode(requestType, failure)
        val safeSummary = if (requestType == UserDataRequestEntity.TYPE_EXPORT)
            "个人数据导出后台任务执行失败" else "账号注销后台任务执行失败"
        val sourceStatus = request.status!!
        if (requests.markFailed(
                requestId, sourceStatus, request.version!!, failureCode, safeSummary, now
            ) != 1
        ) {
            return
        }
        val event = UserDataRequestEventEntity()
        event.requestId = requestId
        event.eventType = if (requestType == UserDataRequestEntity.TYPE_EXPORT)
            "DATA_EXPORT_FAILED" else "DEACTIVATION_FAILED"
        event.fromStatus = sourceStatus
        event.toStatus = UserDataRequestEntity.STATUS_FAILED
        event.actorType = UserDataRequestEventEntity.ACTOR_SYSTEM
        event.resultCode = failureCode
        event.detailJson = "{\"summary\":\"$safeSummary\"}"
        event.createdAt = now
        events.insert(event)
    }

    private fun failureCode(requestType: Int, failure: RuntimeException): String {
        if (requestType == UserDataRequestEntity.TYPE_EXPORT) {
            return if (failure is PersonalDataExportService.ExportLimitExceededException)
                EXPORT_LIMIT_FAILURE else EXPORT_FAILURE
        }
        return DEACTIVATION_FAILURE
    }

    companion object {
        const val DEACTIVATION_FAILURE = "DEACTIVATION_EXECUTION_FAILED"
        const val EXPORT_FAILURE = "DATA_EXPORT_PROCESSING_FAILED"
        const val EXPORT_LIMIT_FAILURE = "DATA_EXPORT_LIMIT_EXCEEDED"
    }
}
