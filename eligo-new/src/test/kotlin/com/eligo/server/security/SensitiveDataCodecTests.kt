package com.eligo.server.security

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.Base64

class SensitiveDataCodecTests {

    private val ENCRYPTION_KEY = Base64.getEncoder().encodeToString(ByteArray(32))
    private val LOOKUP_KEY = Base64.getEncoder().encodeToString(
        byteArrayOf(
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
            17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32
        )
    )
    private val SECOND_LOOKUP_KEY = Base64.getEncoder().encodeToString(
        byteArrayOf(
            32, 31, 30, 29, 28, 27, 26, 25, 24, 23, 22, 21, 20, 19, 18, 17,
            16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1
        )
    )

    private val codec = AesGcmSensitiveDataCodec(
        SensitiveDataProperties(ENCRYPTION_KEY, LOOKUP_KEY)
    )

    @Test
    fun encryptUsesRandomIvAndExposesSeparateAuthenticationTag() {
        val firstCiphertext = codec.encrypt("13800138000")
        val secondCiphertext = codec.encrypt("13800138000")
        val parts = firstCiphertext.split(".")

        assertThat(firstCiphertext).isNotEqualTo(secondCiphertext)
        assertThat(parts).hasSize(4)
        assertThat(parts[0]).isEqualTo("v1")
        assertThat(Base64.getUrlDecoder().decode(parts[1])).hasSize(12)
        assertThat(Base64.getUrlDecoder().decode(parts[3])).hasSize(16)
        assertThat(codec.decrypt(firstCiphertext)).isEqualTo("13800138000")
        assertThat(codec.decrypt(secondCiphertext)).isEqualTo("13800138000")
    }

    @Test
    fun decryptRejectsTamperedAuthenticationTag() {
        val parts = codec.encrypt("13800138000").split(".")
        val tag = Base64.getUrlDecoder().decode(parts[3])
        tag[0] = (tag[0].toInt() xor 1).toByte()
        val tampered = parts[0] + "." + parts[1] + "." + parts[2] + "." +
            Base64.getUrlEncoder().withoutPadding().encodeToString(tag)

        assertThatThrownBy { codec.decrypt(tampered) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun lookupHashNormalizesPlaintextAndUsesDedicatedKey() {
        val codecWithDifferentLookupKey = AesGcmSensitiveDataCodec(
            SensitiveDataProperties(ENCRYPTION_KEY, SECOND_LOOKUP_KEY)
        )
        val normalized = codec.lookupHash(" 13800138000 ")
        val sameValue = codec.lookupHash("13800138000")
        val differentValue = codec.lookupHash("13800138001")

        assertThat(normalized).hasSize(32).isEqualTo(sameValue).isNotEqualTo(differentValue)
        assertThat(normalized).isNotEqualTo(codecWithDifferentLookupKey.lookupHash("13800138000"))
    }
}
