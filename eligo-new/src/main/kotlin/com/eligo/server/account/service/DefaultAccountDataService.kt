package com.eligo.server.account.service

import com.eligo.server.account.dto.DeactivationRequest
import com.eligo.server.account.entity.AccountSecurityEventEntity
import com.eligo.server.account.entity.UserDataRequestEntity
import com.eligo.server.account.entity.UserDataRequestEventEntity
import com.eligo.server.account.entity.UserDeviceEntity
import com.eligo.server.account.entity.UserEntity
import com.eligo.server.account.entity.UserWechatAccountEntity
import com.eligo.server.account.mapper.AccountSecurityEventMapper
import com.eligo.server.account.mapper.UserDataRequestEventMapper
import com.eligo.server.account.mapper.UserDataRequestMapper
import com.eligo.server.account.mapper.UserDeviceMapper
import com.eligo.server.account.mapper.UserMapper
import com.eligo.server.account.mapper.UserWechatAccountMapper
import com.eligo.server.account.vo.DataExportView
import com.eligo.server.account.vo.DeactivationView
import com.eligo.server.account.vo.DownloadUrlView
import com.eligo.server.account.vo.SecurityEventView
import com.eligo.server.common.api.CursorPage
import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.common.web.RequestIdContext
import com.eligo.server.integration.wechat.WechatLoginClient
import com.eligo.server.integration.wechat.WechatProperties
import com.eligo.server.integration.wechat.WechatSession
import com.eligo.server.security.SensitiveDataCodec
import com.eligo.server.security.UserPrincipal
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.ArrayList
import java.util.Arrays
import java.util.Base64
import java.util.Optional
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test")
class DefaultAccountDataService(
    private val wechatProperties: WechatProperties,
    private val wechatLoginClient: WechatLoginClient,
    private val sensitiveDataCodec: SensitiveDataCodec,
    private val userMapper: UserMapper,
    private val wechatAccountMapper: UserWechatAccountMapper,
    private val requestMapper: UserDataRequestMapper,
    private val eventMapper: UserDataRequestEventMapper,
    private val securityEventMapper: AccountSecurityEventMapper?,
    private val deviceMapper: UserDeviceMapper?,
    blockers: List<AccountDeactivationBlocker>,
    private val clock: Clock
) : AccountDataService {

    private val blockers: List<AccountDeactivationBlocker> = blockers.toList()

    @Autowired
    constructor(
        wechatProperties: WechatProperties,
        wechatLoginClient: WechatLoginClient,
        sensitiveDataCodec: SensitiveDataCodec,
        userMapper: UserMapper,
        wechatAccountMapper: UserWechatAccountMapper,
        requestMapper: UserDataRequestMapper,
        eventMapper: UserDataRequestEventMapper,
        securityEventMapper: AccountSecurityEventMapper,
        deviceMapper: UserDeviceMapper,
        blockers: List<AccountDeactivationBlocker>
    ) : this(
        wechatProperties, wechatLoginClient, sensitiveDataCodec, userMapper,
        wechatAccountMapper, requestMapper, eventMapper, securityEventMapper, deviceMapper,
        blockers, Clock.systemUTC()
    )

    constructor(
        wechatProperties: WechatProperties,
        wechatLoginClient: WechatLoginClient,
        sensitiveDataCodec: SensitiveDataCodec,
        userMapper: UserMapper,
        wechatAccountMapper: UserWechatAccountMapper,
        requestMapper: UserDataRequestMapper,
        eventMapper: UserDataRequestEventMapper,
        blockers: List<AccountDeactivationBlocker>,
        clock: Clock
    ) : this(
        wechatProperties, wechatLoginClient, sensitiveDataCodec, userMapper, wechatAccountMapper,
        requestMapper, eventMapper, null, null, blockers, clock
    )

    @Transactional
    override fun requestDeactivation(principal: UserPrincipal, request: DeactivationRequest): DeactivationView =
        requestDeactivationOutcome(principal, request).view

    @Transactional
    override fun requestDeactivationOutcome(
        principal: UserPrincipal,
        request: DeactivationRequest
    ): DeactivationRequestOutcome {
        if (!request.confirmed) {
            throw conflict()
        }
        val session = wechatLoginClient.exchangeCode(request.wechatCode)
        val actualHash = sensitiveDataCodec.lookupHash("wechat-openid:" + session.openid)
        val user = userMapper.lockById(principal.userId).orElseThrow { conflict() }
        verifyWechatIdentity(principal.userId, actualHash)
        val existing = requestMapper.findActiveByUserIdAndType(principal.userId, UserDataRequestEntity.TYPE_DEACTIVATION)
        if (existing.isPresent) {
            return DeactivationRequestOutcome(view(existing.get()), false)
        }
        val now = now()
        if (user.status != ACTIVE || blockers.any { it.blockingReason(principal.userId, now).isPresent }) {
            throw conflict()
        }
        val dataRequest = UserDataRequestEntity()
        dataRequest.userId = principal.userId
        dataRequest.requestType = UserDataRequestEntity.TYPE_DEACTIVATION
        dataRequest.status = UserDataRequestEntity.STATUS_WAITING_EXECUTION
        dataRequest.requestedAt = now
        dataRequest.executeAfter = now.plusDays(7)
        dataRequest.retryCount = 0
        dataRequest.version = 0
        dataRequest.createdAt = now
        dataRequest.updatedAt = now
        requestMapper.insert(dataRequest)
        if (userMapper.markDeactivationPending(user.id!!, user.version!!, now) != 1) {
            throw conflict()
        }
        appendEvent(
            dataRequest.id!!, "DEACTIVATION_REQUESTED", null,
            UserDataRequestEntity.STATUS_WAITING_EXECUTION,
            UserDataRequestEventEntity.ACTOR_USER, principal.userId, RequestIdContext.current(), now
        )
        return DeactivationRequestOutcome(view(dataRequest), true)
    }

    override fun currentDeactivation(principal: UserPrincipal): Optional<DeactivationView> =
        requestMapper.findActiveByUserIdAndType(principal.userId, UserDataRequestEntity.TYPE_DEACTIVATION)
            .map { view(it) }

    @Transactional
    override fun cancelDeactivation(principal: UserPrincipal) {
        val candidate = requestMapper.lockActiveByUserIdAndType(principal.userId, UserDataRequestEntity.TYPE_DEACTIVATION)
        val user = userMapper.lockById(principal.userId).orElseThrow { conflict() }
        if (candidate.isEmpty) {
            if (user.status == ACTIVE) {
                return
            }
            throw conflict()
        }
        val current = candidate.get()
        if (user.status != DEACTIVATION_PENDING ||
            current.status != UserDataRequestEntity.STATUS_WAITING_EXECUTION ||
            requestMapper.markCancelled(current.id!!, current.version!!, now()) != 1
        ) {
            throw conflict()
        }
        val now = now()
        if (userMapper.restoreActive(user.id!!, user.version!!, now) != 1) {
            throw conflict()
        }
        appendEvent(
            current.id!!, "DEACTIVATION_CANCELLED",
            UserDataRequestEntity.STATUS_WAITING_EXECUTION,
            UserDataRequestEntity.STATUS_CANCELLED,
            UserDataRequestEventEntity.ACTOR_USER, principal.userId,
            RequestIdContext.current(), now
        )
    }

    @Transactional
    override fun requestExport(principal: UserPrincipal): DataExportView {
        if (requestMapper.findActiveByUserIdAndType(principal.userId, UserDataRequestEntity.TYPE_EXPORT).isPresent) {
            throw BusinessException(AccountUserFileErrorCode.DATA_EXPORT_IN_PROGRESS)
        }
        val now = now()
        val request = UserDataRequestEntity()
        request.userId = principal.userId
        request.requestType = UserDataRequestEntity.TYPE_EXPORT
        request.status = UserDataRequestEntity.STATUS_REQUESTED
        request.requestedAt = now
        request.executeAfter = now
        request.retryCount = 0
        request.version = 0
        request.createdAt = now
        request.updatedAt = now
        try {
            requestMapper.insert(request)
        } catch (exception: DuplicateKeyException) {
            throw BusinessException(AccountUserFileErrorCode.DATA_EXPORT_IN_PROGRESS)
        }
        appendEvent(
            request.id!!, "DATA_EXPORT_REQUESTED", null,
            UserDataRequestEntity.STATUS_REQUESTED, UserDataRequestEventEntity.ACTOR_USER,
            principal.userId, RequestIdContext.current(), now
        )
        return exportView(request)
    }

    override fun exportStatus(principal: UserPrincipal, requestId: Long): DataExportView =
        exportView(requireOwnedExport(principal.userId, requestId))

    override fun exportDownloadUrl(principal: UserPrincipal, requestId: Long): DownloadUrlView {
        val request = requireOwnedExport(principal.userId, requestId)
        val now = now()
        val expiresAt = request.resultExpiresAt
        if (request.status != UserDataRequestEntity.STATUS_COMPLETED ||
            request.resultFileId == null || expiresAt == null || !expiresAt.isAfter(now)
        ) {
            throw BusinessException(AccountUserFileErrorCode.DATA_EXPORT_EXPIRED)
        }
        return DownloadUrlView(
            "/api/v1/files/" + request.resultFileId + "/content",
            expiresAt.toInstant(ZoneOffset.UTC)
        )
    }

    override fun securityEvents(
        principal: UserPrincipal,
        cursor: String?,
        limit: Int
    ): CursorPage<SecurityEventView> {
        if (limit < 1 || limit > 100) {
            throw BusinessException(CommonErrorCode.VALIDATION_FAILED)
        }
        val decoded = decodeCursor(cursor)
        val rows = securityEventMapper!!.findPage(principal.userId, decoded.occurredAt, decoded.eventId, limit + 1)
        val hasMore = rows.size > limit
        val pageRows = rows.subList(0, minOf(rows.size, limit))
        val items = ArrayList<SecurityEventView>(pageRows.size)
        for (event in pageRows) {
            var deviceName: String? = null
            if (event.deviceId != null) {
                val device = deviceMapper!!.selectById(event.deviceId)
                if (device != null && device.userId == principal.userId) {
                    deviceName = device.deviceName
                }
            }
            items.add(
                SecurityEventView(
                    event.id.toString(),
                    event.eventType,
                    severity(event.severity),
                    deviceName,
                    event.regionCode,
                    event.occurredAt!!.toInstant(ZoneOffset.UTC)
                )
            )
        }
        val next = if (hasMore && pageRows.isNotEmpty()) encodeCursor(pageRows[pageRows.size - 1]) else null
        return CursorPage(items, next, hasMore)
    }

    private fun requireOwnedExport(userId: Long, requestId: Long): UserDataRequestEntity =
        requestMapper.findOwnedByIdAndType(requestId, userId, UserDataRequestEntity.TYPE_EXPORT)
            .orElseThrow { BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND) }

    private fun exportView(request: UserDataRequestEntity): DataExportView =
        DataExportView(
            request.id.toString(),
            status(request.status!!),
            request.requestedAt!!.toInstant(ZoneOffset.UTC),
            instant(request.processedAt),
            instant(request.resultExpiresAt),
            request.failureCode
        )

    private fun instant(value: LocalDateTime?): Instant? = value?.toInstant(ZoneOffset.UTC)

    private fun severity(value: Int?): String = when (value) {
        2 -> "MEDIUM"
        3 -> "HIGH"
        else -> "LOW"
    }

    private fun decodeCursor(cursor: String?): SecurityCursor {
        if (cursor == null || cursor.isBlank()) {
            return SecurityCursor(null, null)
        }
        return try {
            val raw = String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
            val parts = raw.split(":", limit = 2)
            if (parts.size != 2) {
                throw IllegalArgumentException()
            }
            SecurityCursor(
                LocalDateTime.ofInstant(Instant.ofEpochMilli(parts[0].toLong()), ZoneOffset.UTC),
                parts[1].toLong()
            )
        } catch (exception: IllegalArgumentException) {
            throw BusinessException(CommonErrorCode.MALFORMED_REQUEST)
        }
    }

    private fun encodeCursor(event: AccountSecurityEventEntity): String {
        val raw = event.occurredAt!!.toInstant(ZoneOffset.UTC).toEpochMilli().toString() + ":" + event.id
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
    }

    private data class SecurityCursor(val occurredAt: LocalDateTime?, val eventId: Long?)

    private fun verifyWechatIdentity(userId: Long, actualHash: ByteArray) {
        val identity = wechatAccountMapper.lockActiveByUserId(userId).orElseThrow { conflict() }
        if (wechatProperties.appId != identity.appId || !Arrays.equals(actualHash, identity.openidLookupHash)) {
            throw conflict()
        }
    }

    private fun appendEvent(
        requestId: Long,
        type: String,
        from: Int?,
        to: Int?,
        actorType: Int,
        actorId: Long?,
        traceId: String?,
        now: LocalDateTime
    ) {
        val event = UserDataRequestEventEntity()
        event.requestId = requestId
        event.eventType = type
        event.fromStatus = from
        event.toStatus = to
        event.actorType = actorType
        event.actorId = actorId
        event.resultCode = "SUCCESS"
        event.traceRequestId = traceId
        event.createdAt = now
        eventMapper.insert(event)
    }

    private fun view(request: UserDataRequestEntity): DeactivationView =
        DeactivationView(
            request.id.toString(),
            status(request.status!!),
            request.requestedAt!!.toInstant(ZoneOffset.UTC),
            request.executeAfter!!.toInstant(ZoneOffset.UTC),
            request.status == UserDataRequestEntity.STATUS_WAITING_EXECUTION
        )

    private fun status(status: Int): String = when (status) {
        UserDataRequestEntity.STATUS_REQUESTED -> "APPLIED"
        UserDataRequestEntity.STATUS_WAITING_EXECUTION -> "WAITING"
        UserDataRequestEntity.STATUS_PROCESSING -> "PROCESSING"
        UserDataRequestEntity.STATUS_COMPLETED -> "COMPLETED"
        UserDataRequestEntity.STATUS_CANCELLED -> "CANCELLED"
        UserDataRequestEntity.STATUS_FAILED -> "FAILED"
        else -> throw conflict()
    }

    private fun now(): LocalDateTime = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)

    private fun conflict(): BusinessException =
        BusinessException(AccountUserFileErrorCode.ACCOUNT_CANCELLATION_CONFLICT)

    companion object {
        private const val ACTIVE = 1
        private const val DEACTIVATION_PENDING = 2
    }
}
