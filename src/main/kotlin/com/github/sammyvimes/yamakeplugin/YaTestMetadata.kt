package com.github.sammyvimes.yamakeplugin

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.execution.ExecutionException
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

data class YaTestMetadata(
    val targetPath: Path,
    val binaryPath: Path,
    val sourcePath: Path,
    val workingDirectory: Path,
    val contextFile: Path,
    val testName: String,
    val size: String,
)

@Service(Service.Level.PROJECT)
class YaTestMetadataService(private val project: Project) {
    private val entriesCache = ConcurrentHashMap<Path, List<JsonObject>>()

    fun resolve(target: Path, sourceFile: Path? = null): YaTestMetadata =
        loadMetadata(target.toAbsolutePath().normalize(), sourceFile?.toAbsolutePath()?.normalize())

    fun invalidate(target: Path? = null) {
        if (target == null) {
            entriesCache.clear()
        } else {
            entriesCache.remove(target.toAbsolutePath().normalize())
        }
    }

    private fun loadMetadata(queryTarget: Path, sourceFile: Path?): YaTestMetadata {
        val settings = YaProjectSettings.getInstance(project)
        val root = settings.projectRoot()
        val entries = entriesCache.computeIfAbsent(queryTarget) { loadEntries(it, settings, root) }
        val dartInfo = YaTestMetadataParser.select(entries, root, queryTarget, sourceFile)

        val binaryRelative = dartInfo.requiredString("BINARY-PATH")
        val sourceRelative = dartInfo.requiredString("SOURCE-FOLDER-PATH")
        val script = dartInfo.string("SCRIPT-REL-PATH")
        val testName = dartInfo.string("TEST-NAME").ifBlank { queryTarget.fileName.toString() }
        val resultDirectoryName = when {
            script.contains("unittest", ignoreCase = true) -> "unittest"
            script.contains("gtest", ignoreCase = true) -> "gtest"
            else -> testName
        }
        val sourcePath = root.resolve(sourceRelative)
        val workingDirectory = sourcePath.resolve("test-results").resolve(resultDirectoryName)

        return YaTestMetadata(
            targetPath = sourcePath,
            binaryPath = root.resolve(binaryRelative),
            sourcePath = sourcePath,
            workingDirectory = workingDirectory,
            contextFile = workingDirectory.resolve("test.context"),
            testName = testName,
            size = dartInfo.string("SIZE"),
        )
    }

    private fun loadEntries(target: Path, settings: YaProjectSettings, root: Path): List<JsonObject> {
        val output = YaProcessRunner.run(
            settings.yaPath(),
            buildList {
                add("dump")
                add("json-test-list")
                addAll(settings.configurationArgs())
                add(target.toString())
            },
            root,
        )
        return JsonParser.parseString(output.stdout).asJsonArray.map { it.asJsonObject }
    }

    private fun JsonObject.string(name: String): String = get(name)?.takeUnless { it.isJsonNull }?.asString.orEmpty()

    private fun JsonObject.requiredString(name: String): String = string(name).ifBlank {
        throw ExecutionException("ya dump json-test-list did not return $name")
    }

    companion object {
        fun getInstance(project: Project): YaTestMetadataService = project.service()
    }
}

internal object YaTestMetadataParser {
    fun select(
        entries: List<JsonObject>,
        root: Path,
        queryTarget: Path,
        sourceFile: Path?,
        readYaMake: (String) -> String? = { module ->
            runCatching { java.nio.file.Files.readString(root.resolve(module).resolve("ya.make")) }.getOrNull()
        },
    ): JsonObject {
        val binaryTests = entries
            .mapNotNull { it.getAsJsonObject("dart_info") }
            .filter { it.string("BINARY-PATH").isNotBlank() }

        // Gutter tests are Y_UNIT_TEST: only unittest binaries can run them, while ya
        // also reports benchmarks (g_benchmark), fuzzers and linters as tests.
        val unitTests = binaryTests
            .filter { it.string("SCRIPT-REL-PATH").startsWith("unittest") }
            .ifEmpty { binaryTests }

        val sourceRelative = sourceFile
            ?.takeIf { it.startsWith(root) }
            ?.let(root::relativize)
            ?.toString()
            ?.replace(root.fileSystem.separator, "/")
        if (sourceRelative != null) {
            unitTests.firstOrNull { it.containsSourceFile(sourceRelative) }?.let { return it }
        }

        // UNITTEST_FOR keeps its sources in the parent directory and lists them in the
        // test module's ya.make, so the module whose ya.make references the file is its
        // real owner — the build system's answer, not a guess.
        val sourceName = sourceFile?.fileName?.toString()
        if (sourceName != null) {
            val sourceToken = Regex("(^|[\\s(/])${Regex.escape(sourceName)}([\\s)]|$)")
            unitTests.firstOrNull { entry ->
                readYaMake(entry.string("SOURCE-FOLDER-PATH"))?.contains(sourceToken) == true
            }?.let { return it }
        }

        val targetRelative = root.relativize(queryTarget).toString().replace(queryTarget.fileSystem.separator, "/")
        return unitTests.firstOrNull { it.string("SOURCE-FOLDER-PATH") == targetRelative }
            ?: unitTests.singleOrNull()
            // Last-resort tie-breaker: "ut" is the conventional default module,
            // specialized ones (ut_large, ut_pg, ...) are opt-in.
            ?: unitTests.firstOrNull { it.string("SOURCE-FOLDER-PATH").substringAfterLast('/') == "ut" }
            ?: unitTests.firstOrNull()
            ?: throw ExecutionException("No C++ unittest binary found for $queryTarget")
    }

    // TEST-FILES historically listed source files with a $(SOURCE_ROOT)/ prefix; modern
    // ya emits the test module directory with an arcadia/ prefix instead.
    private fun JsonObject.containsSourceFile(relativePath: String): Boolean =
        sequenceOf("TEST-FILES", "FILES")
            .mapNotNull { get(it)?.takeIf { value -> value.isJsonArray }?.asJsonArray }
            .flatMap { it.asSequence() }
            .mapNotNull { it.takeUnless { value -> value.isJsonNull }?.asString }
            .map { it.removePrefix("\$(SOURCE_ROOT)/").removePrefix("arcadia/") }
            .any { entry -> entry == relativePath || relativePath.startsWith("$entry/") }

    private fun JsonObject.string(name: String): String =
        get(name)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
}
