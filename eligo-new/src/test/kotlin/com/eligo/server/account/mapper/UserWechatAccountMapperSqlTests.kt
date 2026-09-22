package com.eligo.server.account.mapper

import org.apache.ibatis.annotations.Select
import org.junit.jupiter.api.Test
import org.assertj.core.api.Assertions.assertThat

class UserWechatAccountMapperSqlTests {

    @Test
    fun conflictWinnerLookupUsesCurrentLockingRead() {
        val select = UserWechatAccountMapper::class.java
            .getMethod("lockActiveByAppIdAndOpenidHash", String::class.java, ByteArray::class.java)
            .getAnnotation(Select::class.java)

        assertThat(select).isNotNull()
        assertThat(select.value.joinToString(" ").replace(Regex("\\s+"), " ").uppercase())
            .contains("FOR UPDATE")
    }
}
