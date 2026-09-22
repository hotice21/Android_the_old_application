package com.eligo.server.file.storage

import java.io.IOException
import java.io.InputStream
import java.time.Instant

interface FileStorage {
    @Throws(IOException::class)
    fun save(objectKey: String, input: InputStream, size: Long): StoredObject
    @Throws(IOException::class)
    fun open(objectKey: String): InputStream
    @Throws(IOException::class)
    fun delete(objectKey: String)
    @Throws(IOException::class)
    fun listObjectKeysOlderThan(cutoff: Instant): List<String>
}
