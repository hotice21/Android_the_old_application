package com.eligo.server.security

import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Component
class AesGcmSensitiveDataCodec(properties: SensitiveDataProperties) : SensitiveDataCodec {
    private val encryptionKey: SecretKeySpec =
        SecretKeySpec(decodeKey(properties.encryptionKeyBase64, "敏感数据加密密钥"), "AES")
    private val lookupKey: SecretKeySpec =
        SecretKeySpec(decodeKey(properties.lookupKeyBase64, "敏感数据查找密钥"), "HmacSHA256")

    override fun encrypt(plaintext: String): String {
        val iv = ByteArray(IV_LENGTH_BYTES)
        SECURE_RANDOM.nextBytes(iv)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            val encryptedWithTag = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
            val ciphertextLength = encryptedWithTag.size - TAG_LENGTH_BYTES
            val encoder = Base64.getUrlEncoder().withoutPadding()
            "$VERSION.${encoder.encodeToString(iv)}" +
                ".${encoder.encodeToString(encryptedWithTag.copyOf(ciphertextLength))}" +
                ".${encoder.encodeToString(encryptedWithTag.copyOfRange(ciphertextLength, encryptedWithTag.size))}"
        } catch (exception: GeneralSecurityException) {
            throw IllegalStateException("敏感数据加密失败", exception)
        }
    }

    override fun decrypt(ciphertext: String): String {
        val parts = ciphertext.split(".")
        if (parts.size != 4 || parts[0] != VERSION) {
            throw IllegalArgumentException("敏感数据密文格式无效")
        }
        return try {
            val decoder = Base64.getUrlDecoder()
            val iv = decoder.decode(parts[1])
            val encrypted = decoder.decode(parts[2])
            val tag = decoder.decode(parts[3])
            if (iv.size != IV_LENGTH_BYTES || tag.size != TAG_LENGTH_BYTES) {
                throw IllegalArgumentException("敏感数据密文格式无效")
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(joinCiphertextAndTag(encrypted, tag)), StandardCharsets.UTF_8)
        } catch (exception: IllegalArgumentException) {
            throw IllegalArgumentException("敏感数据密文格式无效", exception)
        } catch (exception: GeneralSecurityException) {
            throw IllegalArgumentException("敏感数据密文校验失败", exception)
        }
    }

    override fun lookupHash(plaintext: String): ByteArray {
        return try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(lookupKey)
            mac.doFinal(plaintext.trim().toByteArray(StandardCharsets.UTF_8))
        } catch (exception: GeneralSecurityException) {
            throw IllegalStateException("敏感数据摘要计算失败", exception)
        }
    }

    private fun joinCiphertextAndTag(encrypted: ByteArray, tag: ByteArray): ByteArray {
        val encryptedWithTag = encrypted.copyOf(encrypted.size + tag.size)
        System.arraycopy(tag, 0, encryptedWithTag, encrypted.size, tag.size)
        return encryptedWithTag
    }

    private fun decodeKey(encodedKey: String, keyName: String): ByteArray {
        return try {
            val key = Base64.getDecoder().decode(encodedKey)
            if (key.size != KEY_LENGTH_BYTES) {
                throw IllegalArgumentException("$keyName 必须为 Base64 编码的 32 字节值")
            }
            key
        } catch (exception: IllegalArgumentException) {
            throw IllegalArgumentException("$keyName 必须为 Base64 编码的 32 字节值", exception)
        }
    }

    companion object {
        private const val VERSION = "v1"
        private const val KEY_LENGTH_BYTES = 32
        private const val IV_LENGTH_BYTES = 12
        private const val TAG_LENGTH_BITS = 128
        private const val TAG_LENGTH_BYTES = TAG_LENGTH_BITS / Byte.SIZE_BITS
        private val SECURE_RANDOM = SecureRandom()
    }
}
