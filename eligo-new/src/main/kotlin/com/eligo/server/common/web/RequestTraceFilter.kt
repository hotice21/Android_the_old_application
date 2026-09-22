package com.eligo.server.common.web

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestTraceFilter : OncePerRequestFilter() {

    @Throws(ServletException::class, IOException::class)
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER))
        val previousRequestId = MDC.get(RequestIdContext.MDC_KEY)
        val startNanos = System.nanoTime()

        MDC.put(RequestIdContext.MDC_KEY, requestId)
        response.setHeader(REQUEST_ID_HEADER, requestId)

        try {
            filterChain.doFilter(request, response)
        } finally {
            val durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
            log.info(
                "请求完成 method={} path={} status={} durationMs={}",
                request.method,
                request.requestURI,
                response.status,
                durationMillis
            )
            restorePreviousRequestId(previousRequestId)
        }
    }

    private fun resolveRequestId(candidate: String?): String {
        if (candidate != null && VALID_REQUEST_ID.matches(candidate)) {
            return candidate
        }
        return UUID.randomUUID().toString().replace("-", "")
    }

    private fun restorePreviousRequestId(previousRequestId: String?) {
        if (previousRequestId == null) {
            MDC.remove(RequestIdContext.MDC_KEY)
            return
        }
        MDC.put(RequestIdContext.MDC_KEY, previousRequestId)
    }

    companion object {
        const val REQUEST_ID_HEADER = "X-Request-Id"

        private val log = LoggerFactory.getLogger(RequestTraceFilter::class.java)
        private val VALID_REQUEST_ID = Regex("[A-Za-z0-9._:-]{1,64}")
    }
}
