package com.eligo.server.file.storage

import com.eligo.server.file.FileStorageProperties
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("!test")
class LocalFileStorage(properties: FileStorageProperties) : FileStorage {
    private val root: Path = properties.storage.localRoot.toAbsolutePath().normalize()

    override fun save(objectKey: String, input: InputStream, size: Long): StoredObject {
        val target = resolve(objectKey)
        Files.createDirectories(target.parent)
        val temporary = Files.createTempFile(target.parent, ".upload-", ".tmp")
        try {
            val copied = Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING)
            if (copied != size) {
                throw IOException("文件大小不匹配")
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            return StoredObject(objectKey, copied)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    override fun open(objectKey: String): InputStream = Files.newInputStream(resolve(objectKey))

    override fun delete(objectKey: String) {
        Files.deleteIfExists(resolve(objectKey))
    }

    override fun listObjectKeysOlderThan(cutoff: Instant): List<String> {
        if (!Files.isDirectory(root)) {
            return emptyList()
        }
        Files.walk(root, 2).use { paths ->
            return paths
                .filter(Files::isRegularFile)
                .filter { path -> olderThan(path, cutoff) }
                .map(root::relativize)
                .map { path -> path.toString().replace('\\', '/') }
                .filter(this::validObjectKey)
                .sorted()
                .toList()
        }
    }

    private fun olderThan(path: Path, cutoff: Instant): Boolean {
        return try {
            Files.getLastModifiedTime(path).toInstant().isBefore(cutoff)
        } catch (exception: IOException) {
            false
        }
    }

    private fun validObjectKey(objectKey: String): Boolean {
        return objectKey.matches(Regex("(avatars|activities|posts)/[0-9a-f-]+\\.(jpg|png)")) ||
            objectKey.matches(Regex("exports/[0-9a-f-]+\\.zip"))
    }

    private fun resolve(objectKey: String): Path {
        if (!validObjectKey(objectKey)) {
            throw IllegalArgumentException("对象键非法")
        }
        val target = root.resolve(objectKey).normalize()
        if (!target.startsWith(root)) {
            throw IllegalArgumentException("对象键越界")
        }
        return target
    }
}
