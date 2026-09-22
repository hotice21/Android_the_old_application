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
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@Profile("!test")
class FileCleanupJob(
    private val files: FileObjectMapper,
    private val storage: FileStorage,
    private val properties: FileStorageProperties,
    private val clock: Clock = Clock.systemUTC()
) {
    @Scheduled(
        initialDelayString = "\${eligo.file.cleanup-initial-delay-ms:60000}",
        fixedDelayString = "\${eligo.file.cleanup-interval-ms:3600000}"
    )
    fun cleanupExpiredAndOrphanedObjects() {
        markExpiredTemporaryFiles()
        markOrphanedActivityFiles()
        markOrphanedActivityContactQrFiles()
        markOrphanedPostFiles()
        cleanupOrphanedObjects()
    }

    internal fun markExpiredTemporaryFiles() {
        val now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
        var failed = 0
        for (file in files.findExpiredTemporaryFiles(now, EXPIRE_BATCH_SIZE)) {
            try {
                files.markExpiredDeleted(file.id!!, file.version!!, now)
            } catch (exception: RuntimeException) {
                failed++
            }
        }
        if (failed > 0) {
            log.error("到期临时文件状态迁移失败 count={}", failed)
        }
    }

    internal fun markOrphanedActivityFiles() {
        val now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
        var failed = 0
        for (file in files.findOrphanedActiveActivityFiles(EXPIRE_BATCH_SIZE)) {
            try {
                val version = file.version ?: 0
                files.markOrphanedDeleted(file.id!!, version, now)
            } catch (exception: RuntimeException) {
                failed++
            }
        }
        if (failed > 0) {
            log.error("活动孤儿文件状态迁移失败 count={}", failed)
        }
    }

    internal fun markOrphanedActivityContactQrFiles() {
        val now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
        var failed = 0
        for (file in files.findOrphanedActiveActivityContactQrFiles(EXPIRE_BATCH_SIZE)) {
            try {
                val version = file.version ?: 0
                files.markOrphanedActivityContactQrDeleted(file.id!!, version, now)
            } catch (exception: RuntimeException) {
                failed++
            }
        }
        if (failed > 0) {
            log.error("活动联系方式二维码孤儿文件状态迁移失败 count={}", failed)
        }
    }

    internal fun markOrphanedPostFiles() {
        val now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
        var failed = 0
        for (file in files.findOrphanedActivePostFiles(EXPIRE_BATCH_SIZE)) {
            try {
                val version = file.version ?: 0
                files.markOrphanedPostDeleted(file.id!!, version, now)
            } catch (exception: RuntimeException) {
                failed++
            }
        }
        if (failed > 0) {
            log.error("动态孤儿文件状态迁移失败 count={}", failed)
        }
    }

    fun cleanupOrphanedObjects() {
        val cutoff = clock.instant().minus(properties.temporaryTtl)
        var failed = 0
        try {
            for (objectKey in storage.listObjectKeysOlderThan(cutoff)) {
                if (files.countLiveByObjectKey(objectKey) != 0) {
                    continue
                }
                try {
                    storage.delete(objectKey)
                } catch (exception: IOException) {
                    failed++
                } catch (exception: RuntimeException) {
                    failed++
                }
            }
        } catch (exception: IOException) {
            log.error("物理文件扫尾列表读取失败")
            return
        } catch (exception: RuntimeException) {
            log.error("物理文件扫尾列表读取失败")
            return
        }
        if (failed > 0) {
            log.error("物理文件扫尾失败 count={}", failed)
        }
    }

    companion object {
        private const val EXPIRE_BATCH_SIZE = 100
        private val log = LoggerFactory.getLogger(FileCleanupJob::class.java)
    }
}
