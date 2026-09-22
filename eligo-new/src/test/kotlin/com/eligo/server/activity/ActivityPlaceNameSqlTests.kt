package com.eligo.server.activity

import com.eligo.server.activity.mapper.ActivityReadMapper
import org.apache.ibatis.annotations.ConstructorArgs
import org.apache.ibatis.annotations.Select
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ActivityPlaceNameSqlTests {

    @Test
    fun everyActivityReadQuerySelectsAndMapsPlaceName() {
        for (methodName in listOf(
            "findMapItems",
            "findPublicPage",
            "findPublicById",
            "findManagedById",
            "findManagedPage"
        )) {
            val method = method(methodName)
            val sql = method.getAnnotation(Select::class.java).value.joinToString("\n")

            assertThat(sql).contains("a.place_name")
            assertThat(method.getAnnotation(ConstructorArgs::class.java).value)
                .anySatisfy { argument ->
                    assertThat(argument.column).isEqualTo("place_name")
                }
        }
    }

    private fun method(name: String): java.lang.reflect.Method {
        return ActivityReadMapper::class.java.declaredMethods
            .first { it.name == name && it.getAnnotation(Select::class.java) != null }
    }
}
