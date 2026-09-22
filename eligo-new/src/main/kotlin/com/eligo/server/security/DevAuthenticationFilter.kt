package com.eligo.server.security

import com.eligo.server.config.security.SecurityModeProperties
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import java.io.IOException

/**
 * 测试模式专用过滤器：当安全总开关关闭（[SecurityModeProperties.enabled] = false）时，
 * 为所有请求注入一个固定的测试用户身份，使需要 `@AuthenticationPrincipal` 的
 * 控制器无需登录即可直接调用。
 *
 * 该过滤器仅在测试模式下被注册进过滤链；生产环境不会注册。
 */
class DevAuthenticationFilter(
    private val properties: SecurityModeProperties
) : OncePerRequestFilter() {

    @Throws(ServletException::class, IOException::class)
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        if (SecurityContextHolder.getContext().authentication == null) {
            val principal = UserPrincipal(properties.devUserId, properties.devSessionKey)
            val authorities = buildList {
                add(SimpleGrantedAuthority(ROLE_USER))
                if (properties.devAdmin) {
                    add(SimpleGrantedAuthority(ROLE_ADMIN))
                }
            }
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(principal, "dev", authorities)
        }
        chain.doFilter(request, response)
    }

    companion object {
        private const val ROLE_USER = "ROLE_USER"
        private const val ROLE_ADMIN = "ROLE_ADMIN"
    }
}
