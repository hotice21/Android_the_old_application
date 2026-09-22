package com.eligo.server.account.service

import com.eligo.server.account.dto.DeactivationRequest
import com.eligo.server.account.vo.DataExportView
import com.eligo.server.account.vo.DeactivationView
import com.eligo.server.account.vo.DownloadUrlView
import com.eligo.server.account.vo.SecurityEventView
import com.eligo.server.common.api.CursorPage
import com.eligo.server.security.UserPrincipal
import java.util.Optional

interface AccountDataService {
    fun requestDeactivation(principal: UserPrincipal, request: DeactivationRequest): DeactivationView

    fun requestDeactivationOutcome(
        principal: UserPrincipal,
        request: DeactivationRequest
    ): DeactivationRequestOutcome

    fun currentDeactivation(principal: UserPrincipal): Optional<DeactivationView>

    fun cancelDeactivation(principal: UserPrincipal)

    fun requestExport(principal: UserPrincipal): DataExportView

    fun exportStatus(principal: UserPrincipal, requestId: Long): DataExportView

    fun exportDownloadUrl(principal: UserPrincipal, requestId: Long): DownloadUrlView

    fun securityEvents(principal: UserPrincipal, cursor: String?, limit: Int): CursorPage<SecurityEventView>
}
