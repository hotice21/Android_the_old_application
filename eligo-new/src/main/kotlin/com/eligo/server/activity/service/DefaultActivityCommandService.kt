package com.eligo.server.activity.service

import com.eligo.server.account.service.AccountStateLockService
import com.eligo.server.activity.dto.OrganizationActivityCreateRequest
import com.eligo.server.activity.dto.OrganizationActivityUpdateRequest
import com.eligo.server.activity.dto.PersonalActivityCreateRequest
import com.eligo.server.activity.dto.PersonalActivityUpdateRequest
import com.eligo.server.activity.entity.ActivityCreateIdempotencyTombstoneEntity
import com.eligo.server.activity.entity.ActivityEntity
import com.eligo.server.activity.entity.ActivityLifecycleEventEntity
import com.eligo.server.activity.entity.ActivityMediaEntity
import com.eligo.server.activity.error.ActivityErrorCode
import com.eligo.server.activity.mapper.ActivityCreateIdempotencyTombstoneMapper
import com.eligo.server.activity.mapper.ActivityLifecycleEventMapper
import com.eligo.server.activity.mapper.ActivityMapper
import com.eligo.server.activity.mapper.ActivityMediaMapper
import com.eligo.server.activity.vo.ActivityOwnerSummaryView
import com.eligo.server.activity.vo.ManagedActivityDetailView
import com.eligo.server.activity.vo.PublicImageView
import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.file.entity.FileObjectEntity
import com.eligo.server.file.mapper.FileObjectMapper
import com.eligo.server.organization.entity.OrganizationEntity
import com.eligo.server.organization.mapper.OrganizationMapper
import com.eligo.server.participation.service.ParticipationActivityCancellationService
import com.eligo.server.profile.mapper.UserProfileMapper
import com.eligo.server.profile.service.ProfileCompletionReader
import com.eligo.server.profile.service.RegionCatalog
import com.eligo.server.security.SensitiveDataCodec
import com.eligo.server.security.UserPrincipal
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.Locale
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Service
@Profile("!test")
class DefaultActivityCommandService(
    private val activities: ActivityMapper,
    private val idempotencyTombstones: ActivityCreateIdempotencyTombstoneMapper,
    private val media: ActivityMediaMapper,
    private val events: ActivityLifecycleEventMapper,
    private val organizations: OrganizationMapper,
    private val profiles: UserProfileMapper,
    private val files: FileObjectMapper,
    private val completion: ProfileCompletionReader,
    private val regions: RegionCatalog,
    private val redis: StringRedisTemplate,
    private val participationCancellation: ParticipationActivityCancellationService,
    private val accountStates: AccountStateLockService,
    private val sensitiveDataCodec: SensitiveDataCodec? = null,
    private val topics: ActivityTopicService? = null,
    private val clock: Clock = Clock.systemUTC()
) : ActivityCommandService {

    @Transactional
    override fun createPersonal(
        principal: UserPrincipal?,
        request: PersonalActivityCreateRequest?,
        idempotencyKey: String
    ): ActivityCreateOutcome {
        requireCompletedProfile(principal)
        val normalized = normalize(
            request?.title,
            request?.categoryCode,
            request?.description,
            request?.coverFileId,
            request?.mediaFileIds,
            request?.registrationStartsAt,
            request?.registrationEndsAt,
            request?.startsAt,
            request?.endsAt,
            request?.regionCode,
            request?.addressDetail,
            request?.latitude,
            request?.longitude,
            request?.capacity,
            request?.signupDetails,
            request?.organizerMessage,
            request?.registrationGender,
            request?.organizerPhone,
            request?.organizerWechat,
            request?.organizerWechatQrFileId,
            request?.refundPolicy,
            request?.placeName,
            request?.coordinateSystem,
            request?.topics
        )
        return create(
            principal!!,
            "USER",
            principal.userId,
            normalized,
            idempotencyKey
        )
    }

    @Transactional
    override fun createOrganization(
        principal: UserPrincipal?,
        organizationId: Long,
        request: OrganizationActivityCreateRequest?,
        idempotencyKey: String
    ): ActivityCreateOutcome {
        requireCompletedProfile(principal)
        requireActiveOrganization(principal!!.userId, organizationId)
        val normalized = normalize(
            request?.title,
            request?.categoryCode,
            request?.description,
            request?.coverFileId,
            request?.mediaFileIds,
            request?.registrationStartsAt,
            request?.registrationEndsAt,
            request?.startsAt,
            request?.endsAt,
            request?.regionCode,
            request?.addressDetail,
            request?.latitude,
            request?.longitude,
            request?.capacity,
            null,
            null,
            request?.registrationGender,
            request?.organizerPhone,
            request?.organizerWechat,
            request?.organizerWechatQrFileId,
            request?.refundPolicy,
            request?.placeName,
            request?.coordinateSystem,
            request?.topics
        )
        return create(
            principal,
            "ORGANIZATION",
            organizationId,
            normalized,
            idempotencyKey
        )
    }

    @Transactional
    override fun updatePersonal(
        principal: UserPrincipal?,
        activityId: Long,
        request: PersonalActivityUpdateRequest?
    ): ManagedActivityDetailView {
        requireCompletedProfile(principal)
        if (activityId <= 0 || request == null || request.version == null
            || request.version < 0
        ) {
            throw validation()
        }
        val normalized = normalize(
            request.title,
            request.categoryCode,
            request.description,
            request.coverFileId,
            request.mediaFileIds,
            request.registrationStartsAt,
            request.registrationEndsAt,
            request.startsAt,
            request.endsAt,
            request.regionCode,
            request.addressDetail,
            request.latitude,
            request.longitude,
            request.capacity,
            request.signupDetails,
            request.organizerMessage,
            request.registrationGender,
            request.organizerPhone,
            request.organizerWechat,
            request.organizerWechatQrFileId,
            request.refundPolicy,
            request.placeName,
            request.coordinateSystem,
            request.topics
        )
        return update(
            principal!!,
            "USER",
            null,
            activityId,
            request.version,
            normalized
        )
    }

    @Transactional
    override fun updateOrganization(
        principal: UserPrincipal?,
        organizationId: Long,
        activityId: Long,
        request: OrganizationActivityUpdateRequest?
    ): ManagedActivityDetailView {
        requireCompletedProfile(principal)
        if (organizationId <= 0 || activityId <= 0 || request == null
            || request.version == null || request.version < 0
        ) {
            throw validation()
        }
        val normalized = normalize(
            request.title,
            request.categoryCode,
            request.description,
            request.coverFileId,
            request.mediaFileIds,
            request.registrationStartsAt,
            request.registrationEndsAt,
            request.startsAt,
            request.endsAt,
            request.regionCode,
            request.addressDetail,
            request.latitude,
            request.longitude,
            request.capacity,
            null,
            null,
            request.registrationGender,
            request.organizerPhone,
            request.organizerWechat,
            request.organizerWechatQrFileId,
            request.refundPolicy,
            request.placeName,
            request.coordinateSystem,
            request.topics
        )
        return update(
            principal!!,
            "ORGANIZATION",
            organizationId,
            activityId,
            request.version,
            normalized
        )
    }

    @Transactional
    override fun publish(
        principal: UserPrincipal?,
        activityId: Long
    ): ManagedActivityDetailView {
        if (principal == null || activityId <= 0) {
            throw validation()
        }
        val activity = activities.lockById(activityId).orElseThrow(::notFound)
        requireOwner(principal.userId, activity)
        accountStates.lockActive(principal.userId)
        if (activity.status == PUBLISHED) {
            val commandNow = now()
            rejectIfEndReached(activity, commandNow)
            return managed(activity)
        }
        if (activity.status != DRAFT) {
            throw BusinessException(ActivityErrorCode.STATUS_CONFLICT)
        }

        requireCompletedProfile(principal)
        validatePublishable(activity, principal.userId)
        val commandNow = now()
        rejectIfEndReached(activity, commandNow)
        if (activities.publishById(activityId, commandNow) != 1) {
            throw BusinessException(ActivityErrorCode.STATUS_CONFLICT)
        }
        insertLifecycle(activityId, DRAFT, PUBLISHED, principal.userId, commandNow)

        var published = activities.selectById(activityId)
        if (published == null) {
            activity.status = PUBLISHED
            activity.publishedAt = commandNow
            activity.updatedAt = commandNow
            activity.version = (activity.version ?: 0) + 1
            published = activity
        }
        return managed(published)
    }

    @Transactional
    override fun cancel(
        principal: UserPrincipal?,
        activityId: Long
    ): ManagedActivityDetailView {
        if (principal == null || activityId <= 0) {
            throw validation()
        }
        val activity = activities.lockById(activityId).orElseThrow(::notFound)
        requireOwner(principal.userId, activity)
        if (activity.status == CANCELLED) {
            return managed(activity)
        }
        if (activity.status != PUBLISHED && activity.status != HIDDEN) {
            throw BusinessException(ActivityErrorCode.STATUS_CONFLICT)
        }

        val fromStatus = activity.status!!
        val commandNow = now()
        rejectIfEndReached(activity, commandNow)
        participationCancellation.terminateActiveParticipations(
            activityId, activity.participantCount ?: 0, commandNow
        )
        if (activities.cancelById(
                activityId, fromStatus, commandNow, principal.userId
            ) != 1
        ) {
            throw BusinessException(ActivityErrorCode.STATUS_CONFLICT)
        }
        insertLifecycle(activityId, fromStatus, CANCELLED, principal.userId, commandNow)

        var cancelled = activities.selectById(activityId)
        if (cancelled == null) {
            activity.status = CANCELLED
            activity.participantCount = 0
            activity.operatorUserId = principal.userId
            activity.version = (activity.version ?: 0) + 1
            activity.updatedAt = commandNow
            cancelled = activity
        }
        return managed(cancelled)
    }

    @Transactional
    override fun deleteDraft(principal: UserPrincipal?, activityId: Long) {
        if (principal == null || activityId <= 0) {
            throw validation()
        }
        val activity = activities.lockById(activityId).orElseThrow(::notFound)
        requireOwner(principal.userId, activity)
        if (activity.status != DRAFT) {
            throw BusinessException(ActivityErrorCode.STATUS_CONFLICT)
        }

        preserveCreateIdempotency(activity)
        media.deleteByActivityId(activityId)
        deleteTopics(activityId)
        events.deleteByActivityId(activityId)
        if (activities.deleteDraftById(activityId) != 1) {
            throw BusinessException(ActivityErrorCode.STATUS_CONFLICT)
        }
    }

    private fun update(
        principal: UserPrincipal,
        ownerType: String,
        organizationId: Long?,
        activityId: Long,
        expectedVersion: Int,
        normalized: NormalizedActivity
    ): ManagedActivityDetailView {
        val activity = activities.lockById(activityId).orElseThrow(::notFound)
        if ("USER" == ownerType) {
            if (activity.ownerUserId != principal.userId
                || activity.ownerOrganizationId != null
            ) {
                throw notFound()
            }
        } else if (activity.ownerOrganizationId != organizationId
            || activity.ownerUserId != null
            || organizations.findActiveOwnedByUserId(principal.userId).none { item ->
                item.id == organizationId
            }
        ) {
            throw notFound()
        }
        if (activity.version != expectedVersion) {
            throw BusinessException(ActivityErrorCode.VERSION_CONFLICT)
        }
        if (activity.status != DRAFT && activity.status != PUBLISHED) {
            throw BusinessException(ActivityErrorCode.STATUS_CONFLICT)
        }
        val originalEndsAt = activity.endsAt

        val referencedFiles = validateFiles(
            normalized.fileIds, principal.userId
        )
        val contactQrFile = validateContactQrFile(
            normalized.organizerWechatQrFileId, principal.userId
        )
        activity.operatorUserId = principal.userId
        applyContent(activity, normalized)
        if (activity.status == PUBLISHED) {
            validatePublishable(activity, principal.userId, normalized.mediaFileIds)
        }

        val commandNow = now()
        if (activity.status == PUBLISHED) {
            rejectIfEndReached(originalEndsAt, commandNow)
            rejectIfEndReached(activity, commandNow)
        }
        if (activities.updateContentByIdAndVersion(
                activity, expectedVersion, commandNow
            ) != 1
        ) {
            throw BusinessException(ActivityErrorCode.VERSION_CONFLICT)
        }
        media.deleteByActivityId(activityId)
        var sortOrder = 1
        for (fileId in normalized.mediaFileIds) {
            val mediaEntity = ActivityMediaEntity().apply {
                this.activityId = activityId
                this.fileId = fileId
                this.sortOrder = sortOrder++
                createdAt = commandNow
            }
            media.insert(mediaEntity)
        }
        replaceTopics(activityId, normalized.topics, commandNow)
        activateTemporaryFiles(referencedFiles, commandNow)
        activateTemporaryFiles(
            contactQrFile?.let { listOf(it) } ?: emptyList(), commandNow
        )
        activity.version = expectedVersion + 1
        activity.updatedAt = commandNow
        return managed(activity)
    }

    private fun create(
        principal: UserPrincipal,
        ownerType: String,
        ownerId: Long,
        normalized: NormalizedActivity,
        idempotencyKey: String
    ): ActivityCreateOutcome {
        validateIdempotencyKey(idempotencyKey)
        val requestFingerprints = fingerprints(ownerType, ownerId, normalized)
        val fingerprint = requestFingerprints.canonical
        val scope = createIdempotencyScope(ownerType, ownerId, principal.userId)
        val cacheKey = idempotencyKey(ownerType, ownerId, principal.userId, idempotencyKey)
        val now = now()
        var tombstone = idempotencyTombstones.findByScopeAndKey(scope, idempotencyKey)
        if (tombstone.isPresent && isExpiredTombstone(tombstone.get(), now)) {
            val deleted = idempotencyTombstones.deleteExpiredByScopeAndKey(
                scope, idempotencyKey, now
            )
            tombstone = if (deleted == 1) {
                java.util.Optional.empty()
            } else {
                idempotencyTombstones.findByScopeAndKey(scope, idempotencyKey)
            }
        }
        if (tombstone.isPresent) {
            if (!requestFingerprints.matches(
                    tombstone.get().createIdempotencyFingerprint
                )
            ) {
                throw BusinessException(ActivityErrorCode.IDEMPOTENCY_KEY_CONFLICT)
            }
            throw BusinessException(ActivityErrorCode.IDEMPOTENCY_RESULT_DELETED)
        }
        val cached = cached(
            cacheKey, requestFingerprints, scope, idempotencyKey, now
        )
        if (cached != null) {
            return ActivityCreateOutcome(managed(cached), true)
        }
        var existing = activities.findByCreateIdempotencyScope(scope, idempotencyKey)
        if (existing != null && existing.isPresent
            && isExpiredCreateIdempotency(existing.get(), now)
        ) {
            val cleared = activities.clearExpiredCreateIdempotency(
                existing.get().id!!,
                scope,
                idempotencyKey,
                now.minus(IDEMPOTENCY_TTL)
            )
            existing = if (cleared == 1) {
                java.util.Optional.empty()
            } else {
                activities.lockByCreateIdempotencyScope(scope, idempotencyKey)
            }
        }
        if (existing != null && existing.isPresent) {
            val activity = existing.get()
            if (!requestFingerprints.matches(
                    activity.createIdempotencyFingerprint
                )
            ) {
                throw BusinessException(ActivityErrorCode.IDEMPOTENCY_KEY_CONFLICT)
            }
            remember(
                cacheKey,
                activity,
                activity.createIdempotencyFingerprint!!,
                now
            )
            return ActivityCreateOutcome(managed(activity), true)
        }

        val referencedFiles = validateFiles(
            normalized.fileIds, principal.userId
        )
        val contactQrFile = validateContactQrFile(
            normalized.organizerWechatQrFileId, principal.userId
        )
        val activity = ActivityEntity().apply {
            ownerUserId = if ("USER" == ownerType) ownerId else null
            ownerOrganizationId = if ("ORGANIZATION" == ownerType) ownerId else null
            operatorUserId = principal.userId
            createIdempotencyScope = scope
            createIdempotencyKey = idempotencyKey
            createIdempotencyFingerprint = fingerprint
            status = DRAFT
            title = normalized.title
            categoryCode = normalized.categoryCode
            description = normalized.description
            coverFileId = normalized.coverFileId
            registrationStartsAt = normalized.registrationStartsAt
            registrationEndsAt = normalized.registrationEndsAt
            startsAt = normalized.startsAt
            endsAt = normalized.endsAt
            regionCode = normalized.regionCode
            addressDetail = normalized.addressDetail
            placeName = normalized.placeName
            latitude = normalized.latitude
            longitude = normalized.longitude
            capacity = normalized.capacity
            participantCount = 0
            registrationGender = normalized.registrationGender
            organizerPhoneCiphertext = encrypt(normalized.organizerPhone)
            organizerWechatCiphertext = encrypt(normalized.organizerWechat)
            organizerWechatQrFileId = normalized.organizerWechatQrFileId
            signupDetails = normalized.signupDetails
            organizerMessage = normalized.organizerMessage
            publishedAt = null
            version = 0
            createdAt = now
            updatedAt = now
        }
        try {
            activities.insert(activity)
        } catch (exception: DuplicateKeyException) {
            val winner = activities.lockByCreateIdempotencyScope(scope, idempotencyKey)
                .orElseThrow { exception }
            if (!requestFingerprints.matches(winner.createIdempotencyFingerprint)) {
                throw BusinessException(ActivityErrorCode.IDEMPOTENCY_KEY_CONFLICT)
            }
            remember(
                cacheKey,
                winner,
                winner.createIdempotencyFingerprint!!,
                now
            )
            return ActivityCreateOutcome(managed(winner), true)
        }

        var sortOrder = 1
        for (fileId in normalized.mediaFileIds) {
            val mediaEntity = ActivityMediaEntity().apply {
                this.activityId = activity.id
                this.fileId = fileId
                this.sortOrder = sortOrder++
                createdAt = now
            }
            media.insert(mediaEntity)
        }
        replaceTopics(activity.id!!, normalized.topics, now)
        insertLifecycle(activity.id!!, null, DRAFT, principal.userId, now)
        activateTemporaryFiles(referencedFiles, now)
        activateTemporaryFiles(
            contactQrFile?.let { listOf(it) } ?: emptyList(), now
        )
        remember(cacheKey, activity, fingerprint, now)
        return ActivityCreateOutcome(managed(activity), false)
    }

    private fun preserveCreateIdempotency(activity: ActivityEntity) {
        val now = now()
        if (activity.createIdempotencyScope == null
            || activity.createIdempotencyKey == null
            || activity.createIdempotencyFingerprint == null
            || activity.createdAt == null
        ) {
            return
        }
        val expiresAt = activity.createdAt!!.plus(IDEMPOTENCY_TTL)
        if (!expiresAt.isAfter(now)) {
            return
        }
        val tombstone = ActivityCreateIdempotencyTombstoneEntity().apply {
            createIdempotencyScope = activity.createIdempotencyScope
            createIdempotencyKey = activity.createIdempotencyKey
            createIdempotencyFingerprint = activity.createIdempotencyFingerprint
            this.activityId = activity.id
            deletedAt = now
            this.expiresAt = expiresAt
        }
        idempotencyTombstones.insert(tombstone)
    }

    private fun isExpiredCreateIdempotency(
        activity: ActivityEntity,
        now: LocalDateTime
    ): Boolean =
        activity.createdAt != null
            && !activity.createdAt!!.plus(IDEMPOTENCY_TTL).isAfter(now)

    private fun isExpiredTombstone(
        tombstone: ActivityCreateIdempotencyTombstoneEntity,
        now: LocalDateTime
    ): Boolean =
        tombstone.expiresAt != null
            && !tombstone.expiresAt!!.isAfter(now)

    private fun rejectIfEndReached(activity: ActivityEntity, now: LocalDateTime) =
        rejectIfEndReached(activity.endsAt, now)

    private fun rejectIfEndReached(endsAt: LocalDateTime?, now: LocalDateTime) {
        if (endsAt != null && !endsAt.isAfter(now)) {
            throw BusinessException(ActivityErrorCode.STATUS_CONFLICT)
        }
    }

    private fun requireCompletedProfile(principal: UserPrincipal?) {
        if (principal == null) {
            throw BusinessException(CommonErrorCode.AUTHENTICATION_REQUIRED)
        }
        if (!completion.isCompleted(principal.userId)) {
            throw BusinessException(AccountUserFileErrorCode.PROFILE_INCOMPLETE)
        }
    }

    private fun requireActiveOrganization(userId: Long, organizationId: Long): OrganizationEntity {
        if (organizationId <= 0) {
            throw validation()
        }
        return organizations.findActiveOwnedByUserId(userId)
            .firstOrNull { item -> item.id == organizationId }
            ?: throw BusinessException(CommonErrorCode.ACCESS_DENIED)
    }

    private fun requireOwner(userId: Long, activity: ActivityEntity) {
        if (activity.ownerUserId != null) {
            if (activity.ownerUserId != userId) {
                throw notFound()
            }
            return
        }
        if (activity.ownerOrganizationId == null
            || organizations.findActiveOwnedByUserId(userId).none { item ->
                item.id == activity.ownerOrganizationId
            }
        ) {
            throw notFound()
        }
    }

    private fun normalize(
        title: String?,
        categoryCode: String?,
        description: String?,
        coverFileId: String?,
        mediaFileIds: List<String>?,
        registrationStartsAt: Instant?,
        registrationEndsAt: Instant?,
        startsAt: Instant?,
        endsAt: Instant?,
        regionCode: String?,
        addressDetail: String?,
        latitude: BigDecimal?,
        longitude: BigDecimal?,
        capacity: Int?,
        signupDetails: String?,
        organizerMessage: String?,
        registrationGender: String?,
        organizerPhone: String?,
        organizerWechat: String?,
        organizerWechatQrFileId: String?,
        refundPolicy: String?,
        placeName: String?,
        coordinateSystem: String?,
        topicValues: List<String>?
    ): NormalizedActivity {
        val normalizedTitle = requiredText(title, MAX_TITLE_LENGTH)
        val normalizedCategory = category(categoryCode)
        val normalizedDescription = optionalText(description, 5000)
        val coverId = parseFileId(coverFileId)
        val mediaIds = parseMediaIds(mediaFileIds)
        if (coverId != null && mediaIds.contains(coverId)) {
            throw validation()
        }
        val registrationStart = local(registrationStartsAt)
        val registrationEnd = local(registrationEndsAt)
        val start = local(startsAt)
        val end = local(endsAt)
        validateTimeOrder(registrationStart, registrationEnd, start, end)
        val normalizedRegion = region(regionCode)
        val normalizedAddress = optionalText(addressDetail, 512)
        val normalizedPlaceName = optionalText(placeName, 100)
        val normalizedLatitude = coordinate(latitude, MIN_LATITUDE, MAX_LATITUDE)
        val normalizedLongitude = coordinate(longitude, MIN_LONGITUDE, MAX_LONGITUDE)
        if ((normalizedLatitude == null) != (normalizedLongitude == null)) {
            throw validation()
        }
        var normalizedCoordinateSystem = optionalText(coordinateSystem, 16)
        if (normalizedLatitude == null) {
            if (normalizedCoordinateSystem != null) {
                throw validation()
            }
        } else if (normalizedCoordinateSystem == null) {
            normalizedCoordinateSystem = "GCJ-02"
        } else if (normalizedCoordinateSystem != "GCJ-02") {
            throw validation()
        }
        if (capacity != null && capacity <= 0) {
            throw validation()
        }
        val normalizedRegistrationGender = registrationGender(registrationGender)
        val normalizedPhone = optionalText(organizerPhone, 32)
        val normalizedWechat = optionalText(organizerWechat, 64)
        val contactQrFileId = parseFileId(organizerWechatQrFileId)
        if (contactQrFileId != null
            && (contactQrFileId == coverId || mediaIds.contains(contactQrFileId))
        ) {
            throw validation()
        }
        normalizeRefundPolicy(refundPolicy)
        val normalizedTopics = normalizeTopics(topicValues)
        return NormalizedActivity(
            normalizedTitle,
            normalizedCategory,
            normalizedDescription,
            coverId,
            mediaIds,
            registrationStart,
            registrationEnd,
            start,
            end,
            normalizedRegion,
            normalizedAddress,
            normalizedLatitude,
            normalizedLongitude,
            capacity,
            optionalText(signupDetails, 2000),
            optionalText(organizerMessage, 1000),
            normalizedRegistrationGender,
            normalizedPhone,
            normalizedWechat,
            contactQrFileId,
            normalizedPlaceName,
            normalizedCoordinateSystem,
            normalizedTopics,
            topicValues != null
        )
    }

    private fun validatePublishable(activity: ActivityEntity, userId: Long) {
        val mediaFileIds = media.findByActivityId(activity.id!!).map { it.fileId!! }
        validatePublishable(activity, userId, mediaFileIds)
    }

    private fun validatePublishable(
        activity: ActivityEntity,
        userId: Long,
        mediaFileIds: List<Long?>
    ) {
        requiredText(activity.title, MAX_TITLE_LENGTH)
        if (category(activity.categoryCode) == null
            || optionalText(activity.description, 5000) == null
            || activity.coverFileId == null
            || activity.registrationStartsAt == null
            || activity.registrationEndsAt == null
            || activity.startsAt == null
            || activity.endsAt == null
            || region(activity.regionCode) == null
            || optionalText(activity.addressDetail, 512) == null
            || optionalText(activity.placeName, 100) == null
            || activity.latitude == null
            || activity.longitude == null
            || activity.capacity == null
            || activity.capacity!! <= 0
        ) {
            throw validation()
        }
        coordinate(activity.latitude, MIN_LATITUDE, MAX_LATITUDE)
        coordinate(activity.longitude, MIN_LONGITUDE, MAX_LONGITUDE)
        validateTimeOrder(
            activity.registrationStartsAt,
            activity.registrationEndsAt,
            activity.startsAt,
            activity.endsAt
        )
        if (activity.participantCount != null
            && activity.participantCount!! > activity.capacity!!
        ) {
            throw BusinessException(ActivityErrorCode.CAPACITY_CONFLICT)
        }
        if (activity.ownerOrganizationId != null
            && (activity.signupDetails != null
                || activity.organizerMessage != null)
        ) {
            throw validation()
        }
        if (mediaFileIds.size > 5
            || mediaFileIds.any { item -> item == activity.coverFileId }
        ) {
            throw validation()
        }
        validateContactQrFile(activity.organizerWechatQrFileId, userId)
        val fileIds = ArrayList<Long>()
        fileIds.add(activity.coverFileId!!)
        fileIds.addAll(mediaFileIds.filterNotNull())
        validateFiles(fileIds, userId)
    }

    private fun applyContent(activity: ActivityEntity, normalized: NormalizedActivity) {
        activity.title = normalized.title
        activity.categoryCode = normalized.categoryCode
        activity.description = normalized.description
        activity.coverFileId = normalized.coverFileId
        activity.registrationStartsAt = normalized.registrationStartsAt
        activity.registrationEndsAt = normalized.registrationEndsAt
        activity.startsAt = normalized.startsAt
        activity.endsAt = normalized.endsAt
        activity.regionCode = normalized.regionCode
        activity.addressDetail = normalized.addressDetail
        activity.placeName = normalized.placeName
        activity.latitude = normalized.latitude
        activity.longitude = normalized.longitude
        activity.capacity = normalized.capacity
        activity.registrationGender = normalized.registrationGender
        activity.organizerPhoneCiphertext = encrypt(normalized.organizerPhone)
        activity.organizerWechatCiphertext = encrypt(normalized.organizerWechat)
        activity.organizerWechatQrFileId = normalized.organizerWechatQrFileId
        activity.signupDetails = normalized.signupDetails
        activity.organizerMessage = normalized.organizerMessage
    }

    private fun validateFiles(fileIds: List<Long>, userId: Long): List<FileObjectEntity> {
        if (fileIds.isEmpty()) {
            return emptyList()
        }
        val uniqueIds = LinkedHashSet<Long>()
        for (fileId in fileIds) {
            if (!uniqueIds.add(fileId)) {
                throw validation()
            }
        }
        val now = now()
        val referencedFiles = ArrayList<FileObjectEntity>(uniqueIds.size)
        for (fileId in uniqueIds.sorted()) {
            val file = files.lockById(fileId).orElseThrow(::fileConflict)
            val usable = file.uploaderType == FileObjectEntity.UPLOADER_USER
                && file.uploaderId == userId
                && FileObjectEntity.PURPOSE_ACTIVITY == file.purpose
                && file.accessLevel == FileObjectEntity.ACCESS_PUBLIC
                && file.scanStatus == FileObjectEntity.SCAN_PASSED
                && (file.lifecycleStatus == FileObjectEntity.LIFECYCLE_TEMPORARY
                    || file.lifecycleStatus == FileObjectEntity.LIFECYCLE_ACTIVE)
                && (file.expiresAt == null || file.expiresAt!!.isAfter(now))
            if (!usable) {
                throw fileConflict()
            }
            referencedFiles.add(file)
        }
        return referencedFiles.toList()
    }

    private fun validateContactQrFile(fileId: Long?, userId: Long): FileObjectEntity? {
        if (fileId == null) {
            return null
        }
        val file = files.lockById(fileId).orElseThrow(::fileConflict)
        val now = now()
        val usable = file.uploaderType == FileObjectEntity.UPLOADER_USER
            && file.uploaderId == userId
            && FileObjectEntity.PURPOSE_ACTIVITY_CONTACT_QR == file.purpose
            && file.accessLevel == FileObjectEntity.ACCESS_PRIVATE
            && file.scanStatus == FileObjectEntity.SCAN_PASSED
            && (file.lifecycleStatus == FileObjectEntity.LIFECYCLE_TEMPORARY
                || file.lifecycleStatus == FileObjectEntity.LIFECYCLE_ACTIVE)
            && (file.expiresAt == null || file.expiresAt!!.isAfter(now))
        if (!usable) {
            throw fileConflict()
        }
        return file
    }

    private fun activateTemporaryFiles(
        referencedFiles: List<FileObjectEntity>,
        now: LocalDateTime
    ) {
        for (file in referencedFiles) {
            if (file.lifecycleStatus == FileObjectEntity.LIFECYCLE_TEMPORARY
                && files.activate(
                    file.id!!,
                    now,
                    file.version ?: 0
                ) != 1
            ) {
                throw fileConflict()
            }
        }
    }

    private fun cached(
        key: String,
        requestFingerprints: CreateFingerprints,
        scope: String,
        idempotencyKey: String,
        now: LocalDateTime
    ): ActivityEntity? {
        val value = try {
            redis.opsForValue().get(key)
        } catch (exception: RuntimeException) {
            log.warn("读取活动创建幂等缓存失败，回退数据库", exception)
            return null
        } ?: return null
        val separator = value.indexOf('|')
        if (separator <= 0 || separator == value.length - 1) {
            return null
        }
        val cachedFingerprint = value.substring(separator + 1)
        if (!requestFingerprints.matches(cachedFingerprint)) {
            return null
        }
        val activityId = try {
            val parsed = value.substring(0, separator).toLong()
            if (parsed <= 0) throw NumberFormatException()
            parsed
        } catch (exception: NumberFormatException) {
            return null
        }
        val activity = activities.selectById(activityId)
        if (activity == null
            || cachedFingerprint != activity.createIdempotencyFingerprint
            || scope != activity.createIdempotencyScope
            || idempotencyKey != activity.createIdempotencyKey
            || isExpiredCreateIdempotency(activity, now)
        ) {
            return null
        }
        return activity
    }

    private fun remember(
        key: String,
        activity: ActivityEntity,
        fingerprint: String,
        now: LocalDateTime
    ) {
        val expiresAt = if (activity.createdAt == null) {
            now.plus(IDEMPOTENCY_TTL)
        } else {
            activity.createdAt!!.plus(IDEMPOTENCY_TTL)
        }
        if (!expiresAt.isAfter(now)) {
            return
        }
        val cacheWrite = Runnable {
            val ttl = Duration.between(now(), expiresAt)
            if (!ttl.isZero && !ttl.isNegative) {
                writeCache(key, "${activity.id}|$fingerprint", ttl)
            }
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() {
                        cacheWrite.run()
                    }
                }
            )
            return
        }
        cacheWrite.run()
    }

    private fun writeCache(key: String, value: String, ttl: Duration) {
        try {
            redis.opsForValue().set(key, value, ttl)
        } catch (exception: RuntimeException) {
            log.warn("写入活动创建幂等缓存失败，数据库结果保持有效", exception)
        }
    }

    private fun validateIdempotencyKey(value: String?) {
        if (value == null || value.length < 8 || value.length > 128
            || !value.matches(Regex("[A-Za-z0-9._:-]+"))
        ) {
            throw validation()
        }
    }

    private fun idempotencyKey(
        ownerType: String,
        ownerId: Long,
        userId: Long,
        key: String
    ): String = "eligo:activity:create:$userId:$ownerType:$ownerId:$key"

    private fun createIdempotencyScope(
        ownerType: String,
        ownerId: Long,
        userId: Long
    ): String = "$ownerType:$ownerId:$userId"

    private fun fingerprints(
        ownerType: String,
        ownerId: Long,
        activity: NormalizedActivity
    ): CreateFingerprints {
        val withTopics = fingerprint(ownerType, ownerId, activity, true, true, true, true)
        val preTopics = fingerprint(ownerType, ownerId, activity, true, true, true, false)
        val preCoordinateSystem = fingerprint(
            ownerType, ownerId, activity, true, true, false, false
        )
        val coordinatesOnly = fingerprint(
            ownerType, ownerId, activity, true, false, false, false
        )
        val legacy = if (activity.latitude == null && activity.longitude == null) {
            fingerprint(ownerType, ownerId, activity, false, false, false, false)
        } else {
            null
        }
        return CreateFingerprints(
            withTopics,
            preTopics,
            preCoordinateSystem,
            coordinatesOnly,
            legacy,
            activity.placeName != null,
            activity.topicsSpecified,
            activity.topics.isNotEmpty()
        )
    }

    private fun fingerprint(
        ownerType: String,
        ownerId: Long,
        activity: NormalizedActivity,
        includeCoordinates: Boolean,
        includePlaceName: Boolean,
        includeCoordinateSystem: Boolean,
        includeTopics: Boolean
    ): String {
        try {
            val bytes = ByteArrayOutputStream()
            val output = DataOutputStream(bytes)
            writeFingerprintValue(output, ownerType)
            writeFingerprintValue(output, ownerId)
            writeFingerprintValue(output, activity.title)
            writeFingerprintValue(output, activity.categoryCode)
            writeFingerprintValue(output, activity.description)
            writeFingerprintValue(output, activity.coverFileId)
            output.writeInt(activity.mediaFileIds.size)
            for (mediaFileId in activity.mediaFileIds) {
                writeFingerprintValue(output, mediaFileId)
            }
            writeFingerprintValue(output, activity.registrationStartsAt)
            writeFingerprintValue(output, activity.registrationEndsAt)
            writeFingerprintValue(output, activity.startsAt)
            writeFingerprintValue(output, activity.endsAt)
            writeFingerprintValue(output, activity.regionCode)
            writeFingerprintValue(output, activity.addressDetail)
            if (includePlaceName) {
                writeFingerprintValue(output, activity.placeName)
            }
            if (includeCoordinates) {
                writeFingerprintValue(output, activity.latitude)
                writeFingerprintValue(output, activity.longitude)
            }
            if (includeCoordinateSystem) {
                writeFingerprintValue(output, activity.coordinateSystem)
            }
            writeFingerprintValue(output, activity.capacity)
            writeFingerprintValue(output, activity.signupDetails)
            writeFingerprintValue(output, activity.organizerMessage)
            if (includeCoordinates) {
                writeFingerprintValue(output, activity.registrationGender)
                writeFingerprintValue(output, activity.organizerPhone)
                writeFingerprintValue(output, activity.organizerWechat)
                writeFingerprintValue(output, activity.organizerWechatQrFileId)
            }
            if (includeTopics) {
                output.writeInt(activity.topics.size)
                for (topic in activity.topics) {
                    writeFingerprintValue(output, topic)
                }
            }
            output.flush()
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256")
                    .digest(bytes.toByteArray())
            )
        } catch (exception: IOException) {
            throw IllegalStateException(exception)
        } catch (exception: NoSuchAlgorithmException) {
            throw IllegalStateException(exception)
        }
    }

    private fun writeFingerprintValue(output: DataOutputStream, value: Any?) {
        if (value == null) {
            output.writeBoolean(false)
            return
        }
        val encoded = value.toString().toByteArray(StandardCharsets.UTF_8)
        output.writeBoolean(true)
        output.writeInt(encoded.size)
        output.write(encoded)
    }

    private fun insertLifecycle(
        activityId: Long,
        fromStatus: Int?,
        toStatus: Int,
        operatorUserId: Long,
        now: LocalDateTime
    ) {
        val event = ActivityLifecycleEventEntity().apply {
            this.activityId = activityId
            this.fromStatus = fromStatus
            this.toStatus = toStatus
            this.operatorUserId = operatorUserId
            createdAt = now
        }
        events.insert(event)
    }

    private fun managed(activity: ActivityEntity): ManagedActivityDetailView {
        val owner = owner(activity)
        val mediaViews = media.findByActivityId(activity.id!!).map { item -> image(item.fileId!!)!! }
        return ManagedActivityDetailView(
            activity.id.toString(),
            statusName(activity.status),
            activity.title ?: "",
            activity.categoryCode,
            image(activity.coverFileId),
            owner,
            instant(activity.startsAt),
            instant(activity.endsAt),
            activity.regionCode,
            activity.addressDetail,
            activity.latitude,
            activity.longitude,
            activity.capacity,
            activity.participantCount,
            "FREE",
            activity.version,
            instant(activity.updatedAt),
            activity.description,
            mediaViews,
            instant(activity.registrationStartsAt),
            instant(activity.registrationEndsAt),
            if (activity.ownerOrganizationId == null) activity.signupDetails else null,
            if (activity.ownerOrganizationId == null) activity.organizerMessage else null,
            instant(activity.createdAt),
            instant(activity.publishedAt),
            registrationGenderName(activity.registrationGender),
            decrypt(activity.organizerPhoneCiphertext),
            decrypt(activity.organizerWechatCiphertext),
            contactQr(activity.id!!, activity.organizerWechatQrFileId),
            null,
            activity.placeName,
            coordinateSystem(activity.latitude, activity.longitude),
            topicValues(activity.id!!)
        )
    }

    private fun replaceTopics(activityId: Long, values: List<String>, now: LocalDateTime) {
        if (topics != null) {
            topics.replace(activityId, values, now)
        }
    }

    private fun deleteTopics(activityId: Long) {
        if (topics != null) {
            topics.deleteByActivityId(activityId)
        }
    }

    private fun topicValues(activityId: Long): List<String> =
        topics?.findByActivityId(activityId) ?: emptyList()

    private fun coordinateSystem(latitude: BigDecimal?, longitude: BigDecimal?): String? =
        if (latitude != null && longitude != null) "GCJ-02" else null

    private fun owner(activity: ActivityEntity): ActivityOwnerSummaryView {
        if (activity.ownerUserId != null) {
            val profile = profiles.findByUserId(activity.ownerUserId!!).orElse(null)
            val displayName = if (profile == null || profile.nickname.isNullOrBlank()) {
                "已注销用户"
            } else {
                profile.nickname
            }
            return ActivityOwnerSummaryView(
                "USER",
                activity.ownerUserId.toString(),
                displayName ?: "已注销用户",
                image(profile?.avatarFileId)
            )
        }
        val organization = organizations.selectById(activity.ownerOrganizationId)
            ?: throw notFound()
        return ActivityOwnerSummaryView(
            "ORGANIZATION",
            organization.id.toString(),
            organization.name ?: "",
            image(organization.avatarFileId)
        )
    }

    private fun requiredText(value: String?, maxLength: Int): String {
        val normalized = trimNullable(value)
        if (normalized == null || codePointLength(normalized) > maxLength) {
            throw validation()
        }
        return normalized
    }

    private fun optionalText(value: String?, maxLength: Int): String? {
        val normalized = trimNullable(value)
        if (normalized != null && codePointLength(normalized) > maxLength) {
            throw validation()
        }
        return normalized
    }

    private fun category(value: String?): String? {
        var normalized = trimNullable(value) ?: return null
        normalized = normalized.uppercase(Locale.ROOT)
        if (normalized !in CATEGORIES) {
            throw validation()
        }
        return normalized
    }

    private fun registrationGender(value: String?): Int {
        val normalized = trimNullable(value)
        if (normalized == null || "UNLIMITED".equals(normalized, ignoreCase = true)) {
            return 1
        }
        return when (normalized.uppercase(Locale.ROOT)) {
            "MALE" -> 2
            "FEMALE" -> 3
            else -> throw validation()
        }
    }

    private fun normalizeRefundPolicy(value: String?): String? {
        val normalized = trimNullable(value)
        if (normalized != null) {
            throw validation()
        }
        return null
    }

    private fun region(value: String?): String? {
        val normalized = trimNullable(value) ?: return null
        if (!normalized.matches(Regex("[0-9]{6}([0-9]{3})?"))
            || !regions.isDistrictCode(normalized)
        ) {
            throw validation()
        }
        return normalized
    }

    private fun coordinate(
        value: BigDecimal?,
        minimum: BigDecimal,
        maximum: BigDecimal
    ): BigDecimal? {
        if (value == null) {
            return null
        }
        return try {
            val normalized = value.setScale(7, RoundingMode.UNNECESSARY)
            if (normalized.compareTo(minimum) < 0
                || normalized.compareTo(maximum) > 0
            ) {
                throw validation()
            }
            normalized
        } catch (exception: ArithmeticException) {
            throw validation()
        }
    }

    private fun parseFileId(value: String?): Long? {
        val normalized = trimNullable(value) ?: return null
        return try {
            val id = normalized.toLong()
            if (id <= 0) throw NumberFormatException()
            id
        } catch (exception: NumberFormatException) {
            throw validation()
        }
    }

    private fun parseMediaIds(values: List<String>?): List<Long> {
        if (values.isNullOrEmpty()) {
            return emptyList()
        }
        if (values.size > 5) {
            throw validation()
        }
        val ids = LinkedHashSet<Long>()
        for (value in values) {
            val id = parseFileId(value)
            if (id == null || !ids.add(id)) {
                throw validation()
            }
        }
        return ids.toList()
    }

    private fun normalizeTopics(values: List<String>?): List<String> {
        if (topics == null) {
            if (values.isNullOrEmpty()) {
                return emptyList()
            }
            throw IllegalStateException("活动话题服务不可用")
        }
        return topics.normalize(values)
    }

    private fun validateTimeOrder(
        registrationStart: LocalDateTime?,
        registrationEnd: LocalDateTime?,
        start: LocalDateTime?,
        end: LocalDateTime?
    ) {
        if (registrationStart != null && registrationEnd != null
            && !registrationStart.isBefore(registrationEnd)
        ) {
            throw validation()
        }
        if (registrationEnd != null && start != null
            && registrationEnd.isAfter(start)
        ) {
            throw validation()
        }
        if (start != null && end != null && !start.isBefore(end)) {
            throw validation()
        }
    }

    private fun trimNullable(value: String?): String? {
        if (value == null) {
            return null
        }
        val normalized = value.trim()
        return normalized.ifEmpty { null }
    }

    private fun encrypt(value: String?): ByteArray? {
        if (value == null) {
            return null
        }
        if (sensitiveDataCodec == null) {
            throw IllegalStateException("敏感数据编码器不可用")
        }
        return sensitiveDataCodec.encrypt(value).toByteArray(StandardCharsets.UTF_8)
    }

    private fun decrypt(value: ByteArray?): String? {
        if (value == null) {
            return null
        }
        if (sensitiveDataCodec == null) {
            return null
        }
        return sensitiveDataCodec.decrypt(String(value, StandardCharsets.UTF_8))
    }

    private fun codePointLength(value: String): Int =
        value.codePointCount(0, value.length)

    private fun local(value: Instant?): LocalDateTime? =
        value?.let { LocalDateTime.ofInstant(it, ZoneOffset.UTC) }

    private fun now(): LocalDateTime =
        LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)

    private fun instant(value: LocalDateTime?): Instant? =
        value?.toInstant(ZoneOffset.UTC)

    private fun image(fileId: Long?): PublicImageView? =
        fileId?.let {
            PublicImageView(
                it.toString(),
                "/api/v1/files/$it/content"
            )
        }

    private fun contactQr(activityId: Long, fileId: Long?): PublicImageView? =
        fileId?.let {
            PublicImageView(
                it.toString(),
                "/api/v1/activities/$activityId/organizer/wechat-qr"
            )
        }

    private fun registrationGenderName(code: Int?): String? {
        if (code == null || code == 1) {
            return "UNLIMITED"
        }
        return when (code) {
            2 -> "MALE"
            3 -> "FEMALE"
            else -> throw validation()
        }
    }

    private fun statusName(status: Int?): String = when (status) {
        DRAFT -> "DRAFT"
        PUBLISHED -> "PUBLISHED"
        CANCELLED -> "CANCELLED"
        ENDED -> "ENDED"
        HIDDEN -> "HIDDEN"
        else -> throw validation()
    }

    private fun validation(): BusinessException =
        BusinessException(CommonErrorCode.VALIDATION_FAILED)

    private fun notFound(): BusinessException =
        BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND)

    private fun fileConflict(): BusinessException =
        BusinessException(AccountUserFileErrorCode.FILE_STATE_CONFLICT)

    private data class NormalizedActivity(
        val title: String,
        val categoryCode: String?,
        val description: String?,
        val coverFileId: Long?,
        val mediaFileIds: List<Long>,
        val registrationStartsAt: LocalDateTime?,
        val registrationEndsAt: LocalDateTime?,
        val startsAt: LocalDateTime?,
        val endsAt: LocalDateTime?,
        val regionCode: String?,
        val addressDetail: String?,
        val latitude: BigDecimal?,
        val longitude: BigDecimal?,
        val capacity: Int?,
        val signupDetails: String?,
        val organizerMessage: String?,
        val registrationGender: Int,
        val organizerPhone: String?,
        val organizerWechat: String?,
        val organizerWechatQrFileId: Long?,
        val placeName: String?,
        val coordinateSystem: String?,
        val topics: List<String>,
        val topicsSpecified: Boolean
    ) {
        val fileIds: List<Long>
            get() {
                val result = ArrayList<Long>()
                if (coverFileId != null) {
                    result.add(coverFileId)
                }
                result.addAll(mediaFileIds)
                return result.toList()
            }
    }

    private data class CreateFingerprints(
        val withTopics: String,
        val preTopics: String,
        val preCoordinateSystem: String,
        val coordinatesOnly: String,
        val legacy: String?,
        val hasPlaceName: Boolean,
        val topicsSpecified: Boolean,
        val hasTopics: Boolean
    ) {
        val canonical: String
            get() {
                if (topicsSpecified) {
                    return withTopics
                }
                if (hasPlaceName) {
                    return preTopics
                }
                return legacy ?: coordinatesOnly
            }

        fun matches(stored: String?): Boolean {
            if (withTopics == stored) {
                return true
            }
            if (hasTopics) {
                return false
            }
            return preTopics == stored
                || preCoordinateSystem == stored
                || !hasPlaceName && (
                    coordinatesOnly == stored
                        || legacy != null && legacy == stored
                )
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(DefaultActivityCommandService::class.java)
        private const val DRAFT = 1
        private const val PUBLISHED = 2
        private const val CANCELLED = 3
        private const val ENDED = 4
        private const val HIDDEN = 5
        private const val MAX_TITLE_LENGTH = 20
        private val MIN_LATITUDE = BigDecimal("-90.0000000")
        private val MAX_LATITUDE = BigDecimal("90.0000000")
        private val MIN_LONGITUDE = BigDecimal("-180.0000000")
        private val MAX_LONGITUDE = BigDecimal("180.0000000")
        private val IDEMPOTENCY_TTL = Duration.ofHours(24)
        private val CATEGORIES = setOf(
            "HIKING", "CAMPING", "MOUNTAINEERING", "RUNNING", "CYCLING",
            "FITNESS", "BALL_SPORTS", "WATER_SPORTS", "TRAVEL", "FOOD",
            "MUSIC", "MOVIE", "READING", "PHOTOGRAPHY", "BOARD_GAMES",
            "GAMING", "PETS", "PARENT_CHILD", "VOLUNTEERING", "OTHER"
        )
    }
}
