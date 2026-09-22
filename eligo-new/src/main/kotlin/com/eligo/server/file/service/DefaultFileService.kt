package com.eligo.server.file.service

import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.file.FileStorageProperties
import com.eligo.server.file.entity.FileObjectEntity
import com.eligo.server.file.mapper.FileObjectMapper
import com.eligo.server.file.storage.FileStorage
import com.eligo.server.file.vo.FileView
import com.eligo.server.post.service.PostReadService
import com.eligo.server.security.UserPrincipal
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.multipart.MultipartFile

@Service
@Profile("!test")
class DefaultFileService(
    private val files: FileObjectMapper,
    private val storage: FileStorage,
    private val inspector: ImageInspectionService,
    private val properties: FileStorageProperties,
    private val postReads: PostReadService,
    private val clock: Clock = Clock.systemUTC()
) : FileService {

    @Transactional
    override fun uploadAvatarImage(principal: UserPrincipal, file: MultipartFile): FileView =
        uploadImage(principal, file, FileObjectEntity.PURPOSE_AVATAR)

    @Transactional
    override fun uploadImage(principal: UserPrincipal, file: MultipartFile?, purpose: String): FileView {
        if (file == null || file.isEmpty) throw unsupported()
        val normalizedPurpose = normalizePurpose(purpose)
        val contentType = normalizeContentType(file.contentType)
        val extension = extension(file.originalFilename)
        validateExtension(contentType, extension)
        val bytes = readWithinLimit(file)
        inspector.inspect(contentType, bytes)
        val objectKey = storagePrefix(normalizedPurpose) + UUID.randomUUID() + "." + extension
        try {
            storage.save(objectKey, ByteArrayInputStream(bytes), bytes.size.toLong())
        } catch (exception: IOException) {
            throw BusinessException(CommonErrorCode.INTERNAL_ERROR)
        }
        try {
            val entity = FileObjectEntity()
            entity.uploaderType = FileObjectEntity.UPLOADER_USER
            entity.uploaderId = principal.userId
            entity.purpose = normalizedPurpose
            entity.storageProvider = properties.storage.provider
            entity.bucketName = "local"
            entity.objectKey = objectKey
            entity.originalFilename = cleanFilename(file.originalFilename, extension)
            entity.contentType = contentType
            entity.fileExtension = extension
            entity.sizeBytes = bytes.size.toLong()
            entity.sha256 = sha256(bytes)
            entity.accessLevel = if (FileObjectEntity.PURPOSE_ACTIVITY_CONTACT_QR == normalizedPurpose)
                FileObjectEntity.ACCESS_PRIVATE else FileObjectEntity.ACCESS_PUBLIC
            entity.scanStatus = FileObjectEntity.SCAN_PASSED
            entity.lifecycleStatus = FileObjectEntity.LIFECYCLE_TEMPORARY
            val createdAt = now()
            entity.expiresAt = createdAt.plus(properties.temporaryTtl)
            entity.version = 0
            entity.createdAt = createdAt
            entity.updatedAt = createdAt
            files.insert(entity)
            registerRollbackCleanup(objectKey)
            return view(entity, false)
        } catch (exception: RuntimeException) {
            deleteQuietly(objectKey)
            throw exception
        }
    }

    override fun getOwned(principal: UserPrincipal, fileId: Long): FileView =
        view(requireOwned(principal.userId, fileId), true)

    @Transactional
    override fun deleteTemporary(principal: UserPrincipal, fileId: Long) {
        val file = files.lockById(fileId).orElseThrow(::notFound)
        if (!ownedBy(file, principal.userId)) throw notFound()
        if (file.lifecycleStatus == FileObjectEntity.LIFECYCLE_DELETED) return
        if (file.lifecycleStatus != FileObjectEntity.LIFECYCLE_TEMPORARY) throw stateConflict()
        if (files.markDeleted(fileId, now(), file.version!!) != 1) throw stateConflict()
        deleteAfterCommit(fileId, file.objectKey!!)
    }

    override fun requireUsableAvatar(userId: Long, fileId: Long): FileObjectEntity {
        val file = files.lockById(fileId).orElseThrow(::stateConflict)
        if (!ownedBy(file, userId) ||
            FileObjectEntity.PURPOSE_AVATAR != file.purpose ||
            file.accessLevel != FileObjectEntity.ACCESS_PUBLIC ||
            file.scanStatus != FileObjectEntity.SCAN_PASSED ||
            file.lifecycleStatus != FileObjectEntity.LIFECYCLE_TEMPORARY ||
            (file.expiresAt != null && !file.expiresAt!!.isAfter(now()))
        ) {
            throw stateConflict()
        }
        return file
    }

    override fun activateAvatar(file: FileObjectEntity) {
        if (files.activate(file.id!!, now(), file.version!!) != 1) throw stateConflict()
    }

    override fun openOwnedContent(principal: UserPrincipal, fileId: Long): InputStream {
        val file = requireOwned(principal.userId, fileId)
        if (!isAccessible(file)) throw notFound()
        return try {
            storage.open(file.objectKey!!)
        } catch (exception: IOException) {
            throw notFound()
        }
    }

    override fun openContent(principal: UserPrincipal?, fileId: Long): FileService.FileContent {
        val file = files.findById(fileId).orElseThrow(::notFound)
        val policyAccess = isPolicyAccessible(file, principal)
        val uploaderPreview = principal != null &&
            ownedBy(file, principal.userId) &&
            isAccessible(file) &&
            (FileObjectEntity.PURPOSE_POST != file.purpose ||
                file.lifecycleStatus == FileObjectEntity.LIFECYCLE_TEMPORARY)
        if (!policyAccess && !uploaderPreview) {
            throw notFound()
        }
        return try {
            FileService.FileContent(
                storage.open(file.objectKey!!),
                file.contentType!!,
                cacheControl(file, policyAccess)
            )
        } catch (exception: IOException) {
            throw notFound()
        }
    }

    override fun openAuthorizedActivityContactContent(fileId: Long): FileService.FileContent {
        val file = files.findById(fileId).orElseThrow(::notFound)
        val usable = FileObjectEntity.PURPOSE_ACTIVITY_CONTACT_QR == file.purpose &&
            file.accessLevel == FileObjectEntity.ACCESS_PRIVATE &&
            file.scanStatus == FileObjectEntity.SCAN_PASSED &&
            file.lifecycleStatus == FileObjectEntity.LIFECYCLE_ACTIVE &&
            (file.expiresAt == null || file.expiresAt!!.isAfter(now())) &&
            files.existsActivityContactQrReference(fileId)
        if (!usable) {
            throw notFound()
        }
        return try {
            FileService.FileContent(
                storage.open(file.objectKey!!),
                file.contentType!!,
                "private, max-age=60"
            )
        } catch (exception: IOException) {
            throw notFound()
        }
    }

    private fun readWithinLimit(file: MultipartFile): ByteArray {
        val max = maxSize()
        if (file.size <= 0 || file.size > max) throw sizeExceeded()
        try {
            file.inputStream.use { input ->
                val bytes = input.readNBytes(minOf(max + 1, Int.MAX_VALUE.toLong()).toInt())
                if (bytes.isEmpty() || bytes.size > max || input.read() != -1) throw sizeExceeded()
                return bytes
            }
        } catch (exception: IOException) {
            throw BusinessException(CommonErrorCode.INTERNAL_ERROR)
        }
    }

    private fun maxSize(): Long {
        val raw = properties.image.maxSize.trim().uppercase(Locale.ROOT)
        if (!raw.endsWith("MB")) throw IllegalStateException("图片大小配置非法")
        return raw.substring(0, raw.length - 2).trim().toLong() * 1024 * 1024
    }

    private fun normalizeContentType(value: String?): String {
        val result = value?.lowercase(Locale.ROOT)?.trim() ?: ""
        if (!properties.image.allowedContentTypes.contains(result)) throw unsupported()
        return result
    }

    private fun normalizePurpose(value: String?): String {
        val normalized = value?.trim()?.uppercase(Locale.ROOT) ?: ""
        if (!setOf(
                FileObjectEntity.PURPOSE_AVATAR,
                FileObjectEntity.PURPOSE_ACTIVITY,
                FileObjectEntity.PURPOSE_ACTIVITY_CONTACT_QR,
                FileObjectEntity.PURPOSE_POST
            ).contains(normalized)
        ) {
            throw BusinessException(CommonErrorCode.VALIDATION_FAILED)
        }
        return normalized
    }

    private fun storagePrefix(purpose: String): String = when (purpose) {
        FileObjectEntity.PURPOSE_AVATAR -> "avatars/"
        FileObjectEntity.PURPOSE_ACTIVITY -> "activities/"
        FileObjectEntity.PURPOSE_ACTIVITY_CONTACT_QR -> "activity-contact-qrs/"
        FileObjectEntity.PURPOSE_POST -> "posts/"
        else -> throw IllegalArgumentException("图片用途非法")
    }

    private fun extension(filename: String?): String {
        if (filename == null) throw unsupported()
        val name = filename.replace('\\', '/')
        val dot = name.lastIndexOf('.')
        if (dot < 1 || dot == name.length - 1) throw unsupported()
        val extension = name.substring(dot + 1).lowercase(Locale.ROOT)
        return if ("jpeg" == extension) "jpg" else extension
    }

    private fun validateExtension(contentType: String, extension: String) {
        val expected = if ("image/jpeg" == contentType) setOf("jpg") else setOf("png")
        if (!expected.contains(extension)) throw unsupported()
    }

    private fun cleanFilename(raw: String?, extension: String): String {
        var name = raw?.replace('\\', '/') ?: ("image.$extension")
        name = name.substring(name.lastIndexOf('/') + 1).replace("[\\p{Cntrl}]".toRegex(), "").trim()
        return if (name.isBlank()) "image.$extension" else name.substring(0, minOf(255, name.length))
    }

    private fun requireOwned(userId: Long, fileId: Long): FileObjectEntity {
        val file = files.findById(fileId).orElseThrow(::notFound)
        if (!ownedBy(file, userId)) throw notFound()
        return file
    }

    private fun ownedBy(file: FileObjectEntity, userId: Long): Boolean =
        file.uploaderType == FileObjectEntity.UPLOADER_USER && file.uploaderId == userId

    private fun view(file: FileObjectEntity, includeUrl: Boolean): FileView {
        val url = if (includeUrl && isAccessible(file))
            "/api/v1/files/" + file.id + "/content" else null
        return FileView(
            file.id.toString(),
            purposeName(file.purpose),
            file.contentType!!,
            file.sizeBytes!!,
            if (file.accessLevel == FileObjectEntity.ACCESS_PRIVATE) "PRIVATE" else "PUBLIC",
            scanName(file.scanStatus!!),
            lifecycleName(file.lifecycleStatus!!),
            instant(file.expiresAt),
            url
        )
    }

    private fun purposeName(purpose: String?): String = purpose ?: FileObjectEntity.PURPOSE_AVATAR

    private fun scanName(status: Int): String =
        if (status == 2) "PASSED" else if (status == 3) "REJECTED" else "PENDING"

    private fun isAccessible(file: FileObjectEntity): Boolean {
        if (file.scanStatus != FileObjectEntity.SCAN_PASSED ||
            file.lifecycleStatus == FileObjectEntity.LIFECYCLE_DELETED
        ) {
            return false
        }
        return file.lifecycleStatus == FileObjectEntity.LIFECYCLE_ACTIVE ||
            file.expiresAt == null ||
            file.expiresAt!!.isAfter(now())
    }

    private fun isPolicyAccessible(file: FileObjectEntity, principal: UserPrincipal?): Boolean {
        val fileIsPublic = file.accessLevel == FileObjectEntity.ACCESS_PUBLIC &&
            file.scanStatus == FileObjectEntity.SCAN_PASSED &&
            file.lifecycleStatus == FileObjectEntity.LIFECYCLE_ACTIVE &&
            (file.expiresAt == null || file.expiresAt!!.isAfter(now()))
        if (!fileIsPublic) {
            return false
        }
        if (FileObjectEntity.PURPOSE_ACTIVITY_CONTACT_QR == file.purpose) {
            return false
        }
        if (FileObjectEntity.PURPOSE_ACTIVITY == file.purpose) {
            return files.existsPublicActivityReference(file.id!!)
        }
        if (FileObjectEntity.PURPOSE_POST == file.purpose) {
            return postReads.canReadMedia(principal, file.id!!)
        }
        return true
    }

    private fun cacheControl(file: FileObjectEntity, publicAccess: Boolean): String {
        if (FileObjectEntity.PURPOSE_POST == file.purpose) {
            return "no-store"
        }
        if (!publicAccess) {
            return "private, max-age=60"
        }
        return if (FileObjectEntity.PURPOSE_ACTIVITY == file.purpose) "no-store" else "public, max-age=300"
    }

    private fun lifecycleName(status: Int): String =
        if (status == 2) "ACTIVE" else if (status == 3) "DELETED" else "TEMPORARY"

    private fun now(): LocalDateTime = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)

    private fun instant(value: LocalDateTime?): Instant? = value?.toInstant(ZoneOffset.UTC)

    private fun sha256(value: ByteArray): ByteArray {
        return try {
            MessageDigest.getInstance("SHA-256").digest(value)
        } catch (e: NoSuchAlgorithmException) {
            throw IllegalStateException(e)
        }
    }

    private fun registerRollbackCleanup(objectKey: String) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCompletion(status: Int) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    deletePhysicalObject(null, objectKey, "回滚补偿")
                }
            }
        })
    }

    private fun deleteAfterCommit(fileId: Long, objectKey: String) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deletePhysicalObject(fileId, objectKey, "逻辑删除")
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                deletePhysicalObject(fileId, objectKey, "提交后清理")
            }
        })
    }

    private fun deletePhysicalObject(fileId: Long?, key: String, operation: String) {
        try {
            storage.delete(key)
        } catch (exception: IOException) {
            log.error("物理文件清理失败 operation={} fileId={}", operation, fileId)
        } catch (exception: RuntimeException) {
            log.error("物理文件清理失败 operation={} fileId={}", operation, fileId)
        }
    }

    private fun deleteQuietly(key: String) {
        deletePhysicalObject(null, key, "异常补偿")
    }

    private fun unsupported(): BusinessException = BusinessException(AccountUserFileErrorCode.UNSUPPORTED_FILE_TYPE)
    private fun sizeExceeded(): BusinessException = BusinessException(AccountUserFileErrorCode.FILE_TOO_LARGE)
    private fun stateConflict(): BusinessException = BusinessException(AccountUserFileErrorCode.FILE_STATE_CONFLICT)
    private fun notFound(): BusinessException = BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND)

    companion object {
        private val log = LoggerFactory.getLogger(DefaultFileService::class.java)
    }
}
