package com.eligo.server.activity.service

import com.eligo.server.activity.mapper.ActivityManagedDetailRow
import com.eligo.server.activity.mapper.ActivityManagedRow
import com.eligo.server.activity.mapper.ActivityMapRow
import com.eligo.server.activity.mapper.ActivityPublicRow
import com.eligo.server.activity.mapper.ActivityReadMapper
import com.eligo.server.activity.vo.ActivityMapItemView
import com.eligo.server.activity.vo.ActivityMapView
import com.eligo.server.activity.vo.ActivityOwnerSummaryView
import com.eligo.server.activity.vo.ManagedActivityDetailView
import com.eligo.server.activity.vo.ManagedActivitySummaryView
import com.eligo.server.activity.vo.PublicActivityDetailView
import com.eligo.server.activity.vo.PublicActivitySummaryView
import com.eligo.server.activity.vo.PublicImageView
import com.eligo.server.common.api.CursorPage
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.organization.mapper.OrganizationMapper
import com.eligo.server.participation.service.ParticipationReadService
import com.eligo.server.profile.service.RegionCatalog
import com.eligo.server.security.SensitiveDataCodec
import com.eligo.server.security.UserPrincipal
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.time.Clock
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test")
class DefaultActivityReadService(
    private val mapper: ActivityReadMapper,
    private val regionCatalog: RegionCatalog?,
    private val organizations: OrganizationMapper,
    private val participations: ParticipationReadService,
    private val sensitiveDataCodec: SensitiveDataCodec? = null,
    private val topics: ActivityTopicService? = null,
    private val clock: Clock = Clock.systemUTC()
) : ActivityReadService {

    @Transactional(readOnly = true)
    override fun listPublicActivities(
        cursor: String?,
        limit: Int,
        status: String?,
        categoryCode: String?,
        regionCode: String?
    ): CursorPage<PublicActivitySummaryView> = listPublicActivities(
        cursor, limit, status, categoryCode, regionCode, null
    )

    @Transactional(readOnly = true)
    override fun listPublicActivities(
        cursor: String?,
        limit: Int,
        status: String?,
        categoryCode: String?,
        regionCode: String?,
        keyword: String?
    ): CursorPage<PublicActivitySummaryView> = listPublicActivities(
        cursor, limit, status, categoryCode, regionCode, keyword,
        null, null, null, null
    )

    @Transactional(readOnly = true)
    override fun listPublicActivities(
        cursor: String?,
        limit: Int,
        status: String?,
        categoryCode: String?,
        regionCode: String?,
        keyword: String?,
        userLatitude: BigDecimal?,
        userLongitude: BigDecimal?,
        radiusMeters: Int?
    ): CursorPage<PublicActivitySummaryView> = listPublicActivities(
        cursor, limit, status, categoryCode, regionCode, keyword,
        null, userLatitude, userLongitude, radiusMeters
    )

    @Transactional(readOnly = true)
    override fun listPublicActivities(
        cursor: String?,
        limit: Int,
        status: String?,
        categoryCode: String?,
        regionCode: String?,
        keyword: String?,
        topic: String?,
        userLatitude: BigDecimal?,
        userLongitude: BigDecimal?,
        radiusMeters: Int?
    ): CursorPage<PublicActivitySummaryView> {
        if (limit < 1 || limit > 50) {
            throw validation()
        }
        val statusCode = publicStatus(status)
        val category = category(categoryCode)
        val region = region(regionCode)
        val normalizedKeyword = keyword(keyword)
        val normalizedTopic = normalizeTopicFilter(topic)
        val nearby = userLatitude != null || userLongitude != null
        if ((userLatitude == null) != (userLongitude == null)) {
            throw validation()
        }
        if (!nearby && radiusMeters != null) {
            throw validation()
        }
        if (radiusMeters != null && (radiusMeters < 1 || radiusMeters > 50000)) {
            throw validation()
        }
        val normalizedUserLatitude = if (nearby) {
            coordinateBound(userLatitude, MIN_LATITUDE, MAX_LATITUDE)
        } else null
        val normalizedUserLongitude = if (nearby) {
            coordinateBound(userLongitude, MIN_LONGITUDE, MAX_LONGITUDE)
        } else null
        val distanceBounds = if (nearby) {
            distanceBounds(normalizedUserLatitude, normalizedUserLongitude, radiusMeters)
        } else {
            GeoBounds.NONE
        }
        val now = now()
        val filterKey = nearbyFilterKey(
            statusCode, category, region, normalizedKeyword, normalizedTopic,
            normalizedUserLatitude, normalizedUserLongitude, radiusMeters
        )
        val nearbyCursor = if (nearby) decodeNearbyCursor(cursor, filterKey) else null
        val ordinaryCursor = if (nearby) null else decodeCursor(cursor)
        val rows = when {
            nearby && normalizedTopic == null -> mapper.findPublicPageByDistance(
                statusCode, category, region, normalizedKeyword,
                normalizedUserLatitude!!, normalizedUserLongitude!!,
                distanceBounds.minLatitude, distanceBounds.maxLatitude,
                distanceBounds.minLongitude, distanceBounds.maxLongitude,
                radiusMeters, nearbyCursor!!.distanceMeters, nearbyCursor.activityId,
                now, limit + 1
            )
            nearby -> mapper.findPublicPageByDistance(
                statusCode, category, region, normalizedKeyword, normalizedTopic,
                normalizedUserLatitude!!, normalizedUserLongitude!!,
                distanceBounds.minLatitude, distanceBounds.maxLatitude,
                distanceBounds.minLongitude, distanceBounds.maxLongitude,
                radiusMeters, nearbyCursor!!.distanceMeters, nearbyCursor.activityId,
                now, limit + 1
            )
            normalizedTopic == null -> mapper.findPublicPage(
                statusCode, category, region, normalizedKeyword,
                ordinaryCursor!!.startsAt, ordinaryCursor.activityId,
                now, limit + 1
            )
            else -> mapper.findPublicPage(
                statusCode, category, region, normalizedKeyword, normalizedTopic,
                ordinaryCursor!!.startsAt, ordinaryCursor.activityId,
                now, limit + 1
            )
        }
        val hasMore = rows.size > limit
        val pageRows = rows.subList(0, minOf(rows.size, limit))
        val topicMap = topicMap(pageRows.map { it.activityId!! })
        val items = pageRows.map { row ->
            summary(row, now, topicMap[row.activityId] ?: emptyList())
        }
        val nextCursor = if (hasMore && pageRows.isNotEmpty()) {
            if (nearby) {
                encodeNearbyCursor(pageRows[pageRows.size - 1], filterKey)
            } else {
                encodeCursor(pageRows[pageRows.size - 1])
            }
        } else null
        return CursorPage(items, nextCursor, hasMore)
    }

    @Transactional(readOnly = true)
    override fun listActivitiesOnMap(
        minLatitude: BigDecimal?,
        maxLatitude: BigDecimal?,
        minLongitude: BigDecimal?,
        maxLongitude: BigDecimal?,
        categoryCode: String?,
        limit: Int
    ): ActivityMapView = listActivitiesOnMap(
        minLatitude, maxLatitude, minLongitude, maxLongitude,
        null, null, null, categoryCode, limit
    )

    @Transactional(readOnly = true)
    override fun listActivitiesOnMap(
        minLatitude: BigDecimal?,
        maxLatitude: BigDecimal?,
        minLongitude: BigDecimal?,
        maxLongitude: BigDecimal?,
        userLatitude: BigDecimal?,
        userLongitude: BigDecimal?,
        radiusMeters: Int?,
        categoryCode: String?,
        limit: Int
    ): ActivityMapView {
        if (limit < 1 || limit > 200) {
            throw validation()
        }
        val anyBounds = minLatitude != null || maxLatitude != null
            || minLongitude != null || maxLongitude != null
        val allBounds = minLatitude != null && maxLatitude != null
            && minLongitude != null && maxLongitude != null
        if (anyBounds != allBounds) {
            throw validation()
        }
        val anyUserCoordinates = userLatitude != null || userLongitude != null
        val allUserCoordinates = userLatitude != null && userLongitude != null
        if (anyUserCoordinates != allUserCoordinates) {
            throw validation()
        }
        if (allBounds && radiusMeters != null) {
            throw validation()
        }
        if (!allBounds && (!allUserCoordinates || radiusMeters == null)) {
            throw validation()
        }
        if (radiusMeters != null && (radiusMeters < 1 || radiusMeters > 50000)) {
            throw validation()
        }
        val normalizedMinLatitude = if (allBounds) {
            coordinateBound(minLatitude, MIN_LATITUDE, MAX_LATITUDE)
        } else null
        val normalizedMaxLatitude = if (allBounds) {
            coordinateBound(maxLatitude, MIN_LATITUDE, MAX_LATITUDE)
        } else null
        val normalizedMinLongitude = if (allBounds) {
            coordinateBound(minLongitude, MIN_LONGITUDE, MAX_LONGITUDE)
        } else null
        val normalizedMaxLongitude = if (allBounds) {
            coordinateBound(maxLongitude, MIN_LONGITUDE, MAX_LONGITUDE)
        } else null
        if (allBounds && (normalizedMinLatitude!!.compareTo(normalizedMaxLatitude) >= 0
            || normalizedMinLongitude!!.compareTo(normalizedMaxLongitude) >= 0)
        ) {
            throw validation()
        }
        val normalizedUserLatitude = if (allUserCoordinates) {
            coordinateBound(userLatitude, MIN_LATITUDE, MAX_LATITUDE)
        } else null
        val normalizedUserLongitude = if (allUserCoordinates) {
            coordinateBound(userLongitude, MIN_LONGITUDE, MAX_LONGITUDE)
        } else null
        val queryBounds = if (allBounds) {
            GeoBounds(
                normalizedMinLatitude!!, normalizedMaxLatitude!!,
                normalizedMinLongitude!!, normalizedMaxLongitude!!
            )
        } else {
            distanceBounds(normalizedUserLatitude, normalizedUserLongitude, radiusMeters)
        }
        val category = category(categoryCode)
        val now = now()
        val rows = if (allUserCoordinates) {
            mapper.findMapItemsByDistance(
                queryBounds.minLatitude, queryBounds.maxLatitude,
                queryBounds.minLongitude, queryBounds.maxLongitude,
                normalizedUserLatitude!!, normalizedUserLongitude!!,
                radiusMeters, category, now, limit + 1
            )
        } else {
            mapper.findMapItems(
                queryBounds.minLatitude!!, queryBounds.maxLatitude!!,
                queryBounds.minLongitude!!, queryBounds.maxLongitude!!,
                category, now, limit + 1
            )
        }
        val truncated = rows.size > limit
        val pageRows = rows.subList(0, minOf(rows.size, limit))
        val topicMap = topicMap(pageRows.map { it.activityId!! })
        val items = pageRows.map { row ->
            mapItem(row, now, topicMap[row.activityId] ?: emptyList())
        }
        return ActivityMapView(items, truncated)
    }

    @Transactional(readOnly = true)
    override fun listManagedActivities(
        principal: UserPrincipal?,
        cursor: String?,
        limit: Int,
        status: String?,
        ownerType: String?
    ): CursorPage<ManagedActivitySummaryView> {
        if (principal == null) {
            throw BusinessException(CommonErrorCode.AUTHENTICATION_REQUIRED)
        }
        if (limit < 1 || limit > 50) {
            throw validation()
        }
        val statusCode = managedStatus(status)
        val normalizedOwnerType = ownerType(ownerType)
        val organizationId = ownedOrganizationId(principal.userId)
        val decoded = decodeManagedCursor(cursor)
        val rows = mapper.findManagedPage(
            principal.userId,
            organizationId,
            statusCode,
            normalizedOwnerType,
            decoded.updatedAt,
            decoded.activityId,
            limit + 1
        )
        val hasMore = rows.size > limit
        val pageRows = rows.subList(0, minOf(rows.size, limit))
        val topicMap = topicMap(pageRows.map { it.activityId!! })
        val items = pageRows.map { row ->
            managedSummary(row, topicMap[row.activityId] ?: emptyList())
        }
        val nextCursor = if (hasMore && pageRows.isNotEmpty()) {
            encodeManagedCursor(pageRows[pageRows.size - 1])
        } else null
        return CursorPage(items, nextCursor, hasMore)
    }

    @Transactional(readOnly = true)
    override fun getManagedActivity(
        principal: UserPrincipal?,
        activityId: Long
    ): ManagedActivityDetailView {
        if (principal == null) {
            throw BusinessException(CommonErrorCode.AUTHENTICATION_REQUIRED)
        }
        if (activityId <= 0) {
            throw validation()
        }
        val row = mapper.findManagedById(
            principal.userId,
            ownedOrganizationId(principal.userId),
            activityId
        ) ?: throw BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND)
        val media = mapper.findPublicMedia(activityId).map { item -> image(item.fileId!!)!! }
        return managedDetail(row, media, topicValues(activityId))
    }

    @Transactional(readOnly = true)
    override fun getPublicActivity(activityId: Long): PublicActivityDetailView =
        getPublicActivity(null, activityId)

    @Transactional(readOnly = true)
    override fun findPublicSummaries(
        activityIds: List<Long>?
    ): Map<Long, PublicActivitySummaryView> {
        if (activityIds.isNullOrEmpty()) {
            return emptyMap()
        }
        val distinctIds = activityIds.filter { it > 0 }.distinct()
        if (distinctIds.isEmpty()) {
            return emptyMap()
        }
        val rows = mapper.findPublicSummariesByIds(distinctIds)
        val topicMap = topicMap(rows.map { it.activityId!! })
        val now = now()
        val result = LinkedHashMap<Long, PublicActivitySummaryView>()
        for (row in rows) {
            result[row.activityId!!] = summary(
                row, now, topicMap[row.activityId] ?: emptyList()
            )
        }
        return result.toMap()
    }

    @Transactional(readOnly = true)
    override fun getPublicActivity(
        principal: UserPrincipal?,
        activityId: Long
    ): PublicActivityDetailView {
        if (activityId <= 0) {
            throw validation()
        }
        val row = mapper.findPublicById(activityId)
            ?: throw BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND)
        val media = mapper.findPublicMedia(activityId).map { item -> image(item.fileId!!)!! }
        val summary = summary(row, now(), topicValues(activityId))
        val viewerIsOwner = viewerIsOwner(principal, row)
        val myParticipationStatus = if (viewerIsOwner) {
            null
        } else {
            myParticipationStatus(principal, activityId)
        }
        val contactVisible = viewerIsOwner || "ACTIVE" == myParticipationStatus
        return PublicActivityDetailView(
            summary.activityId,
            summary.status,
            summary.title,
            summary.categoryCode,
            summary.cover,
            summary.owner,
            summary.startsAt,
            summary.endsAt,
            summary.regionCode,
            summary.addressDetail,
            summary.latitude,
            summary.longitude,
            summary.capacity,
            summary.participantCount,
            summary.registrationStatus,
            summary.feeType,
            row.description,
            media,
            instant(row.registrationStartsAt),
            instant(row.registrationEndsAt),
            if (isOrganization(row)) null else row.signupDetails,
            if (isOrganization(row)) null else row.organizerMessage,
            myParticipationStatus,
            viewerIsOwner,
            instant(row.publishedAt),
            instant(row.updatedAt),
            registrationGenderName(row.registrationGender),
            if (contactVisible) decrypt(row.organizerPhoneCiphertext) else null,
            if (contactVisible) decrypt(row.organizerWechatCiphertext) else null,
            if (contactVisible) contactQr(activityId, row.organizerWechatQrFileId) else null,
            null,
            row.placeName,
            coordinateSystem(row.latitude, row.longitude),
            row.distanceMeters,
            summary.topics
        )
    }

    private fun viewerIsOwner(principal: UserPrincipal?, row: ActivityPublicRow): Boolean {
        if (principal == null) {
            return false
        }
        return if ("USER" == row.ownerType) {
            row.ownerId == principal.userId
        } else {
            ownedOrganizationId(principal.userId) == row.ownerId
        }
    }

    private fun myParticipationStatus(principal: UserPrincipal?, activityId: Long): String? {
        if (principal == null) {
            return null
        }
        return participations.findStatus(activityId, principal.userId).orElse(null)
    }

    private fun summary(row: ActivityPublicRow, now: LocalDateTime): PublicActivitySummaryView =
        summary(row, now, topicValues(row.activityId!!))

    private fun summary(
        row: ActivityPublicRow,
        now: LocalDateTime,
        topicValues: List<String>
    ): PublicActivitySummaryView {
        val effectiveStatus = effectivePublicStatus(row, now)
        return PublicActivitySummaryView(
            row.activityId.toString(),
            statusName(effectiveStatus),
            row.title ?: "",
            row.categoryCode,
            image(row.coverFileId),
            owner(row),
            instant(row.startsAt),
            instant(row.endsAt),
            row.regionCode,
            row.addressDetail,
            row.latitude,
            row.longitude,
            row.capacity,
            row.participantCount,
            registrationStatus(row, effectiveStatus, now),
            "FREE",
            row.placeName,
            coordinateSystem(row.latitude, row.longitude),
            row.distanceMeters,
            topicValues
        )
    }

    private fun owner(row: ActivityPublicRow): ActivityOwnerSummaryView =
        ActivityOwnerSummaryView(
            row.ownerType ?: "",
            row.ownerId.toString(),
            row.ownerDisplayName ?: "",
            image(row.ownerAvatarFileId)
        )

    private fun managedSummary(
        row: ActivityManagedRow,
        topicValues: List<String>
    ): ManagedActivitySummaryView = ManagedActivitySummaryView(
        row.activityId.toString(),
        statusName(row.status),
        row.title ?: "",
        row.categoryCode,
        image(row.coverFileId),
        owner(row),
        instant(row.startsAt),
        instant(row.endsAt),
        row.regionCode,
        row.addressDetail,
        row.latitude,
        row.longitude,
        row.capacity,
        row.participantCount,
        "FREE",
        row.version,
        instant(row.updatedAt),
        row.placeName,
        coordinateSystem(row.latitude, row.longitude),
        topicValues
    )

    private fun owner(row: ActivityManagedRow): ActivityOwnerSummaryView =
        ActivityOwnerSummaryView(
            row.ownerType ?: "",
            row.ownerId.toString(),
            row.ownerDisplayName ?: "",
            image(row.ownerAvatarFileId)
        )

    private fun managedDetail(
        row: ActivityManagedDetailRow,
        media: List<PublicImageView>,
        topicValues: List<String>
    ): ManagedActivityDetailView {
        val organization = "ORGANIZATION" == row.ownerType
        return ManagedActivityDetailView(
            row.activityId.toString(),
            statusName(row.status),
            row.title ?: "",
            row.categoryCode,
            image(row.coverFileId),
            owner(row),
            instant(row.startsAt),
            instant(row.endsAt),
            row.regionCode,
            row.addressDetail,
            row.latitude,
            row.longitude,
            row.capacity,
            row.participantCount,
            "FREE",
            row.version,
            instant(row.updatedAt),
            row.description,
            media,
            instant(row.registrationStartsAt),
            instant(row.registrationEndsAt),
            if (organization) null else row.signupDetails,
            if (organization) null else row.organizerMessage,
            instant(row.createdAt),
            instant(row.publishedAt),
            registrationGenderName(row.registrationGender),
            decrypt(row.organizerPhoneCiphertext),
            decrypt(row.organizerWechatCiphertext),
            contactQr(row.activityId!!, row.organizerWechatQrFileId),
            null,
            row.placeName,
            coordinateSystem(row.latitude, row.longitude),
            topicValues
        )
    }

    private fun owner(row: ActivityManagedDetailRow): ActivityOwnerSummaryView =
        ActivityOwnerSummaryView(
            row.ownerType ?: "",
            row.ownerId.toString(),
            row.ownerDisplayName ?: "",
            image(row.ownerAvatarFileId)
        )

    private fun image(fileId: Long?): PublicImageView? =
        fileId?.let {
            PublicImageView(
                it.toString(),
                "/api/v1/files/$it/content"
            )
        }

    private fun mapItem(
        row: ActivityMapRow,
        now: LocalDateTime,
        topicValues: List<String>
    ): ActivityMapItemView = ActivityMapItemView(
        row.activityId.toString(),
        row.title ?: "",
        row.categoryCode,
        image(row.coverFileId),
        instant(row.startsAt),
        instant(row.endsAt),
        row.regionCode,
        row.addressDetail,
        row.latitude,
        row.longitude,
        registrationWindowStatus(
            row.registrationStartsAt,
            row.registrationEndsAt,
            row.capacity,
            row.participantCount,
            now
        ),
        "FREE",
        row.placeName,
        coordinateSystem(row.latitude, row.longitude),
        row.distanceMeters,
        topicValues
    )

    private fun coordinateSystem(latitude: BigDecimal?, longitude: BigDecimal?): String? =
        if (latitude != null && longitude != null) "GCJ-02" else null

    private fun contactQr(activityId: Long, fileId: Long?): PublicImageView? =
        fileId?.let {
            PublicImageView(
                it.toString(),
                "/api/v1/activities/$activityId/organizer/wechat-qr"
            )
        }

    private fun decrypt(ciphertext: ByteArray?): String? {
        if (ciphertext == null || sensitiveDataCodec == null) {
            return null
        }
        return sensitiveDataCodec.decrypt(String(ciphertext, StandardCharsets.UTF_8))
    }

    private fun registrationGenderName(code: Int?): String? {
        if (code == null || code == 1) {
            return "UNLIMITED"
        }
        return when (code) {
            2 -> "MALE"
            3 -> "FEMALE"
            else -> throw BusinessException(CommonErrorCode.VALIDATION_FAILED)
        }
    }

    private fun registrationStatus(
        row: ActivityPublicRow,
        effectiveStatus: Int,
        now: LocalDateTime
    ): String = when (effectiveStatus) {
        CANCELLED -> "CANCELLED"
        ENDED -> "ENDED"
        PUBLISHED -> registrationWindowStatus(row, now)
        else -> throw BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND)
    }

    private fun registrationWindowStatus(
        row: ActivityPublicRow,
        now: LocalDateTime
    ): String = registrationWindowStatus(
        row.registrationStartsAt,
        row.registrationEndsAt,
        row.capacity,
        row.participantCount,
        now
    )

    private fun registrationWindowStatus(
        registrationStartsAt: LocalDateTime?,
        registrationEndsAt: LocalDateTime?,
        capacity: Int?,
        participantCount: Int?,
        now: LocalDateTime
    ): String {
        if (now.isBefore(registrationStartsAt)) {
            return "NOT_STARTED"
        }
        if (!now.isBefore(registrationEndsAt)) {
            return "CLOSED"
        }
        if (capacity != null
            && participantCount != null
            && participantCount >= capacity
        ) {
            return "FULL"
        }
        return "OPEN"
    }

    private fun coordinateBound(
        value: BigDecimal?,
        minimum: BigDecimal,
        maximum: BigDecimal
    ): BigDecimal {
        if (value == null) {
            throw validation()
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

    private fun effectivePublicStatus(row: ActivityPublicRow, now: LocalDateTime): Int {
        if (row.status == PUBLISHED
            && row.endsAt != null
            && !now.isBefore(row.endsAt)
        ) {
            return ENDED
        }
        return row.status!!
    }

    private fun now(): LocalDateTime =
        LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)

    private fun isOrganization(row: ActivityPublicRow): Boolean =
        "ORGANIZATION" == row.ownerType

    private fun publicStatus(value: String?): Int {
        if (value == null) {
            return PUBLISHED
        }
        return when (value.trim().uppercase(Locale.ROOT)) {
            "PUBLISHED" -> PUBLISHED
            "CANCELLED" -> CANCELLED
            "ENDED" -> ENDED
            else -> throw validation()
        }
    }

    private fun managedStatus(value: String?): Int? {
        if (value == null) {
            return null
        }
        return when (value.trim().uppercase(Locale.ROOT)) {
            "DRAFT" -> 1
            "PUBLISHED" -> PUBLISHED
            "CANCELLED" -> CANCELLED
            "ENDED" -> ENDED
            "HIDDEN" -> 5
            else -> throw validation()
        }
    }

    private fun ownerType(value: String?): String? {
        if (value == null) {
            return null
        }
        return when (val normalized = value.trim().uppercase(Locale.ROOT)) {
            "USER", "ORGANIZATION" -> normalized
            else -> throw validation()
        }
    }

    private fun ownedOrganizationId(userId: Long): Long? {
        if (organizations == null) {
            return null
        }
        return organizations.findActiveOwnedByUserId(userId)
            .firstOrNull()
            ?.id
    }

    private fun category(value: String?): String? {
        if (value == null) {
            return null
        }
        val normalized = value.trim().uppercase(Locale.ROOT)
        if (normalized !in CATEGORIES) {
            throw validation()
        }
        return normalized
    }

    private fun region(value: String?): String? {
        if (value == null) {
            return null
        }
        val normalized = value.trim()
        if (!normalized.matches(Regex("[0-9]{6}([0-9]{3})?"))) {
            throw validation()
        }
        if (regionCatalog != null && !regionCatalog.isDistrictCode(normalized)) {
            throw validation()
        }
        return normalized
    }

    private fun keyword(value: String?): String? {
        if (value == null) {
            return null
        }
        val normalized = value.trim()
        if (normalized.isEmpty()) {
            return null
        }
        if (normalized.codePointCount(0, normalized.length) > 20) {
            throw validation()
        }
        return normalized
    }

    private fun normalizeTopicFilter(value: String?): String? {
        if (topics != null) {
            return topics.normalizeFilter(value)
        }
        if (value.isNullOrBlank()) {
            return null
        }
        val normalized = value.trim()
        if (normalized.codePointCount(0, normalized.length) > 20) {
            throw validation()
        }
        return normalized.lowercase(Locale.ROOT)
    }

    private fun topicMap(activityIds: List<Long>): Map<Long, List<String>> {
        if (topics == null || activityIds.isEmpty()) {
            return emptyMap()
        }
        return topics.findByActivityIds(activityIds) ?: emptyMap()
    }

    private fun topicValues(activityId: Long): List<String> {
        if (topics == null) {
            return emptyList()
        }
        return topics.findByActivityId(activityId) ?: emptyList()
    }

    private fun decodeCursor(value: String?): Cursor {
        if (value == null) {
            return Cursor(null, null)
        }
        if (value.isBlank() || value.length > MAX_CURSOR_LENGTH) {
            throw validation()
        }
        return try {
            val raw = String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
            val separator = raw.indexOf(':')
            if (separator <= 0 || separator == raw.length - 1
                || raw.indexOf(':', separator + 1) >= 0
            ) {
                throw IllegalArgumentException()
            }
            val epochMillis = raw.substring(0, separator).toLong()
            val activityId = raw.substring(separator + 1).toLong()
            if (activityId <= 0) {
                throw IllegalArgumentException()
            }
            val startsAt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC
            )
            Cursor(startsAt, activityId)
        } catch (exception: IllegalArgumentException) {
            throw validation()
        } catch (exception: DateTimeException) {
            throw validation()
        }
    }

    private fun decodeNearbyCursor(value: String?, expectedFilterKey: String?): NearbyCursor {
        if (value == null) {
            return NearbyCursor(null, null)
        }
        if (value.isBlank() || value.length > MAX_CURSOR_LENGTH) {
            throw validation()
        }
        return try {
            val raw = String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
            val parts = raw.split(":")
            if (parts.size != 4 || parts[0] != "D"
                || parts[3] != expectedFilterKey
            ) {
                throw IllegalArgumentException()
            }
            val distanceMeters = parts[1].toLong()
            val activityId = parts[2].toLong()
            if (distanceMeters < 0 || activityId <= 0) {
                throw IllegalArgumentException()
            }
            NearbyCursor(distanceMeters, activityId)
        } catch (exception: IllegalArgumentException) {
            throw validation()
        }
    }

    private fun decodeManagedCursor(value: String?): ManagedCursor {
        if (value == null) {
            return ManagedCursor(null, null)
        }
        if (value.isBlank() || value.length > MAX_CURSOR_LENGTH) {
            throw validation()
        }
        return try {
            val raw = String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
            val separator = raw.indexOf(':')
            if (separator <= 0 || separator == raw.length - 1
                || raw.indexOf(':', separator + 1) >= 0
            ) {
                throw IllegalArgumentException()
            }
            val epochMillis = raw.substring(0, separator).toLong()
            val activityId = raw.substring(separator + 1).toLong()
            if (activityId <= 0) {
                throw IllegalArgumentException()
            }
            val updatedAt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC
            )
            ManagedCursor(updatedAt, activityId)
        } catch (exception: IllegalArgumentException) {
            throw validation()
        } catch (exception: DateTimeException) {
            throw validation()
        }
    }

    private fun encodeCursor(row: ActivityPublicRow): String {
        val raw = row.startsAt!!.toInstant(ZoneOffset.UTC).toEpochMilli()
            .toString() + ":" + row.activityId
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
    }

    private fun encodeNearbyCursor(row: ActivityPublicRow, filterKey: String?): String {
        if (row.distanceMeters == null || row.distanceMeters < 0) {
            throw validation()
        }
        val raw = "D:" + row.distanceMeters + ":" + row.activityId + ":" + filterKey
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
    }

    private fun nearbyFilterKey(
        status: Int,
        category: String?,
        region: String?,
        keyword: String?,
        topic: String?,
        latitude: BigDecimal?,
        longitude: BigDecimal?,
        radiusMeters: Int?
    ): String? {
        if (latitude == null || longitude == null) {
            return null
        }
        val raw = status.toString() + "\u001f" + value(category) + "\u001f" + value(region) +
            "\u001f" + value(keyword) + "\u001f" + value(topic) +
            "\u001f" + latitude.toPlainString() +
            "\u001f" + longitude.toPlainString() + "\u001f" + value(radiusMeters)
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(raw.toByteArray(StandardCharsets.UTF_8))
            Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        } catch (exception: NoSuchAlgorithmException) {
            throw IllegalStateException(exception)
        }
    }

    private fun value(value: Any?): String = value?.toString() ?: ""

    private fun distanceBounds(
        latitude: BigDecimal?,
        longitude: BigDecimal?,
        radiusMeters: Int?
    ): GeoBounds {
        if (radiusMeters == null) {
            return GeoBounds.NONE
        }
        val centerLatitude = Math.toRadians(latitude!!.toDouble())
        val centerLongitude = Math.toRadians(longitude!!.toDouble())
        val angularDistance = radiusMeters.toDouble() / EARTH_RADIUS_METERS
        val minLatitude = max(-PI / 2, centerLatitude - angularDistance)
        val maxLatitude = min(PI / 2, centerLatitude + angularDistance)
        var minLongitude = -PI
        var maxLongitude = PI
        if (minLatitude > -PI / 2 && maxLatitude < PI / 2) {
            val longitudeRatio = sin(angularDistance) / cos(centerLatitude)
            if (abs(longitudeRatio) < 1) {
                val longitudeDelta = asin(longitudeRatio)
                val candidateMin = centerLongitude - longitudeDelta
                val candidateMax = centerLongitude + longitudeDelta
                if (candidateMin >= -PI && candidateMax <= PI) {
                    minLongitude = candidateMin
                    maxLongitude = candidateMax
                }
            }
        }
        return GeoBounds(
            outwardCoordinate(Math.toDegrees(minLatitude), RoundingMode.FLOOR),
            outwardCoordinate(Math.toDegrees(maxLatitude), RoundingMode.CEILING),
            outwardCoordinate(Math.toDegrees(minLongitude), RoundingMode.FLOOR),
            outwardCoordinate(Math.toDegrees(maxLongitude), RoundingMode.CEILING)
        )
    }

    private fun outwardCoordinate(value: Double, roundingMode: RoundingMode): BigDecimal =
        BigDecimal.valueOf(value).setScale(7, roundingMode)

    private fun encodeManagedCursor(row: ActivityManagedRow): String {
        val raw = row.updatedAt!!.toInstant(ZoneOffset.UTC).toEpochMilli()
            .toString() + ":" + row.activityId
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
    }

    private fun statusName(value: Int?): String = when (value) {
        1 -> "DRAFT"
        PUBLISHED -> "PUBLISHED"
        CANCELLED -> "CANCELLED"
        ENDED -> "ENDED"
        5 -> "HIDDEN"
        else -> throw BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND)
    }

    private fun instant(value: LocalDateTime?): Instant? =
        value?.toInstant(ZoneOffset.UTC)

    private fun validation(): BusinessException =
        BusinessException(CommonErrorCode.VALIDATION_FAILED)

    private data class Cursor(val startsAt: LocalDateTime?, val activityId: Long?)

    private data class NearbyCursor(val distanceMeters: Long?, val activityId: Long?)

    private data class GeoBounds(
        val minLatitude: BigDecimal?,
        val maxLatitude: BigDecimal?,
        val minLongitude: BigDecimal?,
        val maxLongitude: BigDecimal?
    ) {
        companion object {
            val NONE = GeoBounds(null, null, null, null)
        }
    }

    private data class ManagedCursor(val updatedAt: LocalDateTime?, val activityId: Long?)

    companion object {
        private const val PUBLISHED = 2
        private const val CANCELLED = 3
        private const val ENDED = 4
        private const val MAX_CURSOR_LENGTH = 512
        private val MIN_LATITUDE = BigDecimal("-90")
        private val MAX_LATITUDE = BigDecimal("90")
        private val MIN_LONGITUDE = BigDecimal("-180")
        private val MAX_LONGITUDE = BigDecimal("180")
        private const val EARTH_RADIUS_METERS = 6_371_000.0
        private val CATEGORIES = setOf(
            "HIKING", "CAMPING", "MOUNTAINEERING", "RUNNING", "CYCLING",
            "FITNESS", "BALL_SPORTS", "WATER_SPORTS", "TRAVEL", "FOOD",
            "MUSIC", "MOVIE", "READING", "PHOTOGRAPHY", "BOARD_GAMES",
            "GAMING", "PETS", "PARENT_CHILD", "VOLUNTEERING", "OTHER"
        )
    }
}
