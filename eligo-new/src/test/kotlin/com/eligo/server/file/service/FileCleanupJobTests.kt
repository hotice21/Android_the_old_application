package com.eligo.server.file.service

import com.eligo.server.file.FileStorageProperties
import com.eligo.server.file.entity.FileObjectEntity
import com.eligo.server.file.mapper.FileObjectMapper
import com.eligo.server.file.storage.FileStorage
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FileCleanupJobTests {
    private val now = Instant.parse("2026-07-22T08:00:00Z")

    @Test
    fun failedOrphanCleanupIsRetriedOnNextRun() {
        val files = mock<FileObjectMapper>()
        val storage = mock<FileStorage>()
        val properties = FileStorageProperties()
        val orphan = "avatars/7b5e7bb8-38b9-40d2-99bd-e07fd4dc0053.jpg"
        whenever(storage.listObjectKeysOlderThan(now.minus(properties.temporaryTtl)))
            .thenReturn(listOf(orphan))
        whenever(files.countLiveByObjectKey(orphan)).thenReturn(0)
        doThrow(IOException("临时文件系统故障")).doNothing().whenever(storage).delete(orphan)
        val job = FileCleanupJob(
            files, storage, properties, Clock.fixed(now, ZoneOffset.UTC)
        )

        job.cleanupOrphanedObjects()
        job.cleanupOrphanedObjects()

        verify(storage, times(2)).delete(orphan)
    }

    @Test
    fun liveDatabaseObjectIsNeverDeletedBySweeper() {
        val files = mock<FileObjectMapper>()
        val storage = mock<FileStorage>()
        val properties = FileStorageProperties()
        val live = "avatars/7b5e7bb8-38b9-40d2-99bd-e07fd4dc0054.png"
        whenever(storage.listObjectKeysOlderThan(any())).thenReturn(listOf(live))
        whenever(files.countLiveByObjectKey(live)).thenReturn(1)
        val job = FileCleanupJob(
            files, storage, properties, Clock.fixed(now, ZoneOffset.UTC)
        )

        job.cleanupOrphanedObjects()

        verify(storage, never()).delete(any())
    }

    @Test
    fun expiredTemporaryExportIsMarkedDeletedBeforePhysicalRetry() {
        val files = mock<FileObjectMapper>()
        val storage = mock<FileStorage>()
        val properties = FileStorageProperties()
        val expired = FileObjectEntity()
        expired.id = 81L
        expired.version = 0
        expired.objectKey = "exports/7b5e7bb8-38b9-40d2-99bd-e07fd4dc0053.zip"
        val nowDateTime = LocalDateTime.parse("2026-07-22T08:00:00")
        whenever(files.findExpiredTemporaryFiles(nowDateTime, 100)).thenReturn(listOf(expired))
        whenever(files.markExpiredDeleted(81L, 0, nowDateTime)).thenReturn(1)
        whenever(storage.listObjectKeysOlderThan(now.minus(properties.temporaryTtl)))
            .thenReturn(listOf(expired.objectKey!!))
        whenever(files.countLiveByObjectKey(expired.objectKey!!)).thenReturn(0)
        doThrow(IOException("临时文件系统故障")).doNothing()
            .whenever(storage).delete(expired.objectKey!!)
        val job = FileCleanupJob(
            files, storage, properties, Clock.fixed(now, ZoneOffset.UTC)
        )

        job.cleanupExpiredAndOrphanedObjects()
        job.cleanupExpiredAndOrphanedObjects()

        verify(files, times(2)).findExpiredTemporaryFiles(nowDateTime, 100)
        verify(files, times(2)).markExpiredDeleted(81L, 0, nowDateTime)
        verify(storage, times(2)).delete(expired.objectKey!!)
    }

    @Test
    fun activeOrphanedActivityFileIsMarkedDeletedBeforePhysicalCleanup() {
        val files = mock<FileObjectMapper>()
        val storage = mock<FileStorage>()
        val properties = FileStorageProperties()
        val orphan = FileObjectEntity()
        orphan.id = 91L
        orphan.version = 3
        orphan.objectKey = "activity/orphan-91.jpg"
        val nowDateTime = LocalDateTime.parse("2026-07-22T08:00:00")
        whenever(files.findExpiredTemporaryFiles(nowDateTime, 100)).thenReturn(emptyList())
        whenever(files.findOrphanedActiveActivityFiles(100)).thenReturn(listOf(orphan))
        whenever(files.markOrphanedDeleted(91L, 3, nowDateTime)).thenReturn(1)
        whenever(storage.listObjectKeysOlderThan(now.minus(properties.temporaryTtl)))
            .thenReturn(emptyList())
        val job = FileCleanupJob(
            files, storage, properties, Clock.fixed(now, ZoneOffset.UTC)
        )

        job.cleanupExpiredAndOrphanedObjects()

        verify(files).findOrphanedActiveActivityFiles(100)
        verify(files).markOrphanedDeleted(91L, 3, nowDateTime)
        verify(storage, never()).delete(any())
    }

    @Test
    fun activeOrphanedPostFileIsMarkedDeletedWithFinalReferenceCheck() {
        val files = mock<FileObjectMapper>()
        val storage = mock<FileStorage>()
        val properties = FileStorageProperties()
        val orphan = FileObjectEntity()
        orphan.id = 92L
        orphan.version = 4
        val nowDateTime = LocalDateTime.parse("2026-07-22T08:00:00")
        whenever(files.findExpiredTemporaryFiles(nowDateTime, 100)).thenReturn(emptyList())
        whenever(files.findOrphanedActiveActivityFiles(100)).thenReturn(emptyList())
        whenever(files.findOrphanedActivePostFiles(100)).thenReturn(listOf(orphan))
        whenever(files.markOrphanedPostDeleted(92L, 4, nowDateTime)).thenReturn(1)
        whenever(storage.listObjectKeysOlderThan(any())).thenReturn(emptyList())
        val job = FileCleanupJob(
            files, storage, properties, Clock.fixed(now, ZoneOffset.UTC)
        )

        job.cleanupExpiredAndOrphanedObjects()

        verify(files).findOrphanedActivePostFiles(100)
        verify(files).markOrphanedPostDeleted(92L, 4, nowDateTime)
    }
}
