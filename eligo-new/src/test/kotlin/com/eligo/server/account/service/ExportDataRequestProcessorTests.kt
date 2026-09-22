package com.eligo.server.account.service

import com.eligo.server.account.entity.UserDataRequestEntity
import com.eligo.server.account.entity.UserDataRequestEventEntity
import com.eligo.server.account.mapper.UserDataRequestEventMapper
import com.eligo.server.account.mapper.UserDataRequestMapper
import java.time.LocalDateTime
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.mockito.kotlin.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class ExportDataRequestProcessorTests {
    companion object {
        private val NOW: LocalDateTime = LocalDateTime.parse("2026-07-30T08:00:00")
    }

    private val requests = mock(UserDataRequestMapper::class.java)
    private val events = mock(UserDataRequestEventMapper::class.java)
    private val exports = mock(PersonalDataExportService::class.java)
    private val processor = ExportDataRequestProcessor(requests, events, exports)

    @AfterEach
    fun clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    @Test
    fun completionUpdateFailureDeletesCreatedArchive() {
        val request = request()
        val result = PersonalDataExportService.ExportResult(801L,
                "exports/7b5e7bb8-38b9-40d2-99bd-e07fd4dc0053.zip")
        `when`(requests.lockById(601L)).thenReturn(java.util.Optional.of(request))
        `when`(requests.markProcessing(601L, UserDataRequestEntity.STATUS_REQUESTED, 0, NOW))
            .thenReturn(1)
        `when`(exports.create(202L, NOW.plusDays(1))).thenReturn(result)
        `when`(requests.markExportCompleted(601L, 1, 801L, NOW.plusDays(1), NOW))
            .thenReturn(0)

        assertThatThrownBy { processor.process(601L, NOW) }
            .isInstanceOf(IllegalStateException::class.java)

        verify(exports).discard(result)
    }

    @Test
    fun transactionRollbackDeletesCreatedArchive() {
        val request = request()
        val result = PersonalDataExportService.ExportResult(801L,
                "exports/7b5e7bb8-38b9-40d2-99bd-e07fd4dc0053.zip")
        `when`(requests.lockById(601L)).thenReturn(java.util.Optional.of(request))
        `when`(requests.markProcessing(601L, UserDataRequestEntity.STATUS_REQUESTED, 0, NOW))
            .thenReturn(1)
        `when`(exports.create(202L, NOW.plusDays(1))).thenReturn(result)
        `when`(requests.markExportCompleted(601L, 1, 801L, NOW.plusDays(1), NOW))
            .thenReturn(1)
        TransactionSynchronizationManager.initSynchronization()

        processor.process(601L, NOW)
        val synchronizations = TransactionSynchronizationManager.getSynchronizations()
        TransactionSynchronizationManager.clearSynchronization()
        synchronizations.forEach { value ->
            value.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)
        }

        verify(exports).discard(result)
        verify(events, org.mockito.Mockito.times(2))
            .insert(any<UserDataRequestEventEntity>())
    }

    @Test
    fun committedTransactionKeepsCreatedArchive() {
        val request = request()
        val result = PersonalDataExportService.ExportResult(801L,
                "exports/7b5e7bb8-38b9-40d2-99bd-e07fd4dc0053.zip")
        `when`(requests.lockById(601L)).thenReturn(java.util.Optional.of(request))
        `when`(requests.markProcessing(601L, UserDataRequestEntity.STATUS_REQUESTED, 0, NOW))
            .thenReturn(1)
        `when`(exports.create(202L, NOW.plusDays(1))).thenReturn(result)
        `when`(requests.markExportCompleted(601L, 1, 801L, NOW.plusDays(1), NOW))
            .thenReturn(1)
        TransactionSynchronizationManager.initSynchronization()

        processor.process(601L, NOW)
        val synchronizations = TransactionSynchronizationManager.getSynchronizations()
        TransactionSynchronizationManager.clearSynchronization()
        synchronizations.forEach { value ->
            value.afterCompletion(TransactionSynchronization.STATUS_COMMITTED)
        }

        verify(exports, never()).discard(result)
    }

    private fun request(): UserDataRequestEntity {
        val request = UserDataRequestEntity()
        request.id = 601L
        request.userId = 202L
        request.requestType = UserDataRequestEntity.TYPE_EXPORT
        request.status = UserDataRequestEntity.STATUS_REQUESTED
        request.executeAfter = NOW
        request.version = 0
        return request
    }
}
