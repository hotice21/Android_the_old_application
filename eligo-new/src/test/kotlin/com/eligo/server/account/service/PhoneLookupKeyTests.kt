package com.eligo.server.account.service

import com.eligo.server.security.AesGcmSensitiveDataCodec
import com.eligo.server.security.SensitiveDataCodec
import com.eligo.server.security.SensitiveDataProperties
import org.junit.jupiter.api.Test
import java.util.Base64
import org.assertj.core.api.Assertions.assertThat

class PhoneLookupKeyTests {

    companion object {
        private val KEY: String = Base64.getEncoder().encodeToString(ByteArray(32))
    }

    private val lookupKey = PhoneLookupKey()
    private val codec: SensitiveDataCodec =
        AesGcmSensitiveDataCodec(SensitiveDataProperties(KEY, KEY))

    @Test
    fun canonicalValueIncludesPhonePurposeAndNormalizedCountryCode() {
        assertThat(lookupKey.canonical(" +86 ", " 13800121234 "))
            .isEqualTo("phone:86:13800121234")
    }

    @Test
    fun hashesAreSeparatedByPurposeAndCountryCode() {
        val chinaPhone = codec.lookupHash(lookupKey.canonical("86", "13800121234"))
        val usPhone = codec.lookupHash(lookupKey.canonical("1", "13800121234"))
        val anotherPurpose = codec.lookupHash("device:86:13800121234")

        assertThat(chinaPhone).isNotEqualTo(usPhone).isNotEqualTo(anotherPurpose)
    }
}
