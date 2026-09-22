package com.eligo.server.activity.mapper

import java.math.BigDecimal
import java.time.LocalDateTime
import org.apache.ibatis.annotations.Arg
import org.apache.ibatis.annotations.ConstructorArgs
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select

@Mapper
interface ActivityReadMapper {

    @ConstructorArgs(
        Arg(column = "activity_id", javaType = Long::class, id = true),
        Arg(column = "title", javaType = String::class),
        Arg(column = "category_code", javaType = String::class),
        Arg(column = "cover_file_id", javaType = Long::class),
        Arg(column = "registration_starts_at", javaType = LocalDateTime::class),
        Arg(column = "registration_ends_at", javaType = LocalDateTime::class),
        Arg(column = "starts_at", javaType = LocalDateTime::class),
        Arg(column = "ends_at", javaType = LocalDateTime::class),
        Arg(column = "region_code", javaType = String::class),
        Arg(column = "address_detail", javaType = String::class),
        Arg(column = "latitude", javaType = BigDecimal::class),
        Arg(column = "longitude", javaType = BigDecimal::class),
        Arg(column = "capacity", javaType = Int::class),
        Arg(column = "participant_count", javaType = Int::class),
        Arg(column = "place_name", javaType = String::class)
    )
    @Select("""
            SELECT a.id AS activity_id,
                   a.title,
                   a.category_code,
                   a.cover_file_id,
                   a.registration_starts_at,
                   a.registration_ends_at,
                   a.starts_at,
                   a.ends_at,
                   a.region_code,
                   a.address_detail,
                   a.latitude,
                   a.longitude,
                   a.capacity,
                   a.participant_count,
                   a.place_name
              FROM activities a
             WHERE a.status=2
               AND a.ends_at>#{now}
               AND a.latitude IS NOT NULL
               AND a.longitude IS NOT NULL
               AND a.latitude BETWEEN #{minLatitude} AND #{maxLatitude}
               AND a.longitude BETWEEN #{minLongitude} AND #{maxLongitude}
               AND (#{categoryCode} IS NULL OR a.category_code=#{categoryCode})
             ORDER BY a.starts_at ASC, a.id ASC
             LIMIT #{limit}
            """)
    fun findMapItems(
        @Param("minLatitude") minLatitude: BigDecimal,
        @Param("maxLatitude") maxLatitude: BigDecimal,
        @Param("minLongitude") minLongitude: BigDecimal,
        @Param("maxLongitude") maxLongitude: BigDecimal,
        @Param("categoryCode") categoryCode: String?,
        @Param("now") now: LocalDateTime,
        @Param("limit") limit: Int
    ): List<ActivityMapRow>

    @ConstructorArgs(
        Arg(column = "activity_id", javaType = Long::class, id = true),
        Arg(column = "title", javaType = String::class),
        Arg(column = "category_code", javaType = String::class),
        Arg(column = "cover_file_id", javaType = Long::class),
        Arg(column = "registration_starts_at", javaType = LocalDateTime::class),
        Arg(column = "registration_ends_at", javaType = LocalDateTime::class),
        Arg(column = "starts_at", javaType = LocalDateTime::class),
        Arg(column = "ends_at", javaType = LocalDateTime::class),
        Arg(column = "region_code", javaType = String::class),
        Arg(column = "address_detail", javaType = String::class),
        Arg(column = "latitude", javaType = BigDecimal::class),
        Arg(column = "longitude", javaType = BigDecimal::class),
        Arg(column = "capacity", javaType = Int::class),
        Arg(column = "participant_count", javaType = Int::class),
        Arg(column = "place_name", javaType = String::class),
        Arg(column = "distance_meters", javaType = Long::class)
    )
    @Select("""
            SELECT a.id AS activity_id,
                   a.title,
                   a.category_code,
                   a.cover_file_id,
                   a.registration_starts_at,
                   a.registration_ends_at,
                   a.starts_at,
                   a.ends_at,
                   a.region_code,
                   a.address_detail,
                   a.latitude,
                   a.longitude,
                   a.capacity,
                   a.participant_count,
                   a.place_name,
                   CAST(ROUND(
                       6371000 * 2 * ASIN(LEAST(1, SQRT(
                           POWER(SIN(RADIANS(a.latitude - #{userLatitude}) / 2), 2)
                           + COS(RADIANS(#{userLatitude})) * COS(RADIANS(a.latitude))
                           * POWER(SIN(RADIANS(a.longitude - #{userLongitude}) / 2), 2)
                       )))
                   ) AS SIGNED) AS distance_meters
              FROM activities a
             WHERE a.status=2
               AND a.ends_at>#{now}
               AND a.latitude IS NOT NULL
               AND a.longitude IS NOT NULL
               AND (#{minLatitude} IS NULL OR a.latitude BETWEEN #{minLatitude} AND #{maxLatitude})
               AND (#{minLongitude} IS NULL OR a.longitude BETWEEN #{minLongitude} AND #{maxLongitude})
               AND (#{categoryCode} IS NULL OR a.category_code=#{categoryCode})
            HAVING (#{radiusMeters} IS NULL OR distance_meters<=#{radiusMeters})
             ORDER BY distance_meters ASC, a.id ASC
             LIMIT #{limit}
            """)
    fun findMapItemsByDistance(
        @Param("minLatitude") minLatitude: BigDecimal?,
        @Param("maxLatitude") maxLatitude: BigDecimal?,
        @Param("minLongitude") minLongitude: BigDecimal?,
        @Param("maxLongitude") maxLongitude: BigDecimal?,
        @Param("userLatitude") userLatitude: BigDecimal,
        @Param("userLongitude") userLongitude: BigDecimal,
        @Param("radiusMeters") radiusMeters: Int?,
        @Param("categoryCode") categoryCode: String?,
        @Param("now") now: LocalDateTime,
        @Param("limit") limit: Int
    ): List<ActivityMapRow>

