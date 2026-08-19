package com.github.sammyvimes.yamakeplugin

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

class YaCompilationDatabaseTest {
    @Test
    fun `uses Ya compilers and matches Ya IDE include rewriting`() {
        val database = JsonParser.parseString(
            """
            [
              {"command":"clang++ -I/source -include config.h source.cpp"},
              {"command":"clang -I /headers source.c"},
              {"arguments":["clang++", "source.cpp"]}
            ]
            """.trimIndent(),
        ).asJsonArray

        YaCompilationDatabase.patch(database, "/ya/clang++", "/ya/clang")

        assertEquals(
            "/ya/clang++ -isystem/source -include config.h source.cpp",
            database[0].asJsonObject["command"].asString,
        )
        assertEquals(
            "/ya/clang -isystem /headers source.c",
            database[1].asJsonObject["command"].asString,
        )
        assertEquals(false, database[2].asJsonObject.has("command"))
    }
}
