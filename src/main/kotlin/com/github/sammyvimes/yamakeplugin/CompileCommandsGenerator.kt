package com.github.sammyvimes.yamakeplugin

import com.intellij.execution.process.KillableProcessHandler
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.project.getProjectDataPath
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path

object CompileCommandsGenerator {
    fun generateCode(project: Project, yaMakeFile: String): KillableProcessHandler {
        val yaPath = YaSettings.getInstance().state.yaPath

        val codegenFolder: Path = project.getProjectDataPath("yacodegen")

        val commandLine = GeneralCommandLine(
            // Start codegen
            yaPath, "make",
            "--force-build-depends", "--replace-result", "--keep-going",
            "--output=$codegenFolder",
            "--add-result=.h",
            "--add-result=.hh",
            "--add-result=.hpp",
            "--add-result=.inc",
            "--add-result=.c",
            "--add-result=.cc",
            "--add-result=.cpp",
            "--add-result=.C",
            "--add-result=.cxx",
            "--no-src-links",
            "-DTRAVERSE_RECURSE_FOR_TESTS=yes",
            // End codegen
        )

        commandLine.setWorkDirectory(yaMakeFile)

        val killableProcessHandler = KillableProcessHandler(commandLine)

        return killableProcessHandler
    }

    fun generateCompileCommands(project: Project, yaMakeFile: String): KillableProcessHandler {
        val yaPath = YaSettings.getInstance().state.yaPath

        val codegenFolder = project.getProjectDataPath("yacodegen")

        val commandLine = GeneralCommandLine(
            // Start compile_commands.json generation
            yaPath, "dump", "compile-commands",
            "--force-build-depends",
            "--cmd-build-root=$codegenFolder",
            "--output-file=${project.basePath}/compile_commands.json",
            // End compile_commands.json generation
        )

        commandLine.setWorkDirectory(yaMakeFile)

        return KillableProcessHandler(commandLine)
    }
}
