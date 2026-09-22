package com.eligo.server.contract

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class ModuleBoundaryTest {

    private companion object {
        val SOURCE_ROOT = Path.of("src/main/kotlin/com/eligo/server")
    }

    @Test
    fun m2DoesNotImportM3PersistenceTypes() {
        assertNoForbiddenImports(
            SOURCE_ROOT.resolve("activity"),
            listOf(
                "import com.eligo.server.participation.mapper.",
                "import com.eligo.server.participation.entity."
            )
        )
    }

    @Test
    fun m3DoesNotImportM2OrOrganizationPersistenceTypes() {
        assertNoForbiddenImports(
            SOURCE_ROOT.resolve("participation"),
            listOf(
                "import com.eligo.server.activity.mapper.",
                "import com.eligo.server.activity.entity.",
                "import com.eligo.server.organization.mapper.",
                "import com.eligo.server.organization.entity."
            )
        )
    }

    private fun assertNoForbiddenImports(directory: Path, forbidden: List<String>) {
        val violations = Files.walk(directory).use { paths ->
            paths
                .filter { path -> path.toString().endsWith(".kt") }
                .flatMap { path ->
                    forbidden.stream()
                        .filter { marker -> contains(path, marker) }
                        .map { marker -> "$path -> $marker" }
                }
                .toList()
        }
        assertThat(violations).isEmpty()
    }

    private fun contains(path: Path, marker: String): Boolean {
        try {
            return Files.readString(path).contains(marker)
        } catch (exception: IOException) {
            throw IllegalStateException("读取模块源码失败: $path", exception)
        }
    }
}
