package com.eligo.server.account.service

import com.eligo.server.account.entity.UserDataRequestEntity
import com.eligo.server.account.entity.UserDataRequestEventEntity
import com.eligo.server.account.mapper.UserDataRequestEventMapper
import com.eligo.server.account.mapper.UserDataRequestMapper
import com.eligo.server.account.mapper.UserLoginSessionMapper
import com.eligo.server.account.mapper.UserMapper
import com.eligo.server.account.mapper.UserPhoneBindingMapper
import com.eligo.server.account.mapper.UserWechatAccountMapper
import com.eligo.server.favorite.service.ActivityEngagementAccountLifecycleService
import com.eligo.server.post.service.PostAccountLifecycleService
import com.eligo.server.profile.mapper.UserInterestTagMapper
import com.eligo.server.profile.mapper.UserProfileMapper
import java.time.LocalDateTime
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test")
class DeactivationDataRequestProcessor(
    private val requests: UserDataRequestMapper,
    private val events: UserDataRequestEventMapper,
    private val users: UserMapper,
    private val sessions: UserLoginSessionMapper,
    private val wechat: UserWechatAccountMapper,
    private val phones: UserPhoneBindingMapper,
    private val profiles: UserProfileMapper,
    private val interests: UserInterestTagMapper,
    private val postLifecycle: PostAccountLifecycleService,
    private val engagementLifecycle: ActivityEngagementAccountLifecycleService
) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun process(requestId: Long, now: LocalDateTime) {
        val request = requests.lockById(requestId).orElse(null) ?: return
        if (request.requestType != UserDataRequestEntity.TYPE_DEACTIVATION ||
            (request.status != UserDataRequestEntity.STATUS_WAITING_EXECUTION &&
                request.status != UserDataRequestEntity.STATUS_FAILED) ||
            request.executeAfter!!.isAfter(now)
        ) {
            return
        }
        val sourceStatus = request.status!!
        val processingVersion = request.version!! + 1
        if (requests.markProcessing(requestId, sourceStatus, request.version!!, now) != 1) {
            return
        }
        appendEvent(requestId, "DEACTIVATION_PROCESSING", sourceStatus,
            UserDataRequestEntity.STATUS_PROCESSING, "PROCESSING", now)
        val user = users.lockById(request.userId!!).orElse(null)
            ?: throw IllegalStateException("注销任务对应用户不存在")
        if (user.status == 3) {
            complete(requestId, processingVersion, now)
            appendEvent(requestId, "DEACTIVATION_COMPLETED",
                UserDataRequestEntity.STATUS_PROCESSING,
                UserDataRequestEntity.STATUS_COMPLETED, "SUCCESS", now)
            return
        }
        if (user.status != 2) {
            throw IllegalStateException("注销任务对应账号状态冲突")
        }
        postLifecycle.applyDeactivation(user.id!!, now)
        engagementLifecycle.applyDeactivation(user.id!!, now)
        sessions.revokeAllActiveByUserId(user.id!!, "ACCOUNT_DEACTIVATED", now)
        wechat.unbindAllActiveByUserId(user.id!!, now)
        phones.anonymizeAndUnbindAllActiveByUserId(user.id!!, now, "ACCOUNT_DEACTIVATED")
        interests.deleteByUserId(user.id!!)
        profiles.anonymizeByUserId(user.id!!, now)
        if (users.markDeactivated(user.id!!, user.version!!, now) != 1) {
            throw IllegalStateException("注销任务并发状态冲突")
        }
        complete(requestId, processingVersion, now)
        appendEvent(requestId, "DEACTIVATION_COMPLETED",
            UserDataRequestEntity.STATUS_PROCESSING,
            UserDataRequestEntity.STATUS_COMPLETED, "SUCCESS", now)
    }

    private fun complete(requestId: Long, version: Int, now: LocalDateTime) {
        if (requests.markCompleted(requestId, version, now) != 1) {
            throw IllegalStateException("注销任务并发状态冲突")
        }
    }

    private fun appendEvent(
        requestId: Long,
        eventType: String,
        fromStatus: Int,
        toStatus: Int,
        resultCode: String,
        now: LocalDateTime
    ) {
        val event = UserDataRequestEventEntity()
        event.requestId = requestId
        event.eventType = eventType
        event.fromStatus = fromStatus
        event.toStatus = toStatus
        event.actorType = UserDataRequestEventEntity.ACTOR_SYSTEM
        event.resultCode = resultCode
        event.createdAt = now
        events.insert(event)
    }
}
