package com.eligo.server.account

import com.eligo.server.account.service.AccountDataJob
import com.eligo.server.database.Stage2TestDatabaseCleaner
import com.eligo.server.integration.wechat.AuthorizedPhone
import com.eligo.server.integration.wechat.WechatLoginClient
import com.eligo.server.integration.wechat.WechatPhoneClient
import com.eligo.server.integration.wechat.WechatRestClientFactory
import com.eligo.server.integration.wechat.WechatSession
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.mockito.kotlin.any
import org.mockito.Mockito.`when`
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(properties = [
        "eligo.security.jwt.signing-key-base64=IB8eHRwbGhkYFxYVFBMSERAPDg0MCwoJCAcGBQQDAgE=",
        "eligo.security.sensitive-data.encryption-key-base64=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
        "eligo.security.sensitive-data.lookup-key-base64=Hx4dHBsaGRgXFhUUExIREA8ODQwLCgkIBwYFBAMCAQA=",
        "eligo.integration.wechat.app-id=stage2-flow-app",
        "eligo.file.storage.local-root=target/stage2-account-flow-files"
])
@AutoConfigureMockMvc
@ActiveProfiles("it")
@EnabledIfSystemProperty(named = "eligo.database.tests", matches = "true")
class Stage2AccountFlowIntegrationTests {

    companion object {
        private val STORAGE_ROOT: Path = Path.of("target/stage2-account-flow-files")
    }

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var accountDataJob: AccountDataJob
    @MockitoBean private lateinit var wechatLoginClient: WechatLoginClient
    @MockitoBean private lateinit var redisTemplate: StringRedisTemplate
    @MockitoBean private lateinit var wechatPhoneClient: WechatPhoneClient
    @MockitoBean private lateinit var wechatRestClientFactory: WechatRestClientFactory

    @BeforeEach
    fun prepareIsolatedFlow() {
        cleanDatabase()
        deleteDirectoryContents(STORAGE_ROOT)
        `when`(wechatLoginClient.exchangeCode(any<String>()))
            .thenReturn(WechatSession("stage2-flow-openid", "stage2-flow-unionid", "临时会话值"))
        `when`(wechatPhoneClient.exchangePhoneCode("phone-first"))
            .thenReturn(AuthorizedPhone("86", "13800138000"))
        `when`(wechatPhoneClient.exchangePhoneCode("phone-second"))
            .thenReturn(AuthorizedPhone("86", "13900139000"))
        seedAgreement(810001, 1, "用户协议", "v1")
        seedAgreement(810002, 2, "隐私政策", "v1")
    }

    @AfterEach
    fun cleanIsolatedFlow() {
        cleanDatabase()
        deleteDirectoryContents(STORAGE_ROOT)
    }

