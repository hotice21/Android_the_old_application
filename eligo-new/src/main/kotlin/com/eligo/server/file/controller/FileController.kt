package com.eligo.server.file.controller

import com.eligo.server.common.api.Result
import com.eligo.server.file.service.FileService
import com.eligo.server.file.vo.FileView
import com.eligo.server.security.UserPrincipal
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
class FileController(private val service: FileService) {

    @PostMapping(value = ["/api/v1/files/images"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam("file") file: MultipartFile,
        @RequestParam("purpose") purpose: String
    ): ResponseEntity<Result<FileView>> =
        ResponseEntity.status(201).body(Result.success(service.uploadImage(principal, file, purpose)))

    @GetMapping("/api/v1/files/{fileId}")
    fun get(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable fileId: Long
    ): Result<FileView?> = Result.success(service.getOwned(principal, fileId))

    @DeleteMapping("/api/v1/files/{fileId}")
    fun delete(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable fileId: Long
    ): Result<Void> {
        service.deleteTemporary(principal, fileId)
        return Result.success()
    }

    @GetMapping("/api/v1/files/{fileId}/content")
    fun content(
        @AuthenticationPrincipal principal: UserPrincipal?,
        @PathVariable fileId: Long
    ): ResponseEntity<InputStreamResource> {
        val content = service.openContent(principal, fileId)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(content.contentType))
            .header(HttpHeaders.CACHE_CONTROL, content.cacheControl)
            .body(InputStreamResource(content.input))
    }
}
