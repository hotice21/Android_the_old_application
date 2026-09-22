package com.eligo.server.security

import com.eligo.server.common.api.Result
import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.config.security.SecurityModeProperties
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.ObjectMapper
import java.io.IOException
import java.nio.charset.StandardCharsets

@Component
class AccountRestrictionFilter(
    private val restrictionReader: AccountRestrictionReader,
    private val objectMapper: ObjectMapper,
    private val securityModeProperties: SecurityModeProperties
) : OncePerRequestFilter(), AccountAccessPolicy {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !securityModeProperties.enabled

    @Throws(ServletException::class, IOException::class)
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        val authentication = SecurityContextHolder.getContext().authentication
        val principal = authentication?.principal
        if (principal == null || principal !is UserPrincipal) {
            chain.doFilter(request, response)
            return
        }
        try {
            check(principal, request)
            chain.doFilter(request, response)
        } catch (exception: BusinessException) {
            response.status = exception.errorCode.httpStatus.value()
            response.characterEncoding = StandardCharsets.UTF_8.name()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            objectMapper.writeValue(response.outputStream, Result.failure<Any>(exception.errorCode))
        }
    }

    override fun check(principal: UserPrincipal, request: HttpServletRequest) {
        val state = restrictionReader.read(principal.userId)
        if (state.accountStatus == SECURITY_DISABLED) {
            throw BusinessException(CommonErrorCode.ACCESS_DENIED)
        }
        if (state.accountStatus != ACTIVE && state.accountStatus != DEACTIVATION_PENDING) {
            throw BusinessException(AccountUserFileErrorCode.LOGIN_SESSION_INVALID)
        }
        if (state.accountStatus == DEACTIVATION_PENDING) {
            if (!allowedWhileDeactivationPending(request, principal.userId)) {
                throw BusinessException(AccountUserFileErrorCode.ACCOUNT_CANCELLATION_CONFLICT)
            }
            return
        }
        if (state.pendingRequiredAgreementIds.isNotEmpty()) {
            if (!allowedWhileAgreementRequired(request)) {
                throw BusinessException(AccountUserFileErrorCode.LATEST_AGREEMENT_REQUIRED)
            }
            return
        }
        if (!state.profileCompleted && !allowedWhileProfileIncomplete(request)) {
            throw BusinessException(AccountUserFileErrorCode.PROFILE_INCOMPLETE)
        }
    }

    private fun allowedWhileDeactivationPending(request: HttpServletRequest, userId: Long): Boolean {
        val method = request.method
        val path = request.requestURI
        return isAuthLifecyclePath(path) ||
            (path == "/api/v1/account/deactivation" &&
                (method == "GET" || method == "POST" || method == "DELETE")) ||
            path.startsWith("/api/v1/account/data-exports") ||
            canDownloadExport(method, path, userId)
    }

    private fun allowedWhileAgreementRequired(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        return isAuthLifecyclePath(path) ||
            path.startsWith("/api/v1/agreements/") ||
            path == "/api/v1/agreements" ||
            path == "/api/v1/account/deactivation"
    }

    private fun allowedWhileProfileIncomplete(request: HttpServletRequest): Boolean {
        val method = request.method
        val path = request.requestURI
        return isAuthLifecyclePath(path) ||
            ((method == "PUT" || method == "DELETE") &&
                path.matches(Regex("/api/v1/follows/(users|organizations)/\\d+"))) ||
            (method == "GET" &&
                (path == "/api/v1/activities" ||
                    path == "/api/v1/activities/map" ||
                    path.matches(Regex("/api/v1/activities/\\d+")) ||
                    path.matches(Regex("/api/v1/activities/\\d+/comments")) ||
                    path == "/api/v1/posts" ||
                    path.matches(Regex("/api/v1/posts/\\d+")) ||
                    path.matches(Regex("/api/v1/users/\\d+/posts")) ||
                    path.matches(Regex("/api/v1/organizations/\\d+/posts")) ||
                    path == "/api/v1/feed/following" ||
                    path == "/api/v1/feed/recommended" ||
                    path.matches(Regex("/api/v1/users/\\d+/(profile|follow-state)")) ||
                    path.matches(Regex("/api/v1/organizations/\\d+")) ||
                    path.matches(Regex("/api/v1/organizations/\\d+/follow-state")))) ||
            (method == "PUT" &&
                (path.matches(Regex("/api/v1/activities/\\d+/publication")) ||
                    path.matches(Regex("/api/v1/posts/\\d+/publication")) ||
                    path.matches(Regex("/api/v1/activities/\\d+/participation")) ||
                    path.matches(Regex("/api/v1/organizations/\\d+/posts/\\d+")))) ||
            (method == "DELETE" &&
                (path.matches(Regex("/api/v1/posts/\\d+")) ||
                    path.matches(Regex("/api/v1/activities/\\d+/favorite")) ||
                    path.matches(Regex("/api/v1/activities/\\d+/comments/\\d+")))) ||
            path.startsWith("/api/v1/account/") ||
            path.startsWith("/api/v1/agreements/") ||
            path == "/api/v1/agreements" ||
            path.startsWith("/api/v1/users/me/") ||
            path == "/api/v1/interest-tags" ||
            path.startsWith("/api/v1/files/")
    }

    private fun canDownloadExport(method: String, path: String, userId: Long): Boolean {
        val prefix = "/api/v1/files/"
        val suffix = "/content"
        if (method != "GET" || !path.startsWith(prefix) || !path.endsWith(suffix)) {
            return false
        }
        val rawFileId = path.substring(prefix.length, path.length - suffix.length)
        return try {
            val fileId = rawFileId.toLong()
            fileId > 0 && restrictionReader.canDownloadExport(userId, fileId)
        } catch (exception: NumberFormatException) {
            false
        }
    }

    private fun isAuthLifecyclePath(path: String): Boolean {
        return path == "/api/v1/auth/wechat-login" ||
            path == "/api/v1/auth/refresh" ||
            path == "/api/v1/auth/logout"
    }

    companion object {
        private const val ACTIVE = 1
        private const val DEACTIVATION_PENDING = 2
        private const val SECURITY_DISABLED = 4
    }
}