    @ConstructorArgs(
        Arg(column = "activity_id", javaType = Long::class, id = true),
        Arg(column = "status", javaType = Int::class),
        Arg(column = "title", javaType = String::class),
        Arg(column = "category_code", javaType = String::class),
        Arg(column = "cover_file_id", javaType = Long::class),
        Arg(column = "owner_type", javaType = String::class),
        Arg(column = "owner_id", javaType = Long::class),
        Arg(column = "owner_display_name", javaType = String::class),
        Arg(column = "owner_avatar_file_id", javaType = Long::class),
        Arg(column = "registration_starts_at", javaType = LocalDateTime::class),
        Arg(column = "registration_ends_at", javaType = LocalDateTime::class),
        Arg(column = "starts_at", javaType = LocalDateTime::class),
        Arg(column = "ends_at", javaType = LocalDateTime::class),
        Arg(column = "region_code", javaType = String::class),
        Arg(column = "address_detail", javaType = String::class),
        Arg(column = "latitude", javaType = BigDecimal::class),
        Arg(column = "longitude", javaType = BigDecimal::class),
        Arg(column = "capacity", javaType = Int::class),
        Arg(column = "participant_count", javaType = Int::class),
        Arg(column = "description", javaType = String::class),
        Arg(column = "signup_details", javaType = String::class),
        Arg(column = "organizer_message", javaType = String::class),
        Arg(column = "published_at", javaType = LocalDateTime::class),
        Arg(column = "created_at", javaType = LocalDateTime::class),
        Arg(column = "updated_at", javaType = LocalDateTime::class),
        Arg(column = "registration_gender", javaType = Int::class),
        Arg(column = "organizer_phone_ciphertext", javaType = ByteArray::class),
        Arg(column = "organizer_wechat_ciphertext", javaType = ByteArray::class),
        Arg(column = "organizer_wechat_qr_file_id", javaType = Long::class),
        Arg(column = "refund_policy", javaType = String::class),
        Arg(column = "place_name", javaType = String::class)
    )
    @Select("""
            SELECT a.id AS activity_id,
                   a.status,
                   a.title,
                   a.category_code,
                   a.cover_file_id,
                   CASE WHEN a.owner_user_id IS NOT NULL
                        THEN 'USER' ELSE 'ORGANIZATION' END AS owner_type,
                   COALESCE(a.owner_user_id, a.owner_organization_id) AS owner_id,
                   CASE WHEN a.owner_user_id IS NOT NULL
                        THEN COALESCE(NULLIF(TRIM(p.nickname), ''), '已注销用户')
                        ELSE o.name END AS owner_display_name,
                   CASE WHEN a.owner_user_id IS NOT NULL
                        THEN p.avatar_file_id ELSE o.avatar_file_id END AS owner_avatar_file_id,
                   a.registration_starts_at,
                   a.registration_ends_at,
                   a.starts_at,
                   a.ends_at,
                   a.region_code,
                   a.address_detail,
                   a.latitude,
                   a.longitude,
                   a.capacity,
                   a.participant_count,
                   a.description,
                   a.signup_details,
                   a.organizer_message,
                   a.published_at,
                   a.created_at,
                   a.updated_at,
                   a.registration_gender,
                   NULL AS organizer_phone_ciphertext,
                   NULL AS organizer_wechat_ciphertext,
                   NULL AS organizer_wechat_qr_file_id,
                   NULL AS refund_policy,
                   a.place_name
              FROM activities a
              LEFT JOIN user_profiles p ON p.user_id=a.owner_user_id
              LEFT JOIN organizations o ON o.id=a.owner_organization_id
             WHERE (
                    (#{status}=2 AND a.status=2 AND a.ends_at>#{now})
                    OR (#{status}=3 AND a.status=3)
                    OR (#{status}=4 AND (
                        a.status=4 OR (a.status=2 AND a.ends_at<=#{now})
                    )
               )
               AND (#{categoryCode} IS NULL OR a.category_code=#{categoryCode})
               AND (#{regionCode} IS NULL OR a.region_code=#{regionCode})
               AND (
                    #{keyword} IS NULL
                    OR LOCATE(#{keyword}, a.title)>0
                    OR LOCATE(#{keyword}, COALESCE(a.description, ''))>0
               )
               AND (
                    #{topic} IS NULL
                    OR EXISTS (
                        SELECT 1
                        FROM activity_topic_relations atr
                        JOIN activity_topics t ON t.id=atr.topic_id
                        WHERE atr.activity_id=a.id
                          AND t.normalized_name=#{topic}
                    )
               )
               AND (
                    #{cursorStartsAt} IS NULL
                    OR a.starts_at>#{cursorStartsAt}
                    OR (a.starts_at=#{cursorStartsAt} AND a.id>#{cursorActivityId})
               )
             ORDER BY a.starts_at ASC, a.id ASC
             LIMIT #{limit}
            """)
    fun findPublicPage(
        @Param("status") status: Int,
        @Param("categoryCode") categoryCode: String?,
        @Param("regionCode") regionCode: String?,
        @Param("keyword") keyword: String?,
        @Param("topic") topic: String?,
        @Param("cursorStartsAt") cursorStartsAt: LocalDateTime?,
        @Param("cursorActivityId") cursorActivityId: Long?,
        @Param("now") now: LocalDateTime,
        @Param("limit") limit: Int
    ): List<ActivityPublicRow>

