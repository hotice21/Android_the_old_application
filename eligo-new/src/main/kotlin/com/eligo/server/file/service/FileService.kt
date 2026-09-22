package com.eligo.server.file.service

import com.eligo.server.file.entity.FileObjectEntity
import com.eligo.server.file.vo.FileView
import com.eligo.server.security.UserPrincipal
import java.io.InputStream
import org.springframework.web.multipart.MultipartFile

interface FileService {
    fun uploadImage(principal: UserPrincipal, file: MultipartFile?, purpose: String): FileView
    fun uploadAvatarImage(principal: UserPrincipal, file: MultipartFile): FileView
    fun getOwned(principal: UserPrincipal, fileId: Long): FileView
    fun deleteTemporary(principal: UserPrincipal, fileId: Long)
    fun requireUsableAvatar(userId: Long, fileId: Long): FileObjectEntity
    fun activateAvatar(file: FileObjectEntity)
    fun openOwnedContent(principal: UserPrincipal, fileId: Long): InputStream
    fun openContent(principal: UserPrincipal?, fileId: Long): FileContent
    fun openAuthorizedActivityContactContent(fileId: Long): FileContent {
        throw UnsupportedOperationException("活动联系方式二维码读取不可用")
    }

    data class FileContent(val input: InputStream, val contentType: String, val cacheControl: String)
}
