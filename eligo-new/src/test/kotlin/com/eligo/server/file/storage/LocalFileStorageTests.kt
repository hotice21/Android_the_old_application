package com.eligo.server.file.storage

import com.eligo.server.file.FileStorageProperties
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class LocalFileStorageTests {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun openRejectsParentTraversalObjectKey() {
        assertRejectsOpen("../outside.jpg")
    }

    @Test
    fun openRejectsAbsoluteObjectKey() {
        assertRejectsOpen("/tmp/outside.jpg")
    }

    @Test
    fun openRejectsObjectKeyThatNormalizesBeyondRoot() {
        assertRejectsOpen("avatars/../../outside.jpg")
    }

    @Test
    fun savesAndOpensWhitelistedObjectKeyUnderConfiguredRoot() {
        val storage = storage()
        val content = byteArrayOf(1, 2, 3)
        val objectKey = "avatars/7b5e7bb8-38b9-40d2-99bd-e07fd4dc0053.jpg"

        storage.save(objectKey, ByteArrayInputStream(content), content.size.toLong())

        storage.open(objectKey).use { input ->
            assertArrayEquals(content, input.readAllBytes())
        }
        assertArrayEquals(content, Files.readAllBytes(temporaryDirectory.resolve(objectKey)))
    }

    @Test
    fun savesAndOpensPrivateExportZipUnderConfiguredRoot() {
        val storage = storage()
        val content = byteArrayOf(80, 75, 3, 4)
        val objectKey = "exports/7b5e7bb8-38b9-40d2-99bd-e07fd4dc0053.zip"

        storage.save(objectKey, ByteArrayInputStream(content), content.size.toLong())

        storage.open(objectKey).use { input ->
            assertArrayEquals(content, input.readAllBytes())
        }
    }

    @Test
    fun savesAndOpensActivityAndPostImageKeysUnderConfiguredRoot() {
        val storage = storage()
        val content = byteArrayOf(9, 8, 7)

        for (objectKey in arrayOf(
            "activities/7b5e7bb8-38b9-40d2-99bd-e07fd4dc0053.jpg",
            "posts/7b5e7bb8-38b9-40d2-99bd-e07fd4dc0054.png"
        )) {
            storage.save(objectKey, ByteArrayInputStream(content), content.size.toLong())
            storage.open(objectKey).use { input ->
                assertArrayEquals(content, input.readAllBytes())
            }
        }
    }

    private fun assertRejectsOpen(objectKey: String) {
        assertThrows(IllegalArgumentException::class.java) { storage().open(objectKey) }
    }

    private fun storage(): LocalFileStorage {
        val properties = FileStorageProperties()
        properties.storage.localRoot = temporaryDirectory
        return LocalFileStorage(properties)
    }
}