    fun findPublicPage(
        status: Int,
        categoryCode: String?,
        regionCode: String?,
        keyword: String?,
        cursorStartsAt: LocalDateTime?,
        cursorActivityId: Long?,
        now: LocalDateTime,
        limit: Int
    ): List<ActivityPublicRow> = findPublicPage(
        status, categoryCode, regionCode, keyword, null,
        cursorStartsAt, cursorActivityId, now, limit
    )

    @ConstructorArgs(
        Arg(column = "activity_id", javaType = Long::class, id = true),
        Arg(column = "status", javaType = Int::class),
        Arg(column = "title", javaType = String::class),
        Arg(column = "category_code", javaType = String::class),
        Arg(column = "cover_file_id", javaType = Long::class),
        Arg(column = "owner_type", javaType = String::class),
        Arg(column = "owner_id", javaType = Long::class),
        Arg(column = "owner_display_name", javaType = String::class),
        Arg(column = "owner_avatar_file_id", javaType = Long::class),
        Arg(column = "registration_starts_at", javaType = LocalDateTime::class),
        Arg(column = "registration_ends_at", javaType = LocalDateTime::class),
        Arg(column = "starts_at", javaType = LocalDateTime::class),
        Arg(column = "ends_at", javaType = LocalDateTime::class),
        Arg(column = "region_code", javaType = String::class),
        Arg(column = "address_detail", javaType = String::class),
        Arg(column = "latitude", javaType = BigDecimal::class),
        Arg(column = "longitude", javaType = BigDecimal::class),
        Arg(column = "capacity", javaType = Int::class),
        Arg(column = "participant_count", javaType = Int::class),
        Arg(column = "description", javaType = String::class),
        Arg(column = "signup_details", javaType = String::class),
        Arg(column = "organizer_message", javaType = String::class),
        Arg(column = "published_at", javaType = LocalDateTime::class),
        Arg(column = "created_at", javaType = LocalDateTime::class),
        Arg(column = "updated_at", javaType = LocalDateTime::class),
        Arg(column = "registration_gender", javaType = Int::class),
        Arg(column = "organizer_phone_ciphertext", javaType = ByteArray::class),
        Arg(column = "organizer_wechat_ciphertext", javaType = ByteArray::class),
        Arg(column = "organizer_wechat_qr_file_id", javaType = Long::class),
        Arg(column = "refund_policy", javaType = String::class),
        Arg(column = "place_name", javaType = String::class),
        Arg(column = "distance_meters", javaType = Long::class)
    )
    @Select("""
            SELECT a.id AS activity_id,
                   a.status,
                   a.title,
                   a.category_code,
                   a.cover_file_id,
                   CASE WHEN a.owner_user_id IS NOT NULL
                        THEN 'USER' ELSE 'ORGANIZATION' END AS owner_type,
                   COALESCE(a.owner_user_id, a.owner_organization_id) AS owner_id,
                   CASE WHEN a.owner_user_id IS NOT NULL
                        THEN COALESCE(NULLIF(TRIM(p.nickname), ''), '已注销用户')
                        ELSE o.name END AS owner_display_name,
                   CASE WHEN a.owner_user_id IS NOT NULL
                        THEN p.avatar_file_id ELSE o.avatar_file_id END AS owner_avatar_file_id,
                   a.registration_starts_at,
                   a.registration_ends_at,
                   a.starts_at,
                   a.ends_at,
                   a.region_code,
                   a.address_detail,
                   a.latitude,
                   a.longitude,
                   a.capacity,
                   a.participant_count,
                   a.description,
                   a.signup_details,
                   a.organizer_message,
                   a.published_at,
                   a.created_at,
                   a.updated_at,
                   a.registration_gender,
                   NULL AS organizer_phone_ciphertext,
                   NULL AS organizer_wechat_ciphertext,
                   NULL AS organizer_wechat_qr_file_id,
                   NULL AS refund_policy,
                   a.place_name,
                   CAST(ROUND(
                       6371000 * 2 * ASIN(LEAST(1, SQRT(
                           POWER(SIN(RADIANS(a.latitude - #{userLatitude}) / 2), 2)
                           + COS(RADIANS(#{userLatitude})) * COS(RADIANS(a.latitude))
                           * POWER(SIN(RADIANS(a.longitude - #{userLongitude}) / 2), 2)
                       )))
                   ) AS SIGNED) AS distance_meters
              FROM activities a
              LEFT JOIN user_profiles p ON p.user_id=a.owner_user_id
              LEFT JOIN organizations o ON o.id=a.owner_organization_id
             WHERE (
                    (#{status}=2 AND a.status=2 AND a.ends_at>#{now})
                    OR (#{status}=3 AND a.status=3)
                    OR (#{status}=4 AND (
                        a.status=4 OR (a.status=2 AND a.ends_at<=#{now})
                    )
               )
               AND a.latitude IS NOT NULL
               AND a.longitude IS NOT NULL
               AND (#{minLatitude} IS NULL OR a.latitude BETWEEN #{minLatitude} AND #{maxLatitude})
               AND (#{minLongitude} IS NULL OR a.longitude BETWEEN #{minLongitude} AND #{maxLongitude})
               AND (#{categoryCode} IS NULL OR a.category_code=#{categoryCode})
               AND (#{regionCode} IS NULL OR a.region_code=#{regionCode})
               AND (
                    #{keyword} IS NULL
                    OR LOCATE(#{keyword}, a.title)>0
                    OR LOCATE(#{keyword}, COALESCE(a.description, ''))>0
               )
               AND (
                    #{topic} IS NULL
                    OR EXISTS (
                        SELECT 1
                        FROM activity_topic_relations atr
                        JOIN activity_topics t ON t.id=atr.topic_id
                        WHERE atr.activity_id=a.id
                          AND t.normalized_name=#{topic}
                    )
               )
            HAVING (#{radiusMeters} IS NULL OR distance_meters<=#{radiusMeters})
               AND (
                    #{cursorDistanceMeters} IS NULL
                    OR distance_meters>#{cursorDistanceMeters}
                    OR (
                        distance_meters=#{cursorDistanceMeters}
                        AND a.id>#{cursorActivityId}
                    )
               )
             ORDER BY distance_meters ASC, a.id ASC
             LIMIT #{limit}
            """)
    fun findPublicPageByDistance(
        @Param("status") status: Int,
        @Param("categoryCode") categoryCode: String?,
        @Param("regionCode") regionCode: String?,
        @Param("keyword") keyword: String?,
        @Param("topic") topic: String?,
        @Param("userLatitude") userLatitude: BigDecimal,
        @Param("userLongitude") userLongitude: BigDecimal,
        @Param("minLatitude") minLatitude: BigDecimal?,
        @Param("maxLatitude") maxLatitude: BigDecimal?,
        @Param("minLongitude") minLongitude: BigDecimal?,
        @Param("maxLongitude") maxLongitude: BigDecimal?,
        @Param("radiusMeters") radiusMeters: Int?,
        @Param("cursorDistanceMeters") cursorDistanceMeters: Long?,
        @Param("cursorActivityId") cursorActivityId: Long?,
        @Param("now") now: LocalDateTime,
        @Param("limit") limit: Int
    ): List<ActivityPublicRow>

