package com.github.sammyvimes.yamakeplugin

import com.google.gson.JsonArray

internal object YaCompilationDatabase {
    fun patch(database: JsonArray, cxx: String, cc: String) {
        database.forEach { element ->
            val commandObject = element.asJsonObject
            val command = commandObject.get("command")?.asString ?: return@forEach
            val compilerFixed = when {
                command.startsWith("clang++ ") -> cxx + command.removePrefix("clang++")
                command.startsWith("clang ") -> cc + command.removePrefix("clang")
                else -> command
            }
            commandObject.addProperty("command", compilerFixed.replace(" -I", " -isystem"))
        }
    }
}
