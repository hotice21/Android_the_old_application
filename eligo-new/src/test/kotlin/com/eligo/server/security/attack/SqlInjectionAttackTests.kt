package com.eligo.server.security.attack

import com.eligo.server.activity.controller.ActivityController
import com.eligo.server.activity.mapper.ActivityReadMapper
import com.eligo.server.activity.service.ActivityCommandService
import com.eligo.server.activity.service.ActivityReadService
import com.eligo.server.activity.vo.PublicActivitySummaryView
import com.eligo.server.common.api.CursorPage
import com.eligo.server.common.web.GlobalExceptionHandler
import com.eligo.server.config.security.SecurityConfig
import com.eligo.server.security.AccountRestrictionFilter
import com.eligo.server.security.AccountRestrictionReader
import com.eligo.server.security.SessionAccessReader
import com.eligo.server.security.SessionAuthenticationFilter
import org.apache.ibatis.annotations.Select
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 漏洞测试：SQL 注入。
 *
 * 1. 控制器层：注入 payload 必须原样作为参数传递给服务层（不报错、不被拼接到 SQL 结构中）。
 * 2. Mapper 层：公开分页查询必须使用 `#{keyword}` 预编译参数，
 *    且 [ActivityReadMapper] 全部 SQL 不允许出现 `${...}` 字符串替换。
 */
@WebMvcTest(ActivityController::class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(
    GlobalExceptionHandler::class,
    SecurityConfig::class,
    SessionAuthenticationFilter::class,
    AccountRestrictionFilter::class
)
class SqlInjectionAttackTests {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var readService: ActivityReadService

    @MockitoBean
    private lateinit var commandService: ActivityCommandService

    @MockitoBean
    private lateinit var sessionAccessReader: SessionAccessReader

    @MockitoBean
    private lateinit var accountRestrictionReader: AccountRestrictionReader

    private val injectionPayloads = listOf(
        "' OR '1'='1",
        "%'); DROP TABLE activity;--",
        "1 UNION SELECT * FROM user--",
        "\" OR \"\"=\"",
        "'; EXEC xp_cmdshell('dir');--"
    )

    @Test
    fun injectionPayloadsArePassedRawAndDoNotBreakTheEndpoint() {
        // 控制器对未提供的参数传 null，因此可空位置必须使用 anyOrNull()（any(Class) 不匹配 null）
        org.mockito.kotlin.whenever(
            readService.listPublicActivities(
                anyOrNull(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()
            )
        ).thenReturn(CursorPage<PublicActivitySummaryView>(emptyList(), null, false))

        val captor = argumentCaptor<String>()

        injectionPayloads.forEach { payload ->
            mockMvc.perform(get("/api/v1/activities").param("keyword", payload))
                .andExpect(status().isOk)
        }

        verify(
            readService,
            org.mockito.Mockito.times(injectionPayloads.size)
        ).listPublicActivities(
            anyOrNull(), any(), anyOrNull(), anyOrNull(), anyOrNull(), captor.capture()
        )

        assertEquals(injectionPayloads, captor.allValues)
    }

    @Test
    fun activityReadMapperNeverUsesRawStringSubstitution() {
        ActivityReadMapper::class.java.declaredMethods.forEach { method ->
            val select = method.getAnnotation(Select::class.java) ?: return@forEach
            val sql = select.value.joinToString("\n")
            assertEquals(
                false,
                sql.contains("\${"),
                "Mapper 方法 ${method.name} 使用了 \${} 直接拼接，存在 SQL 注入风险：\n$sql"
            )
        }
    }
}