    @Test
    fun completesTheStage2AccountUserAndFileFlow() {
        val firstLogin = login("installation-stage2-0001", "第一台设备")
        val firstToken = firstLogin.path("data").path("accessToken").asText()
        val userId = firstLogin.path("data").path("userId").asLong()
        assertThat(firstLogin.path("data").path("pendingAgreementIds").size()).isEqualTo(2)

        val agreements = json(mockMvc.perform(get("/api/v1/agreements/current")
                        .header("Authorization", bearer(firstToken)))
                .andExpect(status().isOk).andReturn())
        for (agreement in agreements.path("data").path("items")) {
            mockMvc.perform(post("/api/v1/agreements/{id}/consents",
                            agreement.path("agreementId").asText())
                            .header("Authorization", bearer(firstToken)))
                    .andExpect(status().isCreated)
                    .andExpect(jsonPath("$.data.agreementId")
                            .value(agreement.path("agreementId").asText()))
        }

        mockMvc.perform(put("/api/v1/users/me/profile")
                        .header("Authorization", bearer(firstToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"阶段二用户","birthDate":"2000-01-02",
                                 "gender":"OTHER_OR_UNDISCLOSED","provinceCode":"44",
                                 "cityCode":"4403","districtCode":"440305","bio":"端到端测试"}
                                """))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.region.districtName").value("南山区"))

        val interestId = jdbcTemplate.queryForObject(
                "SELECT id FROM interest_tags WHERE tag_code='OUTDOOR'", String::class.java)
        mockMvc.perform(put("/api/v1/users/me/interests")
                        .header("Authorization", bearer(firstToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"interestTagIds\":[\"" + interestId + "\"]}"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.interestTags[0].code").value("OUTDOOR"))

        val image = MockMultipartFile(
                "file", "avatar.png", "image/png", png())
        val uploaded = json(mockMvc.perform(multipart("/api/v1/files/images")
                        .file(image)
                        .param("purpose", "AVATAR")
                        .header("Authorization", bearer(firstToken)))
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.data.scanStatus").value("PASSED"))
                .andReturn())
        val fileId = uploaded.path("data").path("fileId").asText()
        mockMvc.perform(put("/api/v1/users/me/avatar")
                        .header("Authorization", bearer(firstToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileId\":\"" + fileId + "\"}"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.avatar.fileId").value(fileId))
                .andExpect(jsonPath("$.data.profileCompleted").value(false))

        mockMvc.perform(put("/api/v1/account/phone-binding")
                        .header("Authorization", bearer(firstToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneCode\":\"phone-first\"}"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.maskedPhone").value("138****8000"))
        mockMvc.perform(get("/api/v1/users/me/profile")
                        .header("Authorization", bearer(firstToken)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.profileCompleted").value(true))
        mockMvc.perform(put("/api/v1/account/phone-binding")
                        .header("Authorization", bearer(firstToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneCode\":\"phone-second\"}"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.maskedPhone").value("139****9000"))
        mockMvc.perform(delete("/api/v1/account/phone-binding")
                        .header("Authorization", bearer(firstToken)))
                .andExpect(status().isOk)

        login("installation-stage2-0002", "第二台设备")
        login("installation-stage2-0003", "第三台设备")
        val fourthLogin = login("installation-stage2-0004", "第四台设备")
        val activeToken = fourthLogin.path("data").path("accessToken").asText()
        mockMvc.perform(get("/api/v1/account/sessions")
                        .header("Authorization", bearer(activeToken)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.items.length()").value(3))
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_devices WHERE user_id=? AND status=3",
                Int::class.java, userId)).isEqualTo(1)

        mockMvc.perform(post("/api/v1/account/deactivation")
                        .header("Authorization", bearer(activeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"wechatCode\":\"deactivation-code\",\"confirmed\":true}"))
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.data.status").value("WAITING"))
                .andExpect(jsonPath("$.data.canCancel").value(true))
        mockMvc.perform(get("/api/v1/account/deactivation")
                        .header("Authorization", bearer(activeToken)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.status").value("WAITING"))

        val export = json(mockMvc.perform(post("/api/v1/account/data-exports")
                        .header("Authorization", bearer(activeToken)))
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.data.status").value("APPLIED"))
                .andReturn())
        val requestId = export.path("data").path("requestId").asText()
        accountDataJob.executePendingExports()
        mockMvc.perform(get("/api/v1/account/data-exports/{id}", requestId)
                        .header("Authorization", bearer(activeToken)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
        val download = json(mockMvc.perform(
                        get("/api/v1/account/data-exports/{id}/download-url", requestId)
                                .header("Authorization", bearer(activeToken)))
                .andExpect(status().isOk).andReturn())
        val downloadUrl = download.path("data").path("downloadUrl").asText()
        val archive = mockMvc.perform(get(downloadUrl)
                        .header("Authorization", bearer(activeToken)))
                .andExpect(status().isOk)
                .andExpect { result ->
                    assertThat(result.response.contentType)
                        .isEqualTo("application/zip")
                }
                .andReturn()
        assertThat(archive.response.contentAsByteArray).isNotEmpty()

        mockMvc.perform(delete("/api/v1/account/deactivation")
                        .header("Authorization", bearer(activeToken)))
                .andExpect(status().isOk)
        mockMvc.perform(get("/api/v1/account/deactivation")
                        .header("Authorization", bearer(activeToken)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data").doesNotExist())
    }

    private fun login(installationId: String, deviceName: String): JsonNode {
        return json(mockMvc.perform(post("/api/v1/auth/wechat-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"wechatCode":"flow-code","installationId":"$installationId",
                                 "deviceName":"$deviceName","platform":"WECHAT_MINIPROGRAM",
                                 "osVersion":"18.0","appVersion":"2.3.0","regionCode":"4403"}
                                """))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.code").value(0))
                .andReturn())
    }

    private fun json(result: MvcResult): JsonNode {
        return objectMapper.readTree(result.response.contentAsByteArray)
    }

    private fun bearer(token: String): String {
        return "Bearer $token"
    }

    private fun png(): ByteArray {
        val image = BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB)
        val output = ByteArrayOutputStream()
        ImageIO.write(image, "png", output)
        return output.toByteArray()
    }

    private fun seedAgreement(id: Long, type: Int, title: String, version: String) {
        jdbcTemplate.update("""
                INSERT INTO agreements (
                    id,agreement_type,version_code,title,content,content_hash,status,
                    requires_reconsent,effective_at,version,created_at,updated_at
                ) VALUES (?,?,?,?,'测试正文',UNHEX(SHA2(?,256)),3,1,
                    UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))
                """, id, type, version, title, title + version)
    }

    private fun cleanDatabase() {
        Stage2TestDatabaseCleaner.cleanAllStage2Tables(jdbcTemplate)
    }

    private fun deleteDirectoryContents(root: Path) {
        if (!Files.exists(root)) {
            return
        }
        Files.walk(root).use { paths ->
            paths.sorted { left, right -> right.compareTo(left) }
                    .filter { path -> !path.equals(root) }
                    .forEach { path ->
                        try {
                            Files.deleteIfExists(path)
                        } catch (exception: Exception) {
                            throw IllegalStateException("测试文件清理失败", exception)
                        }
                    }
        }
    }
}
