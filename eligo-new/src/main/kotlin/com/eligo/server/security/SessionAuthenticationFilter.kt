package com.eligo.server.security

import com.eligo.server.common.api.Result
import com.eligo.server.common.error.BusinessException
import com.eligo.server.config.security.SecurityModeProperties
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.ObjectMapper
import java.io.IOException
import java.nio.charset.StandardCharsets

@Component
class SessionAuthenticationFilter(
    private val sessionAccessReader: SessionAccessReader,
    private val objectMapper: ObjectMapper,
    private val bearerTokenResolver: BearerTokenResolver,
    private val securityModeProperties: SecurityModeProperties
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !securityModeProperties.enabled

    @Throws(ServletException::class, IOException::class)
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        val authentication = SecurityContextHolder.getContext().authentication
        if (hasBearerToken(request) && authentication != null) {
            val principal = authentication.principal
            if (principal is UserPrincipal) {
                try {
                    sessionAccessReader.requireActive(principal.sessionKey, principal.userId)
                } catch (exception: BusinessException) {
                    SecurityContextHolder.clearContext()
                    response.status = exception.errorCode.httpStatus.value()
                    response.characterEncoding = StandardCharsets.UTF_8.name()
                    response.contentType = MediaType.APPLICATION_JSON_VALUE
                    objectMapper.writeValue(response.outputStream, Result.failure<Any>(exception.errorCode))
                    return
                }
            }
        }
        chain.doFilter(request, response)
    }

    private fun hasBearerToken(request: HttpServletRequest): Boolean =
        try {
            bearerTokenResolver.resolve(request) != null
        } catch (exception: OAuth2AuthenticationException) {
            // 畸形 Authorization 头（如 "Bearer "）不在此处理；
            // 交给链上的 BearerTokenAuthenticationFilter 统一返回 401，避免 500
            false
        }
}
