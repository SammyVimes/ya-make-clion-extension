package com.github.sammyvimes.yamakeplugin

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.jetbrains.cidr.cpp.compdb.actions.CompDBLoadProjectAction
import java.nio.file.Files
import java.nio.file.Path

private val LOG = logger<YaProjectRefreshService>()

@Service(Service.Level.PROJECT)
class YaProjectRefreshService(private val project: Project) {
    fun refresh(target: Path) {
        val normalizedTarget = target.toAbsolutePath().normalize()
        YaProjectSettings.getInstance(project).selectTarget(normalizedTarget)

        object : Task.Backgroundable(project, "Refreshing Ya C++ project", true) {
            override fun run(indicator: ProgressIndicator) {
                val settings = YaProjectSettings.getInstance(project)
                val root = settings.projectRoot()
                val codegen = settings.codegenPath()
                val compilationDatabase = settings.compileCommandsPath()
                Files.createDirectories(codegen)
                Files.createDirectories(compilationDatabase.parent)

                indicator.text = "Running Ya code generation"
                YaProcessRunner.run(
                    settings.yaPath(),
                    codegenArguments(settings, codegen, normalizedTarget),
                    root,
                )

                indicator.checkCanceled()
                indicator.text = "Generating compile_commands.json"
                YaProcessRunner.run(
                    settings.yaPath(),
                    compileCommandsArguments(settings, codegen, compilationDatabase, normalizedTarget),
                    root,
                )

                indicator.checkCanceled()
                indicator.text = "Resolving Ya compiler tools"
                patchCompilationDatabase(settings)

                indicator.checkCanceled()
                indicator.text = "Resolving Ya debugger"
                runCatching {
                    val gdbPath = YaProcessRunner.run(
                        settings.yaPath(),
                        listOf("tool", "gdb", "--print-path"),
                        root,
                    ).stdout.trim().lines().last()
                    YaToolchainConfigurator.ensureYaGdb(project, gdbPath)
                }.onFailure { LOG.warn("Failed to resolve ya gdb", it) }
                YaTestMetadataService.getInstance(project).run {
                    invalidate(normalizedTarget)
                    runCatching { resolve(normalizedTarget) }
                }

                LocalFileSystem.getInstance().refreshAndFindFileByNioFile(codegen)?.refresh(false, true)
                LocalFileSystem.getInstance().refreshAndFindFileByNioFile(compilationDatabase)?.refresh(false, false)
            }

            override fun onSuccess() {
                reloadCompilationDatabase()
                notify("Ya project refreshed", NotificationType.INFORMATION)
            }

            override fun onThrowable(error: Throwable) {
                notify(error.message ?: "Ya project refresh failed", NotificationType.ERROR)
            }
        }.queue()
    }

    private fun codegenArguments(settings: YaProjectSettings, codegen: Path, target: Path): List<String> = buildList {
        addAll(
            listOf(
                "make",
                "--force-build-depends",
                "--replace-result",
                "--keep-going",
                "--output=$codegen",
                "--prefetch",
            ),
        )
        addAll(settings.configurationArgs())
        listOf(".h", ".hh", ".hpp", ".inc", ".c", ".cc", ".cpp", ".C", ".cxx").forEach {
            add("--add-result=$it")
        }
        add("--no-src-links")
        add("-DTRAVERSE_RECURSE_FOR_TESTS=yes")
        add(target.toString())
    }

    private fun compileCommandsArguments(settings: YaProjectSettings, codegen: Path, output: Path, target: Path): List<String> = buildList {
        addAll(
            listOf(
                "dump",
                "compile-commands",
                "--force-build-depends",
                "--cmd-build-root=$codegen",
                "--output-file=$output",
                "--prefetch",
            ),
        )
        addAll(settings.configurationArgs())
        add("-DTRAVERSE_RECURSE_FOR_TESTS=yes")
        add(target.toString())
    }

    private fun patchCompilationDatabase(settings: YaProjectSettings) {
        val cxx = YaProcessRunner.run(
            settings.yaPath(),
            listOf("tool", "c++", "--print-path"),
            settings.projectRoot(),
        ).stdout.trim()
        val cc = YaProcessRunner.run(
            settings.yaPath(),
            listOf("tool", "cc", "--print-path"),
            settings.projectRoot(),
        ).stdout.trim()

        val file = settings.compileCommandsPath().toFile()
        val database = JsonParser.parseReader(file.reader()).asJsonArray
        YaCompilationDatabase.patch(database, cxx, cc)
        file.writer().use { GsonBuilder().setPrettyPrinting().create().toJson(database, it) }
    }

    private fun reloadCompilationDatabase() {
        ApplicationManager.getApplication().invokeLater {
            val database = LocalFileSystem.getInstance()
                .refreshAndFindFileByNioFile(YaProjectSettings.getInstance(project).compileCommandsPath())
                ?: return@invokeLater
            CompDBLoadProjectAction().performLink(project, database)
        }
    }

    private fun notify(message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Ya Make")
            .createNotification(message, type)
            .notify(project)
    }

    companion object {
        fun getInstance(project: Project): YaProjectRefreshService = project.service()
    }
}
