package com.eligo.server.recommendation

import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class RecommendationCursorCodec(signingKey: ByteArray) {

    init {
        if (signingKey.size < 32) {
            throw IllegalArgumentException("推荐游标签名密钥至少需要 32 字节")
        }
    }

    private val signingKey = SecretKeySpec(signingKey.clone(), "HmacSHA256")

    fun encodeSnapshot(
        viewerKey: String,
        mode: String,
        snapshotId: String,
        limit: Int,
        offset: Int
    ): String {
        return encode("r1|snapshot|$viewerKey|$mode|$snapshotId|$limit|$offset")
    }

    fun encodeTime(
        viewerKey: String,
        limit: Int,
        publishedAt: Instant,
        postId: Long
    ): String {
        return encode("r1|time|$viewerKey|$limit|${publishedAt.toEpochMilli()}|$postId")
    }

    fun decode(cursor: String?, expectedViewerKey: String, requestedLimit: Int): Cursor {
        if (cursor == null || cursor.isBlank() || cursor.length > MAX_LENGTH) {
            throw IllegalArgumentException("推荐游标无效")
        }
        try {
            val signedParts = cursor.split(".")
            if (signedParts.size != 2
                || !MessageDigest.isEqual(
                    sign(signedParts[0]),
                    Base64.getUrlDecoder().decode(signedParts[1]))) {
                throw IllegalArgumentException("推荐游标无效")
            }
            val raw = String(
                Base64.getUrlDecoder().decode(signedParts[0]),
                StandardCharsets.UTF_8)
            val parts = raw.split("|")
            if (parts.size < 3 || "r1" != parts[0]
                || expectedViewerKey != parts[2]) {
                throw IllegalArgumentException("推荐游标无效")
            }
            return when (parts[1]) {
                "snapshot" -> decodeSnapshot(parts, requestedLimit)
                "time" -> decodeTime(parts, requestedLimit)
                else -> throw IllegalArgumentException("推荐游标无效")
            }
        } catch (exception: RuntimeException) {
            if (exception is IllegalArgumentException) {
                throw exception
            }
            throw IllegalArgumentException("推荐游标无效", exception)
        }
    }

    private fun decodeSnapshot(parts: List<String>, requestedLimit: Int): SnapshotCursor {
        if (parts.size != 7
            || !("VECTOR" == parts[3] || "FALLBACK" == parts[3])
            || !validLimit(parts[5], requestedLimit)
            || !parts[4].matches("[A-Za-z0-9_-]{1,128}".toRegex())) {
            throw IllegalArgumentException("推荐游标无效")
        }
        val offset = parts[6].toInt()
        if (parts[3].isBlank() || parts[4].isBlank() || offset < 0) {
            throw IllegalArgumentException("推荐游标无效")
        }
        return SnapshotCursor(
            parts[2], parts[3], parts[4], parts[5].toInt(), offset)
    }

    private fun decodeTime(parts: List<String>, requestedLimit: Int): TimeCursor {
        if (parts.size != 6
            || parts[2].isBlank()
            || !validLimit(parts[3], requestedLimit)) {
            throw IllegalArgumentException("推荐游标无效")
        }
        val epoch = parts[4].toLong()
        val postId = parts[5].toLong()
        if (epoch < 0 || postId <= 0) {
            throw IllegalArgumentException("推荐游标无效")
        }
        return TimeCursor(
            parts[2], parts[3].toInt(), Instant.ofEpochMilli(epoch), postId)
    }

    private fun validLimit(raw: String, requestedLimit: Int): Boolean {
        return raw.toInt() == requestedLimit
            && requestedLimit >= 1
            && requestedLimit <= 20
    }

    private fun encode(raw: String): String {
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
        val signature = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(sign(payload))
        return "$payload.$signature"
    }

    private fun sign(payload: String): ByteArray {
        try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(signingKey)
            mac.update(SIGNING_DOMAIN)
            return mac.doFinal(payload.toByteArray(StandardCharsets.US_ASCII))
        } catch (exception: GeneralSecurityException) {
            throw IllegalStateException("运行环境不支持 HMAC-SHA256", exception)
        }
    }

    sealed interface Cursor {
        val viewerKey: String
        val limit: Int
    }

    data class SnapshotCursor(
        override val viewerKey: String,
        val mode: String,
        val snapshotId: String,
        override val limit: Int,
        val offset: Int
    ) : Cursor

    data class TimeCursor(
        override val viewerKey: String,
        override val limit: Int,
        val publishedAt: Instant?,
        val postId: Long
    ) : Cursor

    companion object {
        private const val MAX_LENGTH = 512
        private val SIGNING_DOMAIN =
            "eligo-recommendation-cursor-v1\n".toByteArray(StandardCharsets.UTF_8)
    }
}
