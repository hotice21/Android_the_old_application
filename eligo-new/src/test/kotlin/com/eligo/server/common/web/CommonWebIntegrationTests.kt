package com.eligo.server.common.web

import com.eligo.server.common.api.Result
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(CommonWebIntegrationTests.TestController::class)
class CommonWebIntegrationTests {

    private companion object {
        const val REQUEST_ID = "test-request-123"
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun pingReturnsUnifiedResultAndRequestId() {
        mockMvc.perform(
            get("/api/v1/system/ping")
                .header(RequestTraceFilter.REQUEST_ID_HEADER, REQUEST_ID)
        )
            .andExpect(status().isOk)
            .andExpect(header().string(RequestTraceFilter.REQUEST_ID_HEADER, REQUEST_ID))
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.message").value("成功"))
            .andExpect(jsonPath("$.data.status").value("UP"))
            .andExpect(jsonPath("$.requestId").value(REQUEST_ID))
            .andExpect(jsonPath("$.timestamp").exists())
    }

    @Test
    fun invalidRequestIdIsReplaced() {
        val result = mockMvc.perform(
            get("/api/v1/system/ping")
                .header(RequestTraceFilter.REQUEST_ID_HEADER, "invalid request id")
        )
            .andExpect(status().isOk)
            .andReturn()

        val generatedRequestId = result.response.getHeader(RequestTraceFilter.REQUEST_ID_HEADER)
        assertThat(generatedRequestId).matches("[0-9a-f]{32}")
        assertThat(result.response.contentAsString)
            .contains("\"requestId\":\"$generatedRequestId\"")
    }

    @Test
    fun protectedEndpointRequiresAuthentication() {
        mockMvc.perform(
            get("/test-support/business-error")
                .header(RequestTraceFilter.REQUEST_ID_HEADER, REQUEST_ID)
        )
            .andExpect(status().isUnauthorized)
            .andExpect(header().string(RequestTraceFilter.REQUEST_ID_HEADER, REQUEST_ID))
            .andExpect(jsonPath("$.code").value(10100))
            .andExpect(jsonPath("$.message").value("请先登录"))
            .andExpect(jsonPath("$.requestId").value(REQUEST_ID))
    }

    @Test
    fun adminEndpointRejectsOrdinaryUser() {
        mockMvc.perform(
            get("/api/v1/admin/not-implemented").with(user("test-user").roles("USER"))
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value(10101))
            .andExpect(jsonPath("$.message").value("无权执行此操作"))
    }

    @Test
    fun validationErrorReturnsFieldDetails() {
        mockMvc.perform(
            post("/test-support/validate")
                .with(user("test-user"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value(10001))
            .andExpect(jsonPath("$.message").value("参数校验失败"))
            .andExpect(jsonPath("$.data[0].field").value("name"))
            .andExpect(jsonPath("$.data[0].message").value("名称不能为空"))
    }

    @Test
    fun methodParameterValidationReturnsFieldDetails() {
        mockMvc.perform(
            get("/test-support/validate-query")
                .with(user("test-user"))
                .param("keyword", "")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value(10001))
            .andExpect(jsonPath("$.data[0].field").value("keyword"))
            .andExpect(jsonPath("$.data[0].message").value("关键词不能为空"))
    }

    @Test
    fun malformedJsonReturnsStableError() {
        mockMvc.perform(
            post("/test-support/validate")
                .with(user("test-user"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value(10000))
            .andExpect(jsonPath("$.message").value("请求格式错误"))
    }

    @Test
    fun unsupportedMethodReturnsStableError() {
        mockMvc.perform(
            post("/api/v1/system/ping").with(user("test-user"))
        )
            .andExpect(status().isMethodNotAllowed)
            .andExpect(jsonPath("$.code").value(10002))
            .andExpect(jsonPath("$.message").value("请求方法不支持"))
    }

    @Test
    fun missingResourceReturnsStableError() {
        mockMvc.perform(
            get("/api/v1/not-found").with(user("test-user"))
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value(10006))
            .andExpect(jsonPath("$.message").value("请求资源不存在"))
    }

    @Test
    fun businessExceptionUsesDeclaredStatusAndMessage() {
        mockMvc.perform(
            get("/test-support/business-error").with(user("test-user"))
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value(10007))
            .andExpect(jsonPath("$.message").value("测试资源状态冲突"))
    }

    @Test
    fun unexpectedExceptionDoesNotLeakImplementationDetails() {
        val result = mockMvc.perform(
            get("/test-support/unexpected-error").with(user("test-user"))
        )
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.code").value(10999))
            .andExpect(jsonPath("$.message").value("服务暂时不可用"))
            .andReturn()

        assertThat(result.response.contentAsString).doesNotContain("IllegalStateException")
    }

    @RestController
    @RequestMapping("/test-support")
    class TestController {

        @PostMapping("/validate")
        fun validate(@Valid @RequestBody request: TestRequest): Result<Void> {
            return Result.success()
        }

        @GetMapping("/validate-query")
        fun validateQuery(
            @RequestParam @NotBlank(message = "关键词不能为空") keyword: String
        ): Result<Void> {
            return Result.success()
        }

        @GetMapping("/business-error")
        fun businessError(): Result<Void> {
            throw BusinessException(CommonErrorCode.CONFLICT, "测试资源状态冲突")
        }

        @GetMapping("/unexpected-error")
        fun unexpectedError(): Result<Void> {
            throw IllegalStateException("不应返回给客户端的内部异常")
        }
    }

    data class TestRequest(
        @field:NotBlank(message = "名称不能为空") val name: String?
    )
}
