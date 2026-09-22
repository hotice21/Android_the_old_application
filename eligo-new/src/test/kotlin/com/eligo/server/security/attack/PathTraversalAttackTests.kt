package com.eligo.server.security.attack

import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.common.web.GlobalExceptionHandler
import com.eligo.server.config.security.SecurityConfig
import com.eligo.server.file.controller.FileController
import com.eligo.server.file.service.FileService
import com.eligo.server.security.AccountRestrictionFilter
import com.eligo.server.security.AccountRestrictionReader
import com.eligo.server.security.SessionAccessReader
import com.eligo.server.security.SessionAuthenticationFilter
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 漏洞测试：路径穿越 / 非法文件标识。
 *
 * 文件 ID 路径变量必须为正整数：
 * - 含 `..`、编码斜杠或非数字内容 → 400 参数类型错误；
 * - 负数 ID 访问 → 服务层拒绝，403。
 */
@WebMvcTest(FileController::class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(
    GlobalExceptionHandler::class,
    SecurityConfig::class,
    SessionAuthenticationFilter::class,
    AccountRestrictionFilter::class
)
class PathTraversalAttackTests {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var fileService: FileService

    @MockitoBean
    private lateinit var sessionAccessReader: SessionAccessReader

    @MockitoBean
    private lateinit var accountRestrictionReader: AccountRestrictionReader

    @Test
    fun encodedPathTraversalInFileIdReturnsBadRequest() {
        // 编码斜杠在进入控制器前即被路径解析拒绝（400，无响应体）
        mockMvc.perform(get("/api/v1/files/..%2F..%2Fetc%2Fpasswd/content"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun nonNumericFileIdReturnsBadRequest() {
        mockMvc.perform(get("/api/v1/files/abc/content"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value(10005))
    }

    @Test
    fun zeroFileIdRejectedAtServiceLayer() {
        // 文件内容端点 permitAll，匿名时 principal 为 null，必须用 anyOrNull()
        whenever(fileService.openContent(anyOrNull(), any()))
            .thenThrow(BusinessException(CommonErrorCode.ACCESS_DENIED))

        mockMvc.perform(get("/api/v1/files/0/content"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value(10101))
    }

    @Test
    fun negativeFileIdRejectedAtServiceLayer() {
        whenever(fileService.openContent(anyOrNull(), any()))
            .thenThrow(BusinessException(CommonErrorCode.ACCESS_DENIED))

        mockMvc.perform(get("/api/v1/files/-1/content"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value(10101))
    }
}
