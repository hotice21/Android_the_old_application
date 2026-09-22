package com.eligo.server.account.service

import com.eligo.server.account.entity.UserDataRequestEntity
import com.eligo.server.account.entity.UserDataRequestEventEntity
import com.eligo.server.account.mapper.UserDataRequestEventMapper
import com.eligo.server.account.mapper.UserDataRequestMapper
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Service
@Profile("!test")
class ExportDataRequestProcessor(
    private val requests: UserDataRequestMapper,
    private val events: UserDataRequestEventMapper,
    private val exports: PersonalDataExportService
) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun process(requestId: Long, now: LocalDateTime) {
        val request = requests.lockById(requestId).orElse(null) ?: return
        if (request.requestType != UserDataRequestEntity.TYPE_EXPORT ||
            (request.status != UserDataRequestEntity.STATUS_REQUESTED &&
                request.status != UserDataRequestEntity.STATUS_FAILED) ||
            request.executeAfter!!.isAfter(now)
        ) {
            return
        }
        val sourceStatus = request.status!!
        val processingVersion = request.version!! + 1
        if (requests.markProcessing(requestId, sourceStatus, request.version!!, now) != 1) {
            return
        }
        appendEvent(requestId, "DATA_EXPORT_PROCESSING", sourceStatus,
            UserDataRequestEntity.STATUS_PROCESSING, "PROCESSING", now)
        val expiresAt = now.plusDays(1)
        val result = exports.create(request.userId!!, expiresAt)
        val discarded = AtomicBoolean()
        registerRollbackCleanup(result, discarded)
        try {
            if (requests.markExportCompleted(requestId, processingVersion,
                    result.fileId, expiresAt, now) != 1
            ) {
                throw IllegalStateException("数据导出任务并发状态冲突")
            }
            appendEvent(requestId, "DATA_EXPORT_COMPLETED",
                UserDataRequestEntity.STATUS_PROCESSING,
                UserDataRequestEntity.STATUS_COMPLETED, "SUCCESS", now)
        } catch (exception: RuntimeException) {
            discardOnce(result, discarded)
            throw exception
        }
    }

    private fun registerRollbackCleanup(
        result: PersonalDataExportService.ExportResult,
        discarded: AtomicBoolean
    ) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCompletion(status: Int) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    discardOnce(result, discarded)
                }
            }
        })
    }

    private fun discardOnce(result: PersonalDataExportService.ExportResult, discarded: AtomicBoolean) {
        if (discarded.compareAndSet(false, true)) {
            exports.discard(result)
        }
    }

    private fun appendEvent(
        requestId: Long,
        eventType: String,
        fromStatus: Int,
        toStatus: Int,
        resultCode: String,
        now: LocalDateTime
    ) {
        val event = UserDataRequestEventEntity()
        event.requestId = requestId
        event.eventType = eventType
        event.fromStatus = fromStatus
        event.toStatus = toStatus
        event.actorType = UserDataRequestEventEntity.ACTOR_SYSTEM
        event.resultCode = resultCode
        event.createdAt = now
        events.insert(event)
    }
}
