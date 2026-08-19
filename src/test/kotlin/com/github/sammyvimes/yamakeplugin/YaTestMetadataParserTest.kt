package com.github.sammyvimes.yamakeplugin

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Paths

class YaTestMetadataParserTest {
    @Test
    fun `selects UNITTEST_FOR binary by source file and skips lint entries`() {
        val entries = JsonParser.parseString(
            """
            [
              {"dart_info":{
                "SOURCE-FOLDER-PATH":"util/charset",
                "SCRIPT-REL-PATH":"custom_lint",
                "TEST-FILES":["${'$'}(SOURCE_ROOT)/util/charset/utf8.cpp"]
              }},
              {"dart_info":{
                "SOURCE-FOLDER-PATH":"util/charset/ut",
                "SCRIPT-REL-PATH":"unittest.py",
                "BINARY-PATH":"util/charset/ut/util-charset-ut",
                "TEST-FILES":["${'$'}(SOURCE_ROOT)/util/charset/utf8_ut.cpp"]
              }}
            ]
            """.trimIndent(),
        ).asJsonArray.map { it.asJsonObject }
        val root = Paths.get("/arcadia")

        val selected = YaTestMetadataParser.select(
            entries,
            root,
            root.resolve("util/charset"),
            root.resolve("util/charset/utf8_ut.cpp"),
        )

        assertEquals("util/charset/ut", selected["SOURCE-FOLDER-PATH"].asString)
        assertEquals("util/charset/ut/util-charset-ut", selected["BINARY-PATH"].asString)
    }

    @Test
    fun `skips benchmark binaries and prefers the conventional ut module`() {
        val entries = ydbStyleEntries()
        val root = Paths.get("/arcadia")

        val selected = YaTestMetadataParser.select(
            entries,
            root,
            root.resolve("ydb/core/tablet_flat"),
            root.resolve("ydb/core/tablet_flat/flat_executor_ut.cpp"),
        )

        assertEquals("ydb/core/tablet_flat/ut", selected["SOURCE-FOLDER-PATH"].asString)
    }

    @Test
    fun `resolves owning module through ya make sources for UNITTEST_FOR layouts`() {
        val entries = ydbStyleEntries()
        val root = Paths.get("/arcadia")
        val yaMakes = mapOf(
            "ydb/core/tablet_flat/ut" to "UNITTEST_FOR(ydb/core/tablet_flat)\nSRCS(\n    flat_executor_ut.cpp\n)\n",
            "ydb/core/tablet_flat/ut_large" to "UNITTEST_FOR(ydb/core/tablet_flat)\nSRCS(\n    flat_executor_large_ut.cpp\n)\nSIZE(LARGE)\n",
            "ydb/core/tablet_flat/benchmark" to "G_BENCHMARK()\nSRCS(b_part.cpp)\n",
        )

        val selected = YaTestMetadataParser.select(
            entries,
            root,
            root.resolve("ydb/core/tablet_flat"),
            root.resolve("ydb/core/tablet_flat/flat_executor_large_ut.cpp"),
            readYaMake = { yaMakes[it] },
        )

        assertEquals("ydb/core/tablet_flat/ut_large", selected["SOURCE-FOLDER-PATH"].asString)
    }

    @Test
    fun `matches source file inside a test module directory`() {
        val entries = ydbStyleEntries()
        val root = Paths.get("/arcadia")

        val selected = YaTestMetadataParser.select(
            entries,
            root,
            root.resolve("ydb/core/tablet_flat"),
            root.resolve("ydb/core/tablet_flat/ut_large/flat_executor_large_ut.cpp"),
        )

        assertEquals("ydb/core/tablet_flat/ut_large", selected["SOURCE-FOLDER-PATH"].asString)
    }

    private fun ydbStyleEntries() = JsonParser.parseString(
        """
        [
          {"dart_info":{
            "SOURCE-FOLDER-PATH":"ydb/core/tablet_flat/benchmark",
            "SCRIPT-REL-PATH":"g_benchmark",
            "BINARY-PATH":"ydb/core/tablet_flat/benchmark/core_tablet_flat_benchmark",
            "TEST-FILES":["arcadia/ydb/core/tablet_flat/benchmark"]
          }},
          {"dart_info":{
            "SOURCE-FOLDER-PATH":"ydb/core/tablet_flat/ut_large",
            "SCRIPT-REL-PATH":"unittest.py",
            "BINARY-PATH":"ydb/core/tablet_flat/ut_large/ydb-core-tablet_flat-ut_large",
            "TEST-FILES":["arcadia/ydb/core/tablet_flat/ut_large"]
          }},
          {"dart_info":{
            "SOURCE-FOLDER-PATH":"ydb/core/tablet_flat/ut",
            "SCRIPT-REL-PATH":"unittest.py",
            "BINARY-PATH":"ydb/core/tablet_flat/ut/ydb-core-tablet_flat-ut",
            "TEST-FILES":["arcadia/ydb/core/tablet_flat/ut"]
          }}
        ]
        """.trimIndent(),
    ).asJsonArray.map { it.asJsonObject }
}
