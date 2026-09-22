package com.eligo.server.recommendation

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Arrays

class RecommendationCursorCodecTests {

    private val codec = RecommendationCursorCodec(key(7))

    @Test
    fun snapshotCursorBindsViewerModeSnapshotLimitAndOffset() {
        val cursor = codec.encodeSnapshot("202L", "VECTOR", "snapshot-1", 20, 40)

        assertThat(codec.decode(cursor, "202L", 20))
            .isEqualTo(
                RecommendationCursorCodec.SnapshotCursor(
                    "202L", "VECTOR", "snapshot-1", 20, 40
                )
            )
        assertThatThrownBy { codec.decode(cursor, "303L", 20) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { codec.decode(cursor, "202L", 10) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun timeCursorHasIndependentModeAndStrictShape() {
        val time = Instant.parse("2026-08-18T08:00:00Z")
        val cursor = codec.encodeTime("anonymous", 20, time, 701L)

        assertThat(codec.decode(cursor, "anonymous", 20))
            .isEqualTo(
                RecommendationCursorCodec.TimeCursor(
                    "anonymous", 20, time, 701L
                )
            )
        assertThatThrownBy { codec.decode("not-a-cursor", "anonymous", 20) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun rejectsAnyCursorMutationAndSignaturesFromAnotherKey() {
        val snapshot = codec.encodeSnapshot("202L", "VECTOR", "snapshot-1", 20, 40)
        val time = codec.encodeTime(
            "202L", 20, Instant.parse("2026-08-18T08:00:00Z"), 701L
        )
        val other = RecommendationCursorCodec(key(8))

        assertThatThrownBy { codec.decode(tamper(snapshot), "202L", 20) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { codec.decode(tamper(time), "202L", 20) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { other.decode(snapshot, "202L", 20) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun key(value: Byte): ByteArray {
        val key = ByteArray(32)
        Arrays.fill(key, value)
        return key
    }

    private fun tamper(cursor: String): String {
        val index = cursor.length / 3
        val replacement = if (cursor[index] == 'A') 'B' else 'A'
        return cursor.substring(0, index) + replacement + cursor.substring(index + 1)
    }
}
