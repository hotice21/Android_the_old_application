package com.eligo.server.account.service

import com.eligo.server.account.entity.UserDataRequestEntity
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
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@Profile("!test")
class AccountDataJob(
    private val requests: UserDataRequestMapper,
    private val deactivations: DeactivationDataRequestProcessor,
    private val exports: ExportDataRequestProcessor,
    private val failureRecorder: AccountDataFailureRecorder,
    private val clock: Clock
) {

    @Autowired
    constructor(
        requests: UserDataRequestMapper,
        deactivations: DeactivationDataRequestProcessor,
        exports: ExportDataRequestProcessor,
        failureRecorder: AccountDataFailureRecorder
    ) : this(requests, deactivations, exports, failureRecorder, Clock.systemUTC())

    constructor(
        requests: UserDataRequestMapper,
        events: UserDataRequestEventMapper,
        users: UserMapper,
        sessions: UserLoginSessionMapper,
        wechat: UserWechatAccountMapper,
        phones: UserPhoneBindingMapper,
        profiles: UserProfileMapper,
        interests: UserInterestTagMapper,
        postLifecycle: PostAccountLifecycleService,
        engagementLifecycle: ActivityEngagementAccountLifecycleService,
        exportService: PersonalDataExportService,
        clock: Clock
    ) : this(
        requests,
        DeactivationDataRequestProcessor(
            requests, events, users, sessions, wechat, phones, profiles, interests,
            postLifecycle, engagementLifecycle
        ),
        ExportDataRequestProcessor(requests, events, exportService),
        AccountDataFailureRecorder(requests, events),
        clock
    )

    @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
    fun executePendingDeactivations() {
        val now = now()
        for (requestId in requests.findDueDeactivationIds(now, BATCH_SIZE)) {
            executeSafely(requestId, UserDataRequestEntity.TYPE_DEACTIVATION, now, deactivations::process)
        }
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
    fun executePendingExports() {
        val now = now()
        for (requestId in requests.findDueExportIds(now, BATCH_SIZE)) {
            executeSafely(requestId, UserDataRequestEntity.TYPE_EXPORT, now, exports::process)
        }
    }

    private fun executeSafely(
        requestId: Long,
        requestType: Int,
        now: LocalDateTime,
        processor: RequestProcessor
    ) {
        try {
            processor.process(requestId, now)
        } catch (failure: RuntimeException) {
            try {
                failureRecorder.record(requestId, requestType, failure, now)
            } catch (recordFailure: RuntimeException) {
                log.error(
                    "账号数据后台任务失败状态记录失败 requestId={} requestType={}",
                    requestId, requestType, recordFailure
                )
            }
        }
    }

    private fun now(): LocalDateTime = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)

    private fun interface RequestProcessor {
        fun process(requestId: Long, now: LocalDateTime)
    }

    companion object {
        private val log = LoggerFactory.getLogger(AccountDataJob::class.java)
        private const val BATCH_SIZE = 20
    }
}
