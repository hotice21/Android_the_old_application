package com.eligo.server.activity.service

import com.eligo.server.activity.vo.ActivityMapView
import com.eligo.server.activity.vo.ManagedActivityDetailView
import com.eligo.server.activity.vo.ManagedActivitySummaryView
import com.eligo.server.activity.vo.PublicActivityDetailView
import com.eligo.server.activity.vo.PublicActivitySummaryView
import com.eligo.server.common.api.CursorPage
import com.eligo.server.security.UserPrincipal
import java.math.BigDecimal

interface ActivityReadService {

    fun listPublicActivities(
        cursor: String?,
        limit: Int,
        status: String?,
        categoryCode: String?,
        regionCode: String?
    ): CursorPage<PublicActivitySummaryView>

    fun listPublicActivities(
        cursor: String?,
        limit: Int,
        status: String?,
        categoryCode: String?,
        regionCode: String?,
        keyword: String?
    ): CursorPage<PublicActivitySummaryView> =
        listPublicActivities(cursor, limit, status, categoryCode, regionCode)

    fun listPublicActivities(
        cursor: String?,
        limit: Int,
        status: String?,
        categoryCode: String?,
        regionCode: String?,
        keyword: String?,
        userLatitude: BigDecimal?,
        userLongitude: BigDecimal?,
        radiusMeters: Int?
    ): CursorPage<PublicActivitySummaryView> =
        listPublicActivities(cursor, limit, status, categoryCode, regionCode, keyword)

    fun listPublicActivities(
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
    ): CursorPage<PublicActivitySummaryView> =
        listPublicActivities(
            cursor, limit, status, categoryCode, regionCode, keyword,
            userLatitude, userLongitude, radiusMeters
        )

    fun listActivitiesOnMap(
        minLatitude: BigDecimal?,
        maxLatitude: BigDecimal?,
        minLongitude: BigDecimal?,
        maxLongitude: BigDecimal?,
        categoryCode: String?,
        limit: Int
    ): ActivityMapView

    fun listActivitiesOnMap(
        minLatitude: BigDecimal?,
        maxLatitude: BigDecimal?,
        minLongitude: BigDecimal?,
        maxLongitude: BigDecimal?,
        userLatitude: BigDecimal?,
        userLongitude: BigDecimal?,
        radiusMeters: Int?,
        categoryCode: String?,
        limit: Int
    ): ActivityMapView =
        listActivitiesOnMap(
            minLatitude, maxLatitude, minLongitude, maxLongitude,
            categoryCode, limit
        )

    fun getPublicActivity(activityId: Long): PublicActivityDetailView

    fun findPublicSummaries(activityIds: List<Long>?): Map<Long, PublicActivitySummaryView>

    fun getPublicActivity(
        principal: UserPrincipal?,
        activityId: Long
    ): PublicActivityDetailView = getPublicActivity(activityId)

    fun getManagedActivity(
        principal: UserPrincipal?,
        activityId: Long
    ): ManagedActivityDetailView

    fun listManagedActivities(
        principal: UserPrincipal?,
        cursor: String?,
        limit: Int,
        status: String?,
        ownerType: String?
    ): CursorPage<ManagedActivitySummaryView>
}
