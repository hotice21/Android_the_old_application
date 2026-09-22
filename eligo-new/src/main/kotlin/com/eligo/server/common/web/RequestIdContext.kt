package com.eligo.server.common.web

import org.slf4j.MDC

object RequestIdContext {

    internal const val MDC_KEY = "requestId"

    fun current(): String? = MDC.get(MDC_KEY)
}
