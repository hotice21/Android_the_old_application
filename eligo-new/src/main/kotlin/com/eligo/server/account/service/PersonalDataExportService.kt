package com.eligo.server.account.service

import com.eligo.server.account.entity.AccountSecurityEventEntity
import com.eligo.server.account.entity.UserEntity
import com.eligo.server.account.entity.UserPhoneBindingEntity
import com.eligo.server.account.mapper.AccountSecurityEventMapper
import com.eligo.server.account.mapper.UserMapper
import com.eligo.server.account.mapper.UserPhoneBindingMapper
import com.eligo.server.agreement.entity.AgreementConsentEntity
import com.eligo.server.agreement.mapper.AgreementConsentMapper
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.file.FileStorageProperties
import com.eligo.server.file.entity.FileObjectEntity
import com.eligo.server.file.mapper.FileObjectMapper
import com.eligo.server.file.storage.FileStorage
import com.eligo.server.follow.entity.OrganizationFollowEntity
import com.eligo.server.follow.entity.UserFollowEntity
import com.eligo.server.follow.mapper.OrganizationFollowMapper
import com.eligo.server.follow.mapper.UserFollowMapper
import com.eligo.server.post.entity.PostEntity
import com.eligo.server.post.mapper.PostMapper
import com.eligo.server.profile.entity.UserInterestTagEntity
import com.eligo.server.profile.mapper.UserInterestTagMapper
import com.eligo.server.profile.mapper.UserProfileMapper
import com.eligo.server.security.SensitiveDataCodec
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.time.LocalDateTime
import java.util.LinkedHashMap
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.ObjectMapper

