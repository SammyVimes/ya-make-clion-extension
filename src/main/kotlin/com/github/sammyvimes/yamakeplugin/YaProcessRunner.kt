package com.github.sammyvimes.yamakeplugin

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import java.nio.file.Path

object YaProcessRunner {
    fun run(
        executable: Path,
        arguments: List<String>,
        workingDirectory: Path,
        timeoutMillis: Int = 0,
    ): ProcessOutput {
        val commandLine = GeneralCommandLine(executable.toString())
            .withParameters(arguments)
            .withWorkDirectory(workingDirectory.toFile())

        val output = if (timeoutMillis > 0) {
            CapturingProcessHandler(commandLine).runProcess(timeoutMillis)
        } else {
            CapturingProcessHandler(commandLine).runProcess()
        }

        if (!output.isExitCodeSet || output.exitCode != 0 || output.isTimeout || output.isCancelled) {
            val details = output.stderr.ifBlank { output.stdout }.trim().takeLast(8_000)
            throw ExecutionException(
                buildString {
                    append("Command failed: ")
                    append(commandLine.commandLineString)
                    if (details.isNotBlank()) {
                        append("\n")
                        append(details)
                    }
                },
            )
        }

        return output
    }
}
