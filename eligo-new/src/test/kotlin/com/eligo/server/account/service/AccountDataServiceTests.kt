package com.eligo.server.account.service

import com.eligo.server.account.dto.DeactivationRequest
import com.eligo.server.account.entity.AccountSecurityEventEntity
import com.eligo.server.account.entity.UserDataRequestEntity
import com.eligo.server.account.entity.UserDataRequestEventEntity
import com.eligo.server.account.entity.UserEntity
import com.eligo.server.account.entity.UserWechatAccountEntity
import com.eligo.server.account.mapper.AccountSecurityEventMapper
import com.eligo.server.account.mapper.UserDataRequestEventMapper
import com.eligo.server.account.mapper.UserDeviceMapper
import com.eligo.server.account.mapper.UserDataRequestMapper
import com.eligo.server.account.mapper.UserMapper
import com.eligo.server.account.mapper.UserWechatAccountMapper
import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
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
import java.util.Optional
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.springframework.dao.DuplicateKeyException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.mockito.kotlin.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class AccountDataServiceTests {
    companion object {
        private val CLOCK: Clock =
                Clock.fixed(Instant.parse("2026-07-23T08:00:00Z"), ZoneOffset.UTC)
        private val PRINCIPAL: UserPrincipal = UserPrincipal(202L, "session-key")
        private val OPENID_HASH: ByteArray = "openid-hash".toByteArray(StandardCharsets.UTF_8)
    }

    private val wechat = mock(WechatLoginClient::class.java)
    private val codec = mock(SensitiveDataCodec::class.java)
    private val users = mock(UserMapper::class.java)
    private val identities = mock(UserWechatAccountMapper::class.java)
    private val requests = mock(UserDataRequestMapper::class.java)
    private val events = mock(UserDataRequestEventMapper::class.java)
    private lateinit var service: DefaultAccountDataService

    @BeforeEach
    fun setUp() {
        service = DefaultAccountDataService(
                WechatProperties("wx-app", "secret", "https://example.invalid"),
                wechat, codec, users, identities, requests, events, listOf(), CLOCK)
        `when`(wechat.exchangeCode("fresh-code")).thenReturn(WechatSession("openid", null, "key"))
        `when`(codec.lookupHash("wechat-openid:openid")).thenReturn(OPENID_HASH)
        `when`(identities.lockActiveByUserId(202L)).thenReturn(Optional.of(identity()))
        `when`(users.lockById(202L)).thenReturn(Optional.of(user(1)))
        `when`(users.markDeactivationPending(202L, 0, LocalDateTime.parse("2026-07-23T08:00:00")))
            .thenReturn(1)
        `when`(requests.markCancelled(501L, 0, LocalDateTime.parse("2026-07-23T08:00:00")))
            .thenReturn(1)
        `when`(users.restoreActive(202L, 0, LocalDateTime.parse("2026-07-23T08:00:00")))
            .thenReturn(1)
    }

    @Test
    fun requestRequiresExplicitConfirmationBeforeWechatCall() {
        assertCancellationConflict { service.requestDeactivation(
                PRINCIPAL, DeactivationRequest("fresh-code", false)) }
        verify(wechat, never()).exchangeCode(any())
    }

    @Test
    fun requestRevalidatesWechatIdentityAndCreatesSevenDayCoolingPeriod() {
        `when`(requests.findActiveByUserIdAndType(202L, UserDataRequestEntity.TYPE_DEACTIVATION))
            .thenReturn(Optional.empty())
        `when`(requests.insert(any<UserDataRequestEntity>())).thenAnswer { invocation ->
            invocation.getArgument(0, UserDataRequestEntity::class.java).id = 501L
            1
        }

        val outcome = service.requestDeactivationOutcome(
                PRINCIPAL, DeactivationRequest("fresh-code", true))
        val result = outcome.view

        assertThat(outcome.created).isTrue()
        assertThat(result.requestId).isEqualTo("501")
        assertThat(result.status).isEqualTo("WAITING")
        assertThat(result.requestedAt).isEqualTo(Instant.parse("2026-07-23T08:00:00Z"))
        assertThat(result.executeAfter).isEqualTo(Instant.parse("2026-07-30T08:00:00Z"))
        assertThat(result.canCancel).isTrue()
        val saved = ArgumentCaptor.forClass(UserDataRequestEntity::class.java)
        verify(requests).insert(saved.capture())
        assertThat(saved.value.executeAfter)
            .isEqualTo(LocalDateTime.parse("2026-07-30T08:00:00"))
        verify(users).markDeactivationPending(202L, 0, LocalDateTime.parse("2026-07-23T08:00:00"))
        verify(events).insert(any<UserDataRequestEventEntity>())
        val lockOrder = org.mockito.Mockito.inOrder(users, identities)
        lockOrder.verify(users).lockById(202L)
        lockOrder.verify(identities).lockActiveByUserId(202L)
    }

    @Test
    fun repeatedRequestReturnsSameActiveRequestWithoutSecondInsert() {
        val existing = request(501L, UserDataRequestEntity.STATUS_WAITING_EXECUTION)
        `when`(requests.findActiveByUserIdAndType(202L, UserDataRequestEntity.TYPE_DEACTIVATION))
            .thenReturn(Optional.of(existing))

        val outcome = service.requestDeactivationOutcome(
                PRINCIPAL, DeactivationRequest("fresh-code", true))

        assertThat(outcome.created).isFalse()
        assertThat(outcome.view.requestId).isEqualTo("501")
        verify(requests, never()).insert(any<UserDataRequestEntity>())
        verify(events, never()).insert(any<UserDataRequestEventEntity>())
    }

    @Test
    fun mismatchedWechatIdentityIsRejected() {
        val other = identity()
        other.openidLookupHash = "other".toByteArray(StandardCharsets.UTF_8)
        `when`(identities.lockActiveByUserId(202L)).thenReturn(Optional.of(other))

        assertCancellationConflict { service.requestDeactivation(
                PRINCIPAL, DeactivationRequest("fresh-code", true)) }
        verify(requests, never()).insert(any<UserDataRequestEntity>())
    }

    @Test
    fun blockerPreventsRequestWithoutChangingAccount() {
        val blocker = mock(AccountDeactivationBlocker::class.java)
        val now = LocalDateTime.parse("2026-07-23T08:00:00")
        `when`(blocker.blockingReason(202L, now))
            .thenReturn(Optional.of("存在未完成订单"))
        service = DefaultAccountDataService(
                WechatProperties("wx-app", "secret", "https://example.invalid"),
                wechat, codec, users, identities, requests, events,
                listOf(blocker), CLOCK)

        assertCancellationConflict { service.requestDeactivation(
                PRINCIPAL, DeactivationRequest("fresh-code", true)) }
        verify(blocker).blockingReason(202L, now)
        verify(users, never()).markDeactivationPending(any<Long>(), any<Int>(), any())
    }

    @Test
    fun explicitCancellationRestoresAccountAndAppendsEvent() {
        `when`(users.lockById(202L)).thenReturn(Optional.of(user(2)))
        val current = request(501L, UserDataRequestEntity.STATUS_WAITING_EXECUTION)
        `when`(requests.lockActiveByUserIdAndType(202L, UserDataRequestEntity.TYPE_DEACTIVATION))
            .thenReturn(Optional.of(current))

        service.cancelDeactivation(PRINCIPAL)

        val lockOrder = org.mockito.Mockito.inOrder(requests, users)
        lockOrder.verify(requests).lockActiveByUserIdAndType(
                202L, UserDataRequestEntity.TYPE_DEACTIVATION)
        lockOrder.verify(users).lockById(202L)
        verify(requests).markCancelled(501L, 0, LocalDateTime.parse("2026-07-23T08:00:00"))
        verify(users).restoreActive(202L, 0, LocalDateTime.parse("2026-07-23T08:00:00"))
        verify(events).insert(any<UserDataRequestEventEntity>())
    }

    @Test
    fun repeatedCancellationIsIdempotentWhenAccountAlreadyActive() {
        `when`(requests.lockActiveByUserIdAndType(202L, UserDataRequestEntity.TYPE_DEACTIVATION))
            .thenReturn(Optional.empty())

        service.cancelDeactivation(PRINCIPAL)

        verify(users, never()).restoreActive(any<Long>(), any<Int>(), any())
        verify(events, never()).insert(any<UserDataRequestEventEntity>())
    }

    private fun user(status: Int): UserEntity {
        val user = UserEntity()
        user.id = 202L
        user.status = status
        user.version = 0
        return user
    }

    private fun identity(): UserWechatAccountEntity {
        val identity = UserWechatAccountEntity()
        identity.id = 301L
        identity.userId = 202L
        identity.appId = "wx-app"
        identity.openidLookupHash = OPENID_HASH
        identity.status = 1
        return identity
    }

    private fun request(id: Long, status: Int): UserDataRequestEntity {
        val request = UserDataRequestEntity()
        request.id = id
        request.userId = 202L
        request.requestType = UserDataRequestEntity.TYPE_DEACTIVATION
        request.status = status
        request.requestedAt = LocalDateTime.parse("2026-07-23T08:00:00")
        request.executeAfter = LocalDateTime.parse("2026-07-30T08:00:00")
        request.version = 0
        return request
    }

    @Test
    fun exportRequestCreatesSingleImmediateJob() {
        `when`(requests.findActiveByUserIdAndType(202L, UserDataRequestEntity.TYPE_EXPORT))
            .thenReturn(Optional.empty())
        `when`(requests.insert(any<UserDataRequestEntity>())).thenAnswer { invocation ->
            invocation.getArgument(0, UserDataRequestEntity::class.java).id = 701L
            1
        }

        val result = service.requestExport(PRINCIPAL)

        assertThat(result.requestId).isEqualTo("701")
        assertThat(result.status).isEqualTo("APPLIED")
        val saved = ArgumentCaptor.forClass(UserDataRequestEntity::class.java)
        verify(requests).insert(saved.capture())
        assertThat(saved.value.executeAfter).isEqualTo(LocalDateTime.parse("2026-07-23T08:00:00"))
        verify(events).insert(any<UserDataRequestEventEntity>())
    }

    @Test
    fun secondUnfinishedExportIsRejected() {
        val existing = request(701L, UserDataRequestEntity.STATUS_PROCESSING)
        existing.requestType = UserDataRequestEntity.TYPE_EXPORT
        `when`(requests.findActiveByUserIdAndType(202L, UserDataRequestEntity.TYPE_EXPORT))
            .thenReturn(Optional.of(existing))

        assertThatThrownBy { service.requestExport(PRINCIPAL) }
            .isInstanceOfSatisfying(BusinessException::class.java) { exception ->
                assertThat(exception.errorCode).isEqualTo(AccountUserFileErrorCode.DATA_EXPORT_IN_PROGRESS)
            }
        verify(requests, never()).insert(any<UserDataRequestEntity>())
    }

    @Test
    fun concurrentExportUniqueConflictReturnsStableBusinessError() {
        `when`(requests.findActiveByUserIdAndType(202L, UserDataRequestEntity.TYPE_EXPORT))
            .thenReturn(Optional.empty())
        `when`(requests.insert(any<UserDataRequestEntity>()))
            .thenThrow(DuplicateKeyException("并发唯一约束"))
        assertThatThrownBy { service.requestExport(PRINCIPAL) }
            .isInstanceOfSatisfying(BusinessException::class.java) { exception ->
                assertThat(exception.errorCode).isEqualTo(AccountUserFileErrorCode.DATA_EXPORT_IN_PROGRESS)
            }
    }

    @Test
    fun exportStatusHidesAnotherUsersRequestAsNotFound() {
        `when`(requests.findOwnedByIdAndType(701L, 202L, UserDataRequestEntity.TYPE_EXPORT))
            .thenReturn(Optional.empty())
        assertThatThrownBy { service.exportStatus(PRINCIPAL, 701L) }
            .isInstanceOfSatisfying(BusinessException::class.java) { exception ->
                assertThat(exception.errorCode).isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND)
            }
    }

    @Test
    fun downloadUrlRejectsExpiredCompletedResult() {
        val export = request(701L, UserDataRequestEntity.STATUS_COMPLETED)
        export.requestType = UserDataRequestEntity.TYPE_EXPORT
        export.resultFileId = 801L
        export.resultExpiresAt = LocalDateTime.parse("2026-07-23T07:59:59")
        `when`(requests.findOwnedByIdAndType(701L, 202L, UserDataRequestEntity.TYPE_EXPORT))
            .thenReturn(Optional.of(export))
        assertThatThrownBy { service.exportDownloadUrl(PRINCIPAL, 701L) }
            .isInstanceOfSatisfying(BusinessException::class.java) { exception ->
                assertThat(exception.errorCode).isEqualTo(AccountUserFileErrorCode.DATA_EXPORT_EXPIRED)
            }
    }

    @Test
    fun securityCursorPreservesOccurredAtAndEventIdBoundary() {
        val security = mock(AccountSecurityEventMapper::class.java)
        val devices = mock(UserDeviceMapper::class.java)
        service = DefaultAccountDataService(WechatProperties("wx-app", "secret", "https://example.invalid"),
                wechat, codec, users, identities, requests, events, security, devices, listOf(), CLOCK)
        val event = AccountSecurityEventEntity()
        event.id = 901L
        event.userId = 202L
        event.eventType = "NEW_INSTALLATION_LOGIN"
        event.severity = 1
        event.occurredAt = LocalDateTime.parse("2026-07-23T07:00:00")
        `when`(security.findPage(202L, null, null, 2)).thenReturn(listOf(event, AccountSecurityEventEntity()))
        val first = service.securityEvents(PRINCIPAL, null, 1)
        `when`(security.findPage(202L, LocalDateTime.parse("2026-07-23T07:00:00"), 901L, 2))
            .thenReturn(listOf())

        service.securityEvents(PRINCIPAL, first.nextCursor, 1)

        verify(security).findPage(202L, LocalDateTime.parse("2026-07-23T07:00:00"), 901L, 2)
    }

    private fun assertCancellationConflict(operation: () -> Unit) {
        assertThatThrownBy(operation)
            .isInstanceOfSatisfying(BusinessException::class.java) { exception ->
                assertThat(exception.errorCode)
                    .isEqualTo(AccountUserFileErrorCode.ACCOUNT_CANCELLATION_CONFLICT)
            }
    }
}