    fun findPublicPageByDistance(
        status: Int,
        categoryCode: String?,
        regionCode: String?,
        keyword: String?,
        userLatitude: BigDecimal,
        userLongitude: BigDecimal,
        radiusMeters: Int?,
        cursorDistanceMeters: Long?,
        cursorActivityId: Long?,
        now: LocalDateTime,
        limit: Int
    ): List<ActivityPublicRow> = findPublicPageByDistance(
        status, categoryCode, regionCode, keyword, null,
        userLatitude, userLongitude,
        null, null, null, null, radiusMeters,
        cursorDistanceMeters, cursorActivityId, now, limit
    )

    fun findPublicPageByDistance(
        status: Int,
        categoryCode: String?,
        regionCode: String?,
        keyword: String?,
        topic: String?,
        userLatitude: BigDecimal,
        userLongitude: BigDecimal,
        radiusMeters: Int?,
        cursorDistanceMeters: Long?,
        cursorActivityId: Long?,
        now: LocalDateTime,
        limit: Int
    ): List<ActivityPublicRow> = findPublicPageByDistance(
        status, categoryCode, regionCode, keyword, topic,
        userLatitude, userLongitude,
        null, null, null, null, radiusMeters,
        cursorDistanceMeters, cursorActivityId, now, limit
    )

