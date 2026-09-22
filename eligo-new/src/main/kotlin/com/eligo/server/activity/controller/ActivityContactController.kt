package com.eligo.server.activity.controller

import com.eligo.server.activity.service.ActivityContactAccessService
import com.eligo.server.file.service.FileService
import com.eligo.server.security.UserPrincipal
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class ActivityContactController(private val service: ActivityContactAccessService) {

    @GetMapping("/api/v1/activities/{activityId}/organizer/wechat-qr")
    fun organizerWechatQr(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable activityId: Long
    ): ResponseEntity<InputStreamResource> {
        val content: FileService.FileContent = service.openOrganizerWechatQr(
            principal, activityId
        )
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(content.contentType))
            .header(HttpHeaders.CACHE_CONTROL, content.cacheControl)
            .body(InputStreamResource(content.input))
    }
}
