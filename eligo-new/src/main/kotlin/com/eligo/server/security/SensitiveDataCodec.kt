package com.eligo.server.security

interface SensitiveDataCodec {

    fun encrypt(plaintext: String): String

    fun decrypt(ciphertext: String): String

    fun lookupHash(plaintext: String): ByteArray
}