    fun findPublicPageByDistance(
        status: Int,
        categoryCode: String?,
        regionCode: String?,
        keyword: String?,
        userLatitude: BigDecimal,
        userLongitude: BigDecimal,
        minLatitude: BigDecimal?,
        maxLatitude: BigDecimal?,
        minLongitude: BigDecimal?,
        maxLongitude: BigDecimal?,
        radiusMeters: Int?,
        cursorDistanceMeters: Long?,
        cursorActivityId: Long?,
        now: LocalDateTime,
        limit: Int
    ): List<ActivityPublicRow> = findPublicPageByDistance(
        status, categoryCode, regionCode, keyword, null,
        userLatitude, userLongitude,
        minLatitude, maxLatitude, minLongitude, maxLongitude, radiusMeters,
        cursorDistanceMeters, cursorActivityId, now, limit
    )

    @ConstructorArgs(
        Arg(column = "activity_id", javaType = Long::class, id = true),
        Arg(column = "status", javaType = Int::class),
        Arg(column = "title", javaType = String::class),
        Arg(column = "category_code", javaType = String::class),
        Arg(column = "cover_file_id", javaType = Long::class),
        Arg(column = "owner_type", javaType = String::class),
        Arg(column = "owner_id", javaType = Long::class),
        Arg(column = "owner_display_name", javaType = String::class),
        Arg(column = "owner_avatar_file_id", javaType = Long::class),
        Arg(column = "registration_starts_at", javaType = LocalDateTime::class),
        Arg(column = "registration_ends_at", javaType = LocalDateTime::class),
        Arg(column = "starts_at", javaType = LocalDateTime::class),
        Arg(column = "ends_at", javaType = LocalDateTime::class),
        Arg(column = "region_code", javaType = String::class),
        Arg(column = "address_detail", javaType = String::class),
        Arg(column = "latitude", javaType = BigDecimal::class),
        Arg(column = "longitude", javaType = BigDecimal::class),
        Arg(column = "capacity", javaType = Int::class),
        Arg(column = "participant_count", javaType = Int::class),
        Arg(column = "description", javaType = String::class),
        Arg(column = "signup_details", javaType = String::class),
        Arg(column = "organizer_message", javaType = String::class),
        Arg(column = "published_at", javaType = LocalDateTime::class),
        Arg(column = "created_at", javaType = LocalDateTime::class),
        Arg(column = "updated_at", javaType = LocalDateTime::class),
        Arg(column = "registration_gender", javaType = Int::class),
        Arg(column = "organizer_phone_ciphertext", javaType = ByteArray::class),
        Arg(column = "organizer_wechat_ciphertext", javaType = ByteArray::class),
        Arg(column = "organizer_wechat_qr_file_id", javaType = Long::class),
        Arg(column = "refund_policy", javaType = String::class),
        Arg(column = "place_name", javaType = String::class)
    )
    @Select("""
            SELECT a.id AS activity_id,
                   a.status,
                   a.title,
                   a.category_code,
                   a.cover_file_id,
                   CASE WHEN a.owner_user_id IS NOT NULL
                        THEN 'USER' ELSE 'ORGANIZATION' END AS owner_type,
                   COALESCE(a.owner_user_id, a.owner_organization_id) AS owner_id,
                   CASE WHEN a.owner_user_id IS NOT NULL
                        THEN COALESCE(NULLIF(TRIM(p.nickname), ''), '已注销用户')
                        ELSE o.name END AS owner_display_name,
                   CASE WHEN a.owner_user_id IS NOT NULL
                        THEN p.avatar_file_id ELSE o.avatar_file_id END AS owner_avatar_file_id,
                   a.registration_starts_at,
                   a.registration_ends_at,
                   a.starts_at,
                   a.ends_at,
                   a.region_code,
                   a.address_detail,
                   a.latitude,
                   a.longitude,
                   a.capacity,
                   a.participant_count,
                   a.description,
                   a.signup_details,
                   a.organizer_message,
                   a.published_at,
                   a.created_at,
                   a.updated_at,
                   a.registration_gender,
                   a.organizer_phone_ciphertext,
                   a.organizer_wechat_ciphertext,
                   a.organizer_wechat_qr_file_id,
                   NULL AS refund_policy,
                   a.place_name
              FROM activities a
              LEFT JOIN user_profiles p ON p.user_id=a.owner_user_id
              LEFT JOIN organizations o ON o.id=a.owner_organization_id
             WHERE a.id=#{activityId}
               AND a.status IN (2, 3, 4)
            """)
    fun findPublicById(@Param("activityId") activityId: Long): ActivityPublicRow?

