package com.github.sammyvimes.yamakeplugin

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.system.OS
import com.jetbrains.cidr.cpp.toolchains.CPPDebugger
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains
import java.nio.file.Files
import java.nio.file.Paths

private val LOG = logger<YaToolchainConfigurator>()

/**
 * Keeps the CLion debugger pointed at `ya tool gdb`.
 *
 * ydb links debug info compressed with zstd (ymake_conf: --compress-debug-sections=zstd
 * for every non-release Linux build); the bundled GDB/LLDB cannot read such binaries and
 * fail with "not in executable format". Only ya's own gdb can debug them, and its
 * versioned path under ~/.ya/tools changes whenever the tool is updated, so it is
 * re-resolved on every project refresh.
 */
object YaToolchainConfigurator {
    fun ensureYaGdb(project: Project, gdbPath: String) {
        if (!Files.isExecutable(Paths.get(gdbPath))) {
            LOG.warn("ya gdb path is not executable: $gdbPath")
            return
        }
        ApplicationManager.getApplication().invokeLater {
            // Toolchain mutations touch application-level state and require the write lock.
            ApplicationManager.getApplication().runWriteAction {
                val toolchains = CPPToolchains.getInstance()
                val toolchain = toolchains.defaultToolchain
                if (toolchain == null) {
                    val created = CPPToolchains.Toolchain(OS.CURRENT).apply {
                        name = "Ya"
                        debugger = CPPDebugger.customGdb(gdbPath)
                    }
                    toolchains.beginUpdate()
                    try {
                        toolchains.addToolchain(created)
                    } finally {
                        toolchains.endUpdate()
                    }
                    notify(project, "Created toolchain 'Ya' with ya gdb: $gdbPath")
                    return@runWriteAction
                }

                val debugger = toolchain.debugger
                val currentPath = debugger.customPath
                when {
                    debugger.kind == CPPDebugger.Kind.CUSTOM_GDB && currentPath == gdbPath -> Unit
                    // Bundled debuggers cannot read zstd debug info; a ya-tools path that
                    // stopped existing is a stale resolve. Anything else is a deliberate
                    // user choice — suggest, do not override.
                    debugger.kind == CPPDebugger.Kind.BUNDLED_GDB ||
                        debugger.kind == CPPDebugger.Kind.BUNDLED_LLDB ||
                        (currentPath != null && currentPath.contains("/.ya/tools/") && !Files.exists(Paths.get(currentPath))) -> {
                        toolchains.beginUpdate()
                        try {
                            toolchain.debugger = CPPDebugger.customGdb(gdbPath)
                        } finally {
                            toolchains.endUpdate()
                        }
                        notify(project, "Toolchain '${toolchain.name}' now debugs with ya gdb: $gdbPath")
                    }
                    else -> notify(
                        project,
                        "Toolchain '${toolchain.name}' uses a custom debugger ($currentPath); " +
                            "ydb debug binaries need ya gdb: $gdbPath",
                    )
                }
            }
        }
    }

    private fun notify(project: Project, message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Ya Make")
            .createNotification(message, NotificationType.INFORMATION)
            .notify(project)
    }
}
