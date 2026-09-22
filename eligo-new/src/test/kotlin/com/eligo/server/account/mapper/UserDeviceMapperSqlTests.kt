package com.eligo.server.account.mapper

import org.apache.ibatis.annotations.Select
import org.apache.ibatis.annotations.Update
import org.junit.jupiter.api.Test
import org.assertj.core.api.Assertions.assertThat

class UserDeviceMapperSqlTests {

    @Test
    fun installationLookupUsesCurrentReadAfterUserLock() {
        val select = UserDeviceMapper::class.java.getMethod(
                "findByUserIdAndInstallationHash", Long::class.java, ByteArray::class.java)
            .getAnnotation(Select::class.java)
        val sql = normalize(select.value)

        assertThat(sql).contains("FOR UPDATE")
    }

    @Test
    fun activeCandidatesRequireAnUnexpiredActiveSession() {
        val select = UserDeviceMapper::class.java.getMethod("findActiveForUpdate", Long::class.java)
            .getAnnotation(Select::class.java)
        val sql = normalize(select.value)

        assertThat(sql).contains("USER_LOGIN_SESSIONS", "STATUS = 1", "EXPIRES_AT > UTC_TIMESTAMP(3)")
    }

    @Test
    fun lastSeenUpdateIsThrottledByDatabaseTime() {
        val update = UserDeviceMapper::class.java.getMethod("touchLastSeenIfStale", Long::class.java)
            .getAnnotation(Update::class.java)
        val sql = normalize(update.value)

        assertThat(sql).contains("LAST_SEEN_AT", "INTERVAL 5 MINUTE")
    }

    private fun normalize(fragments: Array<String>): String {
        return fragments.joinToString(" ").replace(Regex("\\s+"), " ").uppercase()
    }
}