    @ConstructorArgs(
        Arg(column = "activity_id", javaType = Long::class, id = true),
        Arg(column = "status", javaType = Int::class),
        Arg(column = "title", javaType = String::class),
        Arg(column = "category_code", javaType = String::class),
        Arg(column = "cover_file_id", javaType = Long::class),
        Arg(column = "owner_type", javaType = String::class),
        Arg(column = "owner_id", javaType = Long::class),
        Arg(column = "owner_display_name", javaType = String::class),
        Arg(column = "owner_avatar_file_id", javaType = Long::class),
        Arg(column = "registration_starts_at", javaType = LocalDateTime::class),
        Arg(column = "registration_ends_at", javaType = LocalDateTime::class),
        Arg(column = "starts_at", javaType = LocalDateTime::class),
        Arg(column = "ends_at", javaType = LocalDateTime::class),
        Arg(column = "region_code", javaType = String::class),
        Arg(column = "address_detail", javaType = String::class),
        Arg(column = "latitude", javaType = BigDecimal::class),
        Arg(column = "longitude", javaType = BigDecimal::class),
        Arg(column = "capacity", javaType = Int::class),
        Arg(column = "participant_count", javaType = Int::class),
        Arg(column = "description", javaType = String::class),
        Arg(column = "signup_details", javaType = String::class),
        Arg(column = "organizer_message", javaType = String::class),
        Arg(column = "published_at", javaType = LocalDateTime::class),
        Arg(column = "created_at", javaType = LocalDateTime::class),
        Arg(column = "updated_at", javaType = LocalDateTime::class),
        Arg(column = "registration_gender", javaType = Int::class),
        Arg(column = "organizer_phone_ciphertext", javaType = ByteArray::class),
        Arg(column = "organizer_wechat_ciphertext", javaType = ByteArray::class),
        Arg(column = "organizer_wechat_qr_file_id", javaType = Long::class),
        Arg(column = "refund_policy", javaType = String::class),
        Arg(column = "place_name", javaType = String::class)
    )
    @Select("""
            <script>
            SELECT a.id AS activity_id,
                   a.status,
                   a.title,
                   a.category_code,
                   a.cover_file_id,
                   CASE WHEN a.owner_user_id IS NOT NULL
                        THEN 'USER' ELSE 'ORGANIZATION' END AS owner_type,
                   COALESCE(a.owner_user_id, a.owner_organization_id) AS owner_id,
                   CASE WHEN a.owner_user_id IS NOT NULL
                        THEN COALESCE(NULLIF(TRIM(p.nickname), ''), '已注销用户')
                        ELSE o.name END AS owner_display_name,
                   CASE WHEN a.owner_user_id IS NOT NULL
                        THEN p.avatar_file_id ELSE o.avatar_file_id END AS owner_avatar_file_id,
                   a.registration_starts_at,
                   a.registration_ends_at,
                   a.starts_at,
                   a.ends_at,
                   a.region_code,
                   a.address_detail,
                   a.latitude,
                   a.longitude,
                   a.capacity,
                   a.participant_count,
                   NULL AS description,
                   NULL AS signup_details,
                   NULL AS organizer_message,
                   NULL AS published_at,
                   NULL AS created_at,
                   NULL AS updated_at,
                   NULL AS registration_gender,
                   NULL AS organizer_phone_ciphertext,
                   NULL AS organizer_wechat_ciphertext,
                   NULL AS organizer_wechat_qr_file_id,
                   NULL AS refund_policy,
                   a.place_name
              FROM activities a
              LEFT JOIN user_profiles p ON p.user_id=a.owner_user_id
              LEFT JOIN organizations o ON o.id=a.owner_organization_id
             WHERE a.status IN (2, 3, 4)
               AND a.id IN
               <foreach collection="activityIds" item="activityId"
                        open="(" separator="," close=")">
                   #{activityId}
               </foreach>
            </script>
            """)
    fun findPublicSummariesByIds(
        @Param("activityIds") activityIds: List<Long>
    ): List<ActivityPublicRow>

