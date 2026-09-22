package com.eligo.server.file

import java.nio.file.Path
import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "eligo.file")
class FileStorageProperties {
    var storage: Storage = Storage()
    var image: Image = Image()
    var temporaryTtl: Duration = Duration.ofHours(24)
    var export: Export = Export()

    class Storage {
        var provider: String = "LOCAL"
        var localRoot: Path = Path.of(".data/eligo-files")
    }

    class Export {
        var maxFiles: Int = 20
        var maxStructuredBytes: Long = 5L * 1024 * 1024
        var maxTotalBytes: Long = 50L * 1024 * 1024
    }

    class Image {
        var allowedContentTypes: List<String> = listOf("image/jpeg", "image/png")
        var maxSize: String = "10MB"
    }
}
