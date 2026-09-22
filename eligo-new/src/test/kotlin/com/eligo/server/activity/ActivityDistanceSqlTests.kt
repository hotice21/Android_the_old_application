package com.eligo.server.activity

import com.eligo.server.activity.mapper.ActivityReadMapper
import org.apache.ibatis.annotations.ConstructorArgs
import org.apache.ibatis.annotations.Select
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ActivityDistanceSqlTests {

    @Test
    fun nearbyQueryUsesHaversineRadiusDistanceCursorAndStableOrder() {
        val method = ActivityReadMapper::class.java.declaredMethods
            .first { it.name == "findPublicPageByDistance" && it.getAnnotation(Select::class.java) != null }
        val sql = method.getAnnotation(Select::class.java).value.joinToString("\n")

        assertThat(sql)
            .contains("6371000", "ASIN", "RADIANS", "distance_meters")
            .contains("#{radiusMeters}", "#{cursorDistanceMeters}")
            .contains(
                "a.latitude BETWEEN #{minLatitude} AND #{maxLatitude}",
                "a.longitude BETWEEN #{minLongitude} AND #{maxLongitude}"
            )
            .contains("ORDER BY distance_meters ASC, a.id ASC")
            .contains("a.latitude IS NOT NULL", "a.longitude IS NOT NULL")
        assertThat(method.getAnnotation(ConstructorArgs::class.java).value)
            .anySatisfy { argument ->
                assertThat(argument.column).isEqualTo("distance_meters")
            }
    }

    @Test
    fun mapDistanceQuerySupportsViewportAndNearbyRadiusModes() {
        val method = ActivityReadMapper::class.java.declaredMethods
            .first { it.name == "findMapItemsByDistance" }
        val sql = method.getAnnotation(Select::class.java).value.joinToString("\n")

        assertThat(sql)
            .contains("6371000", "distance_meters", "#{radiusMeters}")
            .contains("#{minLatitude} IS NULL", "#{minLongitude} IS NULL")
            .contains("ORDER BY distance_meters ASC, a.id ASC")
            .contains("a.status=2", "a.ends_at>#{now}")
    }
}