    @ConstructorArgs(
        Arg(column = "activity_id", javaType = Long::class, id = true),
        Arg(column = "status", javaType = Int::class),
        Arg(column = "title", javaType = String::class),
        Arg(column = "category_code", javaType = String::class),
        Arg(column = "cover_file_id", javaType = Long::class),
        Arg(column = "owner_type", javaType = String::class),
        Arg(column = "owner_id", javaType = Long::class),
        Arg(column = "owner_display_name", javaType = String::class),
        Arg(column = "owner_avatar_file_id", javaType = Long::class),
        Arg(column = "registration_starts_at", javaType = LocalDateTime::class),
        Arg(column = "registration_ends_at", javaType = LocalDateTime::class),
        Arg(column = "starts_at", javaType = LocalDateTime::class),
        Arg(column = "ends_at", javaType = LocalDateTime::class),
        Arg(column = "region_code", javaType = String::class),
        Arg(column = "address_detail", javaType = String::class),
        Arg(column = "latitude", javaType = BigDecimal::class),
        Arg(column = "longitude", javaType = BigDecimal::class),
        Arg(column = "capacity", javaType = Int::class),
        Arg(column = "participant_count", javaType = Int::class),
        Arg(column = "description", javaType = String::class),
        Arg(column = "signup_details", javaType = String::class),
        Arg(column = "organizer_message", javaType = String::class),
        Arg(column = "published_at", javaType = LocalDateTime::class),
        Arg(column = "version", javaType = Int::class),
        Arg(column = "created_at", javaType = LocalDateTime::class),
        Arg(column = "updated_at", javaType = LocalDateTime::class),
        Arg(column = "registration_gender", javaType = Int::class),
        Arg(column = "organizer_phone_ciphertext", javaType = ByteArray::class),
        Arg(column = "organizer_wechat_ciphertext", javaType = ByteArray::class),
        Arg(column = "organizer_wechat_qr_file_id", javaType = Long::class),
        Arg(column = "refund_policy", javaType = String::class),
        Arg(column = "place_name", javaType = String::class)
    )
    @Select("""
            SELECT a.id AS activity_id,
                   a.status,
                   a.title,
                   a.category_code,
                   a.cover_file_id,
                   CASE WHEN a.owner_user_id IS NOT NULL
                        THEN 'USER' ELSE 'ORGANIZATION' END AS owner_type,
                   COALESCE(a.owner_user_id, a.owner_organization_id) AS owner_id,
                   CASE WHEN a.owner_user_id IS NOT NULL
                        THEN COALESCE(NULLIF(TRIM(p.nickname), ''), '已注销用户')
                        ELSE o.name END AS owner_display_name,
                   CASE WHEN a.owner_user_id IS NOT NULL
                        THEN p.avatar_file_id ELSE o.avatar_file_id END AS owner_avatar_file_id,
                   a.registration_starts_at,
                   a.registration_ends_at,
                   a.starts_at,
                   a.ends_at,
                   a.region_code,
                   a.address_detail,
                   a.latitude,
                   a.longitude,
                   a.capacity,
                   a.participant_count,
                   a.description,
                   a.signup_details,
                   a.organizer_message,
                   a.published_at,
                   a.version,
                   a.created_at,
                   a.updated_at,
                   a.registration_gender,
                   a.organizer_phone_ciphertext,
                   a.organizer_wechat_ciphertext,
                   a.organizer_wechat_qr_file_id,
                   NULL AS refund_policy,
                   a.place_name
              FROM activities a
              LEFT JOIN user_profiles p ON p.user_id=a.owner_user_id
              LEFT JOIN organizations o ON o.id=a.owner_organization_id
             WHERE a.id=#{activityId}
               AND (
                    a.owner_user_id=#{userId}
                    OR (
                        #{organizationId} IS NOT NULL
                        AND a.owner_organization_id=#{organizationId}
                    )
               )
            """)
    fun findManagedById(
        @Param("userId") userId: Long,
        @Param("organizationId") organizationId: Long?,
        @Param("activityId") activityId: Long
    ): ActivityManagedDetailRow?