@Service
@Profile("!test")
class PersonalDataExportService(
    private val users: UserMapper,
    private val profiles: UserProfileMapper,
    private val interests: UserInterestTagMapper,
    private val consents: AgreementConsentMapper,
    private val phones: UserPhoneBindingMapper,
    private val securityEvents: AccountSecurityEventMapper,
    private val userFollows: UserFollowMapper,
    private val organizationFollows: OrganizationFollowMapper,
    private val posts: PostMapper,
    private val files: FileObjectMapper,
    private val storage: FileStorage,
    private val properties: FileStorageProperties,
    private val codec: SensitiveDataCodec,
    private val phoneMasker: PhoneMasker,
    private val objectMapper: ObjectMapper
) {

    fun create(userId: Long, expiresAt: LocalDateTime): ExportResult {
        val user = users.selectById(userId)
            ?: throw BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND)
        val objectKey = "exports/" + UUID.randomUUID() + ".zip"
        var archive: Path? = null
        var json: Path? = null
        try {
            val maxFiles = properties.export.maxFiles
            if (maxFiles < 0 || maxFiles == Int.MAX_VALUE) {
                throw ExportLimitExceededException()
            }
            val exportable = exportableFiles(userId, maxFiles)
            validateDeclaredLimits(exportable)
            archive = Files.createTempFile("eligo-personal-data-", ".zip")
            json = Files.createTempFile("eligo-personal-data-", ".json")
            writeStructuredJson(json, userId, user, exportable)
            validateCombinedDeclaredLimit(json, exportable)
            writeArchive(archive, json, exportable)
            val archiveSize = Files.size(archive)
            val digest = sha256Digest()
            DigestInputStream(
                BufferedInputStream(Files.newInputStream(archive)), digest
            ).use { input ->
                storage.save(objectKey, input, archiveSize)
            }
            val file = resultFile(userId, objectKey, archiveSize, digest.digest(), expiresAt)
            files.insert(file)
            return ExportResult(file.id!!, objectKey)
        } catch (exception: ExportLimitExceededException) {
            deleteStoredObject(objectKey)
            throw exception
        } catch (exception: Exception) {
            deleteStoredObject(objectKey)
            if (exception is BusinessException) {
                throw exception
            }
            throw BusinessException(CommonErrorCode.INTERNAL_ERROR)
        } finally {
            deleteTemporaryFile(json)
            deleteTemporaryFile(archive)
        }
    }

    private fun validateDeclaredLimits(exportable: List<FileObjectEntity>) {
        if (exportable.size > properties.export.maxFiles) {
            throw ExportLimitExceededException()
        }
        var total = 0L
        for (file in exportable) {
            total = addWithinLimit(total, file.sizeBytes, properties.export.maxTotalBytes)
        }
    }

    private fun validateCombinedDeclaredLimit(json: Path, exportable: List<FileObjectEntity>) {
        var total = Files.size(json)
        if (total > properties.export.maxTotalBytes) {
            throw ExportLimitExceededException()
        }
        for (file in exportable) {
            total = addWithinLimit(total, file.sizeBytes, properties.export.maxTotalBytes)
        }
    }

    private fun addWithinLimit(total: Long, size: Long?, limit: Long): Long {
        if (size == null || size < 0 || total < 0 || limit < 0) {
            throw ExportLimitExceededException()
        }
        val newTotal = try {
            java.lang.Math.addExact(total, size)
        } catch (exception: ArithmeticException) {
            throw ExportLimitExceededException()
        }
        if (newTotal > limit) {
            throw ExportLimitExceededException()
        }
        return newTotal
    }

    private fun writeStructuredJson(json: Path, userId: Long, user: UserEntity, exportable: List<FileObjectEntity>) {
        BufferedOutputStream(Files.newOutputStream(json)).use { fileOutput ->
            LimitedOutputStream(fileOutput, properties.export.maxStructuredBytes).use { limited ->
                objectMapper.createGenerator(limited).use { generator ->
                    generator.writeStartObject()
                    writeValue(
                        generator, "account", mapOf(
                            "userId" to userId.toString(),
                            "status" to user.status,
                            "createdAt" to user.createdAt
                        )
                    )
                    writeValue(generator, "profile", profile(userId))
                    writeValue(generator, "interests", interests.findAllByUserId(userId).map { interest(it) })
                    writeConsentPages(generator, userId)
                    writeValue(generator, "phone", phones.findActiveByUserId(userId).map { phone(it) }.orElse(null))
                    writeSecurityEventPages(generator, userId)
                    writeUserFollowPages(generator, userId, true)
                    writeOrganizationFollowPages(generator, userId)
                    writeUserFollowPages(generator, userId, false)
                    writePostPages(generator, userId)
                    writeValue(generator, "files", exportable.map { fileMetadata(it) })
                    generator.writeEndObject()
                }
            }
        }
    }

    private fun writeConsentPages(generator: JsonGenerator, userId: Long) {
        generator.writeArrayPropertyStart("agreementConsents")
        var afterId: Long? = null
        while (true) {
            val page = consents.findExportPage(userId, afterId, EXPORT_PAGE_SIZE)
            if (page.isNullOrEmpty()) break
            for (value in page) {
                val id = value.id
                if (id == null || (afterId != null && id <= afterId)) {
                    throw IllegalStateException("协议导出分页游标未前进")
                }
                objectMapper.writeValue(generator, consent(value))
                afterId = id
            }
            if (page.size < EXPORT_PAGE_SIZE) break
        }
        generator.writeEndArray()
    }

    private fun writeSecurityEventPages(generator: JsonGenerator, userId: Long) {
        generator.writeArrayPropertyStart("securityEvents")
        var cursorTime: LocalDateTime? = null
        var cursorId: Long? = null
        while (true) {
            val page = securityEvents.findExportPage(userId, cursorTime, cursorId, EXPORT_PAGE_SIZE)
            if (page.isNullOrEmpty()) break
            for (value in page) {
                if (!advances(cursorTime, cursorId, value)) {
                    throw IllegalStateException("安全事件导出分页游标未前进")
                }
                objectMapper.writeValue(generator, security(value))
                cursorTime = value.occurredAt
                cursorId = value.id
            }
            if (page.size < EXPORT_PAGE_SIZE) break
        }
        generator.writeEndArray()
    }

    private fun writeUserFollowPages(generator: JsonGenerator, userId: Long, following: Boolean) {
        generator.writeArrayPropertyStart(if (following) "followingUsers" else "followers")
        var afterId: Long? = null
        while (true) {
            val page = if (following)
                userFollows.findFollowingExportPage(userId, afterId, EXPORT_PAGE_SIZE)
            else
                userFollows.findFollowerExportPage(userId, afterId, EXPORT_PAGE_SIZE)
            if (page.isNullOrEmpty()) break
            for (value in page) {
                afterId = advancingId(afterId, value.id, "关注关系")
                objectMapper.writeValue(generator, userFollow(value, following))
            }
            if (page.size < EXPORT_PAGE_SIZE) break
        }
        generator.writeEndArray()
    }

    private fun writeOrganizationFollowPages(generator: JsonGenerator, userId: Long) {
        generator.writeArrayPropertyStart("followingOrganizations")
        var afterId: Long? = null
        while (true) {
            val page = organizationFollows.findExportPage(userId, afterId, EXPORT_PAGE_SIZE)
            if (page.isNullOrEmpty()) break
            for (value in page) {
                afterId = advancingId(afterId, value.id, "企业关注关系")
                objectMapper.writeValue(generator, organizationFollow(value))
            }
            if (page.size < EXPORT_PAGE_SIZE) break
        }
        generator.writeEndArray()
    }

    private fun writePostPages(generator: JsonGenerator, userId: Long) {
        generator.writeArrayPropertyStart("posts")
        var afterId: Long? = null
        while (true) {
            val page = posts.findPersonalExportPage(userId, afterId, EXPORT_PAGE_SIZE)
            if (page.isNullOrEmpty()) break
            for (value in page) {
                afterId = advancingId(afterId, value.id, "个人动态")
                objectMapper.writeValue(generator, post(value))
            }
            if (page.size < EXPORT_PAGE_SIZE) break
        }
        generator.writeEndArray()
    }

    private fun advancingId(afterId: Long?, nextId: Long?, subject: String): Long {
        if (nextId == null || (afterId != null && nextId <= afterId)) {
            throw IllegalStateException(subject + "导出分页游标未前进")
        }
        return nextId
    }

    private fun exportableFiles(userId: Long, maxFiles: Int): List<FileObjectEntity> {
        val queryLimit = maxFiles + 1
        val byId = LinkedHashMap<Long, FileObjectEntity>()
        for (file in files.findExportableByUserId(userId, queryLimit)) {
            addExportFile(byId, file)
        }
        for (file in files.findExportablePersonalPostMedia(userId, queryLimit)) {
            addExportFile(byId, file)
        }
        return byId.values.sortedBy { it.id }
    }

    private fun addExportFile(filesById: MutableMap<Long, FileObjectEntity>, file: FileObjectEntity?) {
        if (file == null || file.id == null) {
            throw IllegalStateException("个人数据导出文件缺少编号")
        }
        filesById.putIfAbsent(file.id!!, file)
    }

    private fun advances(cursorTime: LocalDateTime?, cursorId: Long?, value: AccountSecurityEventEntity): Boolean {
        val occurredAt = value.occurredAt ?: return false
        val id = value.id ?: return false
        if (cursorTime == null) return true
        val timeComparison = occurredAt.compareTo(cursorTime)
        return timeComparison > 0 || (timeComparison == 0 && id > cursorId!!)
    }

    private fun writeValue(generator: JsonGenerator, name: String, value: Any?) {
        generator.writeName(name)
        objectMapper.writeValue(generator, value)
    }

    private fun writeArchive(archive: Path, json: Path, exportable: List<FileObjectEntity>) {
        var copied = 0L
        BufferedOutputStream(Files.newOutputStream(archive)).use { output ->
            ZipOutputStream(output, StandardCharsets.UTF_8).use { zip ->
                zip.putNextEntry(ZipEntry("personal-data.json"))
                BufferedInputStream(Files.newInputStream(json)).use { input ->
                    copied = copyLimited(input, zip, copied, properties.export.maxTotalBytes)
                }
                zip.closeEntry()
                for (file in exportable) {
                    zip.putNextEntry(ZipEntry("files/" + file.id + "-" + safeName(file.originalFilename)))
                    BufferedInputStream(storage.open(file.objectKey!!)).use { input ->
                        copied = copyLimited(input, zip, copied, properties.export.maxTotalBytes)
                    }
                    zip.closeEntry()
                }
                zip.finish()
            }
        }
    }

    private fun copyLimited(input: InputStream, output: OutputStream, alreadyCopied: Long, limit: Long): Long {
        val buffer = ByteArray(8192)
        var total = alreadyCopied
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            total = addWithinLimit(total, read.toLong(), limit)
            output.write(buffer, 0, read)
        }
        return total
    }

    private fun resultFile(
        userId: Long, objectKey: String, size: Long,
        sha256: ByteArray, expiresAt: LocalDateTime
    ): FileObjectEntity {
        val file = FileObjectEntity()
        file.uploaderType = FileObjectEntity.UPLOADER_USER
        file.uploaderId = userId
        file.storageProvider = properties.storage.provider
        file.bucketName = "local"
        file.objectKey = objectKey
        file.originalFilename = "eligo-personal-data.zip"
        file.contentType = "application/zip"
        file.fileExtension = "zip"
        file.sizeBytes = size
        file.sha256 = sha256
        file.accessLevel = 2
        file.scanStatus = FileObjectEntity.SCAN_PASSED
        file.lifecycleStatus = FileObjectEntity.LIFECYCLE_TEMPORARY
        file.expiresAt = expiresAt
        file.version = 0
        file.createdAt = expiresAt.minusDays(1)
        file.updatedAt = expiresAt.minusDays(1)
        return file
    }

    private fun profile(userId: Long): Map<String, Any?> =
        profiles.findByUserId(userId).map { value ->
            val data = LinkedHashMap<String, Any?>()
            data["nickname"] = value.nickname
            data["birthDate"] = decrypt(value.birthDateCiphertext)
            data["genderCode"] = value.genderCode
            data["provinceCode"] = value.provinceCode
            data["provinceName"] = value.provinceName
            data["cityCode"] = value.cityCode
            data["cityName"] = value.cityName
            data["districtCode"] = value.districtCode
            data["districtName"] = value.districtName
            data["bio"] = value.bio
            data
        }.orElseGet { LinkedHashMap() }

    private fun interest(value: UserInterestTagEntity): Map<String, Any?> = mapOf(
        "tagId" to value.interestTagId.toString(),
        "selectedAt" to value.selectedAt
    )

    private fun consent(value: AgreementConsentEntity): Map<String, Any?> = mapOf(
        "agreementId" to value.agreementId.toString(),
        "agreedAt" to value.agreedAt,
        "clientVersion" to (value.clientVersion ?: "")
    )

    private fun phone(value: UserPhoneBindingEntity): Map<String, Any?> {
        val plain = codec.decrypt(String(value.phoneCiphertext!!, StandardCharsets.UTF_8))
        return mapOf(
            "countryCode" to value.countryCode,
            "maskedPhone" to phoneMasker.mask(plain),
            "boundAt" to value.boundAt
        )
    }

    private fun security(value: AccountSecurityEventEntity): Map<String, Any?> {
        val data = LinkedHashMap<String, Any?>()
        data["eventId"] = value.id.toString()
        data["type"] = value.eventType
        data["severity"] = value.severity
        data["regionCode"] = value.regionCode
        data["occurredAt"] = value.occurredAt
        return data
    }

    private fun userFollow(value: UserFollowEntity, following: Boolean): Map<String, Any?> {
        val key = if (following) "followedUserId" else "followerUserId"
        val id = if (following) value.followedUserId else value.followerUserId
        return mapOf(
            key to id.toString(),
            "followedAt" to value.followedAt
        )
    }

    private fun organizationFollow(value: OrganizationFollowEntity): Map<String, Any?> = mapOf(
        "organizationId" to value.organizationId.toString(),
        "followedAt" to value.followedAt
    )

    private fun post(value: PostEntity): Map<String, Any?> {
        val data = LinkedHashMap<String, Any?>()
        data["postId"] = value.id.toString()
        data["status"] = postStatus(value.status)
        data["visibility"] = postVisibility(value.visibility)
        data["title"] = value.title
        data["content"] = value.content
        data["activityId"] = value.activityId?.toString()
        data["publishedAt"] = value.publishedAt
        data["hiddenAt"] = value.hiddenAt
        data["deletedAt"] = value.deletedAt
        data["createdAt"] = value.createdAt
        data["updatedAt"] = value.updatedAt
        return data
    }

    private fun postStatus(status: Int?): String? = when (status) {
        null -> null
        PostEntity.STATUS_DRAFT -> "DRAFT"
        PostEntity.STATUS_PUBLISHED -> "PUBLISHED"
        PostEntity.STATUS_DELETED -> "DELETED"
        PostEntity.STATUS_HIDDEN -> "HIDDEN"
        else -> throw IllegalStateException("个人动态状态非法")
    }

    private fun postVisibility(visibility: Int?): String? = when (visibility) {
        null -> null
        PostEntity.VISIBILITY_PUBLIC -> "PUBLIC"
        PostEntity.VISIBILITY_FOLLOWERS_ONLY -> "FOLLOWERS_ONLY"
        PostEntity.VISIBILITY_PRIVATE -> "PRIVATE"
        else -> throw IllegalStateException("个人动态可见范围非法")
    }

    private fun fileMetadata(value: FileObjectEntity): Map<String, Any?> = mapOf(
        "fileId" to value.id.toString(),
        "filename" to value.originalFilename,
        "contentType" to value.contentType,
        "sizeBytes" to value.sizeBytes
    )

    private fun decrypt(value: ByteArray?): String? =
        value?.let { codec.decrypt(String(it, StandardCharsets.UTF_8)) }

    private fun safeName(value: String?): String =
        value?.replace("[^\\p{L}\\p{N}._-]".toRegex(), "_") ?: "file"

    private fun sha256Digest(): MessageDigest =
        try {
            MessageDigest.getInstance("SHA-256")
        } catch (exception: NoSuchAlgorithmException) {
            throw IllegalStateException(exception)
        }

    private fun deleteStoredObject(objectKey: String) {
        try {
            storage.delete(objectKey)
        } catch (ignored: Exception) {
        }
    }

    private fun deleteTemporaryFile(path: Path?) {
        if (path == null) return
        try {
            Files.deleteIfExists(path)
        } catch (ignored: IOException) {
        }
    }

    fun discard(result: ExportResult) {
        val objectKey = result.objectKey ?: return
        deleteStoredObject(objectKey)
    }

    data class ExportResult(val fileId: Long, val objectKey: String?) {
        constructor(fileId: Long) : this(fileId, null)
    }

    class ExportLimitExceededException : RuntimeException("个人数据导出超过允许上限")

    private class LimitedOutputStream(output: OutputStream, private val limit: Long) : FilterOutputStream(output) {
        private var written: Long = 0

        @Throws(IOException::class)
        override fun write(value: Int) {
            ensureCapacity(1)
            out.write(value)
            written++
        }

        @Throws(IOException::class)
        override fun write(value: ByteArray, offset: Int, length: Int) {
            ensureCapacity(length)
            out.write(value, offset, length)
            written += length
        }

        private fun ensureCapacity(length: Int) {
            if (length < 0 || limit < 0 || written > limit - length) {
                throw ExportLimitExceededException()
            }
        }
    }

    companion object {
        private const val EXPORT_PAGE_SIZE = 100
    }
}
