package com.github.sammyvimes.yamakeplugin

import com.intellij.build.BuildViewManager
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.FailureResultImpl
import com.intellij.build.events.impl.FileMessageEventImpl
import com.intellij.build.events.impl.FinishBuildEventImpl
import com.intellij.build.events.impl.OutputBuildEventImpl
import com.intellij.build.events.impl.StartBuildEventImpl
import com.intellij.build.events.impl.SuccessResultImpl
import com.intellij.build.process.BuildProcessHandler
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.filters.RegexpFilter
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import java.io.File
import java.io.OutputStream
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

// clang/gcc diagnostic line: /abs/path/file.cpp:LINE:COL: error|warning: message
private val COMPILER_DIAGNOSTIC = Regex("""^(/[^:]+?):(\d+):(\d+):\s+(error|warning):\s+(.*)$""")

/**
 * Runs a ya command with its output streamed into the Build tool window, the way
 * CMake and Gradle surface their before-launch builds. Cancelling the surrounding
 * progress indicator (the before-launch task progress) terminates the process.
 */
object YaBuildRunner {
    fun run(
        project: Project,
        title: String,
        executable: Path,
        arguments: List<String>,
        workingDirectory: Path,
    ): Boolean {
        val commandLine = GeneralCommandLine(executable.toString())
            .withParameters(arguments)
            .withWorkDirectory(workingDirectory.toFile())

        val buildId = Any()
        val handler = OSProcessHandler(commandLine)

        // Gives the Build tool window its stop button. Only lifecycle is delegated:
        // output still travels as build events, so the handle must stay silent.
        val stopHandle = object : BuildProcessHandler() {
            init {
                handler.addProcessListener(object : ProcessListener {
                    override fun processTerminated(event: ProcessEvent) = notifyProcessTerminated(event.exitCode)
                })
            }

            override fun getExecutionName(): String = title
            override fun getProcessInput(): OutputStream? = null
            override fun detachIsDefault(): Boolean = false
            override fun destroyProcessImpl() = handler.destroyProcess()
            override fun detachProcessImpl() = notifyProcessDetached()
        }

        val descriptor = DefaultBuildDescriptor(buildId, title, workingDirectory.toString(), System.currentTimeMillis())
            .withProcessHandler(stopHandle, null)
            // Makes file:line:column occurrences in the console cmd-clickable.
            .withExecutionFilter(RegexpFilter(project, "${RegexpFilter.FILE_PATH_MACROS}:${RegexpFilter.LINE_MACROS}:${RegexpFilter.COLUMN_MACROS}"))
            .apply { isActivateToolWindowWhenAdded = true }
        val buildView = project.getService(BuildViewManager::class.java)
        buildView.onEvent(buildId, StartBuildEventImpl(descriptor, commandLine.commandLineString))

        handler.addProcessListener(object : ProcessListener {
            private val pendingLines = HashMap<Key<*>, StringBuilder>()

            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                if (outputType == ProcessOutputTypes.SYSTEM) return
                // ya streams its progress on stderr; forwarding it as stderr would paint
                // the whole build log in error color, so everything goes out as stdout.
                buildView.onEvent(buildId, OutputBuildEventImpl(buildId, event.text, true))
                collectDiagnostics(event.text, outputType).forEach { buildView.onEvent(buildId, it) }
            }

            // Chunks are not line-aligned, so lines are reassembled per stream before
            // matching compiler diagnostics.
            private fun collectDiagnostics(chunk: String, outputType: Key<*>): List<BuildEvent> =
                synchronized(pendingLines) {
                    val buffer = pendingLines.getOrPut(outputType) { StringBuilder() }
                    buffer.append(chunk)
                    val events = mutableListOf<BuildEvent>()
                    while (true) {
                        val newline = buffer.indexOf("\n")
                        if (newline < 0) break
                        val line = buffer.substring(0, newline)
                        buffer.delete(0, newline + 1)
                        parseDiagnostic(line)?.let(events::add)
                    }
                    events
                }

            private fun parseDiagnostic(line: String): BuildEvent? {
                val match = COMPILER_DIAGNOSTIC.find(line.trim()) ?: return null
                val (path, lineNumber, column, kind, message) = match.destructured
                return FileMessageEventImpl(
                    buildId,
                    if (kind == "error") MessageEvent.Kind.ERROR else MessageEvent.Kind.WARNING,
                    "Compiler",
                    message,
                    line,
                    FilePosition(File(path), lineNumber.toInt() - 1, column.toInt() - 1),
                )
            }
        })
        handler.startNotify()
        stopHandle.startNotify()

        // Before-run tasks are not guaranteed a visible progress indicator (there is
        // none in Remote Development), so the build owns one: a cancellable background
        // task whose cancellation terminates ya. The Build window stop button and this
        // progress are two equivalent ways to abort.
        val finished = CompletableFuture<Boolean>()
        object : Task.Backgroundable(project, title, true) {
            override fun run(indicator: ProgressIndicator) {
                var cancelled = false
                while (!handler.waitFor(200)) {
                    if (!cancelled && indicator.isCanceled) {
                        cancelled = true
                        handler.destroyProcess()
                    }
                }
                val exitCode = handler.exitCode
                val success = !cancelled && exitCode == 0
                val finishEvent = when {
                    cancelled -> FinishBuildEventImpl(buildId, null, System.currentTimeMillis(), "cancelled", FailureResultImpl())
                    success -> FinishBuildEventImpl(buildId, null, System.currentTimeMillis(), "finished", SuccessResultImpl())
                    else -> FinishBuildEventImpl(buildId, null, System.currentTimeMillis(), "exit code $exitCode", FailureResultImpl())
                }
                buildView.onEvent(buildId, finishEvent)
                finished.complete(success)
            }

            override fun onThrowable(error: Throwable) {
                handler.destroyProcess()
                finished.completeExceptionally(error)
            }
        }.queue()

        return try {
            finished.get()
        } catch (error: java.util.concurrent.ExecutionException) {
            throw error.cause ?: error
        }
    }
}