    @ConstructorArgs(
        Arg(column = "file_id", javaType = Long::class, id = true),
        Arg(column = "sort_order", javaType = Int::class)
    )
    @Select("""
            SELECT file_id, sort_order
              FROM activity_media
             WHERE activity_id=#{activityId}
             ORDER BY sort_order ASC, id ASC
            """)
    fun findPublicMedia(@Param("activityId") activityId: Long): List<ActivityPublicMediaRow>

    @ConstructorArgs(
        Arg(column = "activity_id", javaType = Long::class, id = true),
        Arg(column = "status", javaType = Int::class),
        Arg(column = "title", javaType = String::class),
        Arg(column = "category_code", javaType = String::class),
        Arg(column = "cover_file_id", javaType = Long::class),
        Arg(column = "owner_type", javaType = String::class),
        Arg(column = "owner_id", javaType = Long::class),
        Arg(column = "owner_display_name", javaType = String::class),
        Arg(column = "owner_avatar_file_id", javaType = Long::class),
        Arg(column = "starts_at", javaType = LocalDateTime::class),
        Arg(column = "ends_at", javaType = LocalDateTime::class),
        Arg(column = "region_code", javaType = String::class),
        Arg(column = "address_detail", javaType = String::class),
        Arg(column = "latitude", javaType = BigDecimal::class),
        Arg(column = "longitude", javaType = BigDecimal::class),
        Arg(column = "capacity", javaType = Int::class),
        Arg(column = "participant_count", javaType = Int::class),
        Arg(column = "version", javaType = Int::class),
        Arg(column = "updated_at", javaType = LocalDateTime::class),
        Arg(column = "place_name", javaType = String::class)
    )
    @Select("""
            SELECT a.id AS activity_id,
                   a.status,
                   a.title,
                   a.category_code,
                   a.cover_file_id,
                   CASE WHEN a.owner_user_id IS NOT NULL
                        THEN 'USER' ELSE 'ORGANIZATION' END AS owner_type,
                   COALESCE(a.owner_user_id, a.owner_organization_id) AS owner_id,
                   CASE WHEN a.owner_user_id IS NOT NULL
                        THEN COALESCE(NULLIF(TRIM(p.nickname), ''), '已注销用户')
                        ELSE o.name END AS owner_display_name,
                   CASE WHEN a.owner_user_id IS NOT NULL
                        THEN p.avatar_file_id ELSE o.avatar_file_id END AS owner_avatar_file_id,
                   a.starts_at,
                   a.ends_at,
                   a.region_code,
                   a.address_detail,
                   a.latitude,
                   a.longitude,
                   a.capacity,
                   a.participant_count,
                   a.version,
                   a.updated_at,
                   a.place_name
              FROM activities a
              LEFT JOIN user_profiles p ON p.user_id=a.owner_user_id
              LEFT JOIN organizations o ON o.id=a.owner_organization_id
             WHERE (
                    ((#{ownerType} IS NULL OR #{ownerType}='USER')
                        AND a.owner_user_id=#{userId})
                    OR
                    ((#{ownerType} IS NULL OR #{ownerType}='ORGANIZATION')
                        AND #{organizationId} IS NOT NULL
                        AND a.owner_organization_id=#{organizationId})
                   )
               AND (#{status} IS NULL OR a.status=#{status})
               AND (
                    #{cursorUpdatedAt} IS NULL
                    OR a.updated_at<#{cursorUpdatedAt}
                    OR (a.updated_at=#{cursorUpdatedAt} AND a.id<#{cursorActivityId})
               )
             ORDER BY a.updated_at DESC, a.id DESC
             LIMIT #{limit}
            """)
    fun findManagedPage(
        @Param("userId") userId: Long,
        @Param("organizationId") organizationId: Long?,
        @Param("status") status: Int?,
        @Param("ownerType") ownerType: String?,
        @Param("cursorUpdatedAt") cursorUpdatedAt: LocalDateTime?,
        @Param("cursorActivityId") cursorActivityId: Long?,
        @Param("limit") limit: Int
    ): List<ActivityManagedRow>
}
