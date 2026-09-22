package com.eligo.server.file

import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.common.web.GlobalExceptionHandler
import com.eligo.server.config.security.SecurityConfig
import com.eligo.server.file.controller.FileController
import com.eligo.server.file.service.FileService
import com.eligo.server.file.vo.FileView
import com.eligo.server.security.AccountRestrictionFilter
import com.eligo.server.security.AccountRestrictionReader
import com.eligo.server.security.SessionAccessReader
import com.eligo.server.security.SessionAuthenticationFilter
import com.eligo.server.security.UserPrincipal
import java.io.ByteArrayInputStream
import java.time.Instant
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication

@WebMvcTest(FileController::class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(
    GlobalExceptionHandler::class,
    SecurityConfig::class,
    SessionAuthenticationFilter::class,
    AccountRestrictionFilter::class
)
class FileControllerIntegrationTests {
    private val owner = UserPrincipal(202L, "session")

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var service: FileService

    @MockitoBean
    lateinit var sessionAccessReader: SessionAccessReader

    @MockitoBean
    lateinit var accountRestrictionReader: AccountRestrictionReader

    @Test
    fun uploadsImageAsCreatedWithoutInternalStorageFields() {
        whenever(service.uploadImage(eq(owner), any(), eq("AVATAR"))).thenReturn(uploadView())
        val file = MockMultipartFile("file", "avatar.png", "image/png", byteArrayOf(1))
        mockMvc.perform(
            multipart("/api/v1/files/images").file(file)
                .param("purpose", "AVATAR").with(authentication(auth()))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.fileId").value("77"))
            .andExpect(jsonPath("$.data.scanStatus").value("PASSED"))
            .andExpect(jsonPath("$.data.purpose").value("AVATAR"))
            .andExpect(jsonPath("$.data.objectKey").doesNotExist())
            .andExpect(jsonPath("$.data.url").doesNotExist())
    }

    @Test
    fun requiresPurposeWhenUploadingImage() {
        mockMvc.perform(
            multipart("/api/v1/files/images")
                .file(MockMultipartFile("file", "avatar.png", "image/png", byteArrayOf(1)))
                .with(authentication(auth()))
        )
            .andExpect(status().isBadRequest)
        verifyNoInteractions(service)
    }

    @Test
    fun onlyOwnerCanReadFileStateAndTemporaryDeleteIsIdempotent() {
        whenever(service.getOwned(owner, 77L)).thenReturn(view())
        mockMvc.perform(get("/api/v1/files/77").with(authentication(auth())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.url").value("/api/v1/files/77/content"))
        mockMvc.perform(delete("/api/v1/files/77").with(authentication(auth())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.code").value(0))
        org.mockito.kotlin.verify(service).deleteTemporary(owner, 77L)

        whenever(service.getOwned(owner, 88L)).thenThrow(BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND))
        mockMvc.perform(get("/api/v1/files/88").with(authentication(auth())))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value(10006))
    }

    @Test
    fun allowsAnonymousReadingOfPublicFileContentWithPublicCache() {
        whenever(service.openContent(isNull(), eq(77L)))
            .thenReturn(
                FileService.FileContent(
                    ByteArrayInputStream(byteArrayOf(1, 2)),
                    "image/png",
                    "public, max-age=300"
                )
            )

        mockMvc.perform(get("/api/v1/files/77/content"))
            .andExpect(status().isOk)
            .andExpect(content().contentType("image/png"))
            .andExpect(header().string("Cache-Control", "public, max-age=300"))
            .andExpect(content().bytes(byteArrayOf(1, 2)))
    }

    private fun uploadView(): FileView =
        FileView(
            "77", "image/png", 68, "PUBLIC", "PASSED", "TEMPORARY",
            Instant.parse("2026-07-23T08:00:00Z"), null
        )

    private fun view(): FileView =
        FileView(
            "77", "image/png", 68, "PUBLIC", "PASSED", "TEMPORARY",
            Instant.parse("2026-07-23T08:00:00Z"), "/api/v1/files/77/content"
        )

    private fun auth(): Authentication =
        UsernamePasswordAuthenticationToken(owner, "", emptyList())
}
