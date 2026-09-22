package com.eligo.server.favorite

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ActivityEngagementModuleBoundaryTests {

    @Test
    fun m3DoesNotImportActivityMapperOrEntityAndM2DoesNotImportM3Persistence() {
        assertNoForbiddenImport(
            Path.of("src/main/kotlin/com/eligo/server/favorite"),
            listOf(
                "com.eligo.server.activity.mapper",
                "com.eligo.server.activity.entity"
            )
        )
        assertNoForbiddenImport(
            Path.of("src/main/kotlin/com/eligo/server/comment"),
            listOf(
                "com.eligo.server.activity.mapper",
                "com.eligo.server.activity.entity"
            )
        )
        assertNoForbiddenImport(
            Path.of("src/main/kotlin/com/eligo/server/activity"),
            listOf(
                "com.eligo.server.favorite.mapper",
                "com.eligo.server.favorite.entity",
                "com.eligo.server.comment.mapper",
                "com.eligo.server.comment.entity"
            )
        )
    }

    private fun assertNoForbiddenImport(root: Path, forbidden: List<String>) {
        Files.walk(root).use { paths ->
            for (source in paths.filter { path -> path.toString().endsWith(".kt") }.toList()) {
                val content = Files.readString(source)
                assertThat(forbidden)
                    .`as`("%s 不得出现跨模块持久层依赖", source)
                    .noneMatch { content.contains(it) }
            }
        }
    }
}
