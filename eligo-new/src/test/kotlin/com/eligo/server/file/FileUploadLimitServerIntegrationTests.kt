package com.eligo.server.file

import com.eligo.server.file.service.FileService
import com.eligo.server.file.vo.FileView
import com.eligo.server.security.JwtTokenService
import com.eligo.server.security.UserPrincipal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class FileUploadLimitServerIntegrationTests {

    private val owner = UserPrincipal(202L, "upload-limit-session")

    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var jwtTokenService: JwtTokenService

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @MockitoBean
    lateinit var fileService: FileService

    @Test
    fun acceptsImageLargerThanOneMegabyteWithinBusinessLimit() {
        whenever(fileService.uploadImage(eq(owner), any(), eq("AVATAR"))).thenReturn(uploadView())

        val response = upload(ByteArray(2 * 1024 * 1024))

        assertThat(response.statusCode()).isEqualTo(201)
        assertThat(objectMapper.readTree(response.body()).path("data").path("fileId").asText())
            .isEqualTo("77")
        verify(fileService).uploadImage(eq(owner), any(), eq("AVATAR"))
    }

    @Test
    fun rejectsImageLargerThanTenMegabytesWithStableBusinessCode() {
        val response = upload(ByteArray(10 * 1024 * 1024 + 1))

        val body = objectMapper.readTree(response.body())
        assertThat(response.statusCode()).isEqualTo(400)
        assertThat(body.path("code").asInt()).isEqualTo(11402)
        verifyNoInteractions(fileService)
    }

    private fun upload(fileBytes: ByteArray): HttpResponse<ByteArray> {
        val boundary = "EligoUploadBoundary"
        val prefix = ("--" + boundary + "\r\n" +
            "Content-Disposition: form-data; name=\"file\"; filename=\"avatar.png\"\r\n" +
            "Content-Type: image/png\r\n\r\n").toByteArray(StandardCharsets.UTF_8)
        val purpose = ("\r\n--" + boundary + "\r\n" +
            "Content-Disposition: form-data; name=\"purpose\"\r\n\r\n" +
            "AVATAR").toByteArray(StandardCharsets.UTF_8)
        val suffix = ("\r\n--" + boundary + "--\r\n").toByteArray(StandardCharsets.UTF_8)
        val body = ByteArray(prefix.size + fileBytes.size + purpose.size + suffix.size)
        System.arraycopy(prefix, 0, body, 0, prefix.size)
        System.arraycopy(fileBytes, 0, body, prefix.size, fileBytes.size)
        System.arraycopy(purpose, 0, body, prefix.size + fileBytes.size, purpose.size)
        System.arraycopy(suffix, 0, body, prefix.size + fileBytes.size + purpose.size, suffix.size)

        val token = jwtTokenService.issueTokenPair(owner).accessToken
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:$port/api/v1/files/images"))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build()
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray())
    }

    private fun uploadView(): FileView =
        FileView(
            "77", "image/png", 2L * 1024 * 1024, "PUBLIC", "PASSED",
            "TEMPORARY", Instant.parse("2026-07-23T08:00:00Z"), null
        )
}
