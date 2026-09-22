package com.eligo.server.file.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

@TableName("file_objects")
class FileObjectEntity {
    @TableId(type = IdType.ASSIGN_ID)
    var id: Long? = null
    var uploaderType: Int? = null
    var uploaderId: Long? = null
    var purpose: String? = null
    var storageProvider: String? = null
    var bucketName: String? = null
    var objectKey: String? = null
    var originalFilename: String? = null
    var contentType: String? = null
    var fileExtension: String? = null
    var sizeBytes: Long? = null
    var sha256: ByteArray? = null
    var accessLevel: Int? = null
    var scanStatus: Int? = null
    var lifecycleStatus: Int? = null
    var expiresAt: LocalDateTime? = null
    var activatedAt: LocalDateTime? = null
    var deletedAt: LocalDateTime? = null
    var version: Int? = null
    var createdAt: LocalDateTime? = null
    var updatedAt: LocalDateTime? = null

    companion object {
        const val UPLOADER_USER = 1
        const val PURPOSE_AVATAR = "AVATAR"
        const val PURPOSE_ACTIVITY = "ACTIVITY"
        const val PURPOSE_ACTIVITY_CONTACT_QR = "ACTIVITY_CONTACT_QR"
        const val PURPOSE_POST = "POST"
        const val ACCESS_PUBLIC = 1
        const val ACCESS_PRIVATE = 2
        const val SCAN_PENDING = 1
        const val SCAN_PASSED = 2
        const val SCAN_FAILED = 3
        const val LIFECYCLE_TEMPORARY = 1
        const val LIFECYCLE_ACTIVE = 2
        const val LIFECYCLE_DELETED = 3
    }
}
