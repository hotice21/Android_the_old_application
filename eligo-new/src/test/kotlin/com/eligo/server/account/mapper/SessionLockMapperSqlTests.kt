package com.eligo.server.account.mapper

import org.apache.ibatis.annotations.Select
import org.junit.jupiter.api.Test
import java.lang.reflect.Method
import org.assertj.core.api.Assertions.assertThat

class SessionLockMapperSqlTests {

    @Test
    fun candidateReadsDoNotTakeRowLocks() {
        assertNotLocking(UserLoginSessionMapper::class.java
            .getMethod("findByRefreshTokenHash", ByteArray::class.java))
        assertNotLocking(UserLoginSessionMapper::class.java
            .getMethod("findBySessionKey", String::class.java))
        assertNotLocking(UserLoginSessionMapper::class.java
            .getMethod("findOwnedById", Long::class.java, Long::class.java))
    }

    @Test
    fun finalReadsTakeRowLocks() {
        assertLocking(UserMapper::class.java.getMethod("lockById", Long::class.java))
        assertLocking(UserPhoneBindingMapper::class.java.getMethod("lockActiveByUserId", Long::class.java))
        assertLocking(UserDeviceMapper::class.java.getMethod("lockOwnedById", Long::class.java, Long::class.java))
        assertLocking(UserLoginSessionMapper::class.java
            .getMethod("lockByIdAndRefreshTokenHash", Long::class.java, ByteArray::class.java))
        assertLocking(UserLoginSessionMapper::class.java.getMethod("lockBySessionKey", String::class.java))
        assertLocking(UserLoginSessionMapper::class.java.getMethod("lockOwnedById", Long::class.java, Long::class.java))
    }

    private fun assertLocking(method: Method) {
        assertThat(sql(method)).contains("FOR UPDATE")
    }

    private fun assertNotLocking(method: Method) {
        assertThat(sql(method)).doesNotContain("FOR UPDATE")
    }

    private fun sql(method: Method): String {
        val select = method.getAnnotation(Select::class.java)
        return select.value.joinToString(" ").replace(Regex("\\s+"), " ").uppercase()
    }
}
