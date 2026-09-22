package com.eligo.server.account.service

import com.eligo.server.account.mapper.UserPhoneBindingMapper
import com.eligo.server.account.mapper.UserWechatAccountMapper
import java.lang.reflect.Method
import org.apache.ibatis.annotations.Update
import org.junit.jupiter.api.Test
import org.assertj.core.api.Assertions.assertThat

class IdentityAnonymizationSqlTests {
    @Test
    fun wechatCleanupCoversAllHistoricalBindingsAndClearsIdentifiers() {
        val sql = sql(UserWechatAccountMapper::class.java.getMethod(
                "unbindAllActiveByUserId", Long::class.java, java.time.LocalDateTime::class.java))

        assertThat(sql).doesNotContain("status=1")
        assertThat(sql).contains("openid_ciphertext=X''")
        assertThat(sql).contains("openid_lookup_hash=UNHEX(REPEAT('00',32))")
        assertThat(sql).contains("unionid_ciphertext=NULL", "unionid_lookup_hash=NULL")
    }

    @Test
    fun phoneCleanupCoversAllHistoricalBindingsAndClearsIdentifiers() {
        val sql = sql(UserPhoneBindingMapper::class.java.getMethod(
                "anonymizeAndUnbindAllActiveByUserId", Long::class.java,
                java.time.LocalDateTime::class.java, String::class.java))

        assertThat(sql).doesNotContain("status=1")
        assertThat(sql).contains("country_code=''", "phone_ciphertext=X''")
        assertThat(sql).contains("phone_lookup_hash=UNHEX(REPEAT('00',32))")
        assertThat(sql).contains("phone_last_four=''")
    }

    private fun sql(method: Method): String {
        return method.getAnnotation(Update::class.java).value.joinToString(" ")
            .replace(Regex("\\s+"), "")
    }
}
