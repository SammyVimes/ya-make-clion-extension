package com.github.sammyvimes.yamakeplugin

import com.intellij.execution.BeforeRunTask
import com.intellij.execution.BeforeRunTaskProvider
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.jetbrains.cidr.cpp.execution.external.run.CLionExternalRunConfiguration
import org.jdom.Element
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.Icon

class YaPrepareTestBeforeRunTaskProvider : BeforeRunTaskProvider<YaPrepareTestTask>() {
    override fun getId(): Key<YaPrepareTestTask> = ID

    override fun getName(): String = "Prepare Ya test"

    override fun getIcon(): Icon = AllIcons.Actions.Compile

    override fun createTask(runConfiguration: RunConfiguration): YaPrepareTestTask = YaPrepareTestTask()

    override fun executeTask(
        context: DataContext,
        configuration: RunConfiguration,
        environment: ExecutionEnvironment,
        task: YaPrepareTestTask,
    ): Boolean {
        val project = configuration.project
        val target = task.targetPath.takeIf(String::isNotBlank)?.let(Paths::get) ?: return false
        val settings = YaProjectSettings.getInstance(project)

        val built = try {
            YaBuildRunner.run(
                project,
                "Prepare Ya test: ${target.fileName ?: target}",
                settings.yaPath(),
                buildList {
                    add("test")
                    // Plain per-node status lines: without -T a non-TTY ya stays silent
                    // until the very end, leaving the Build console empty.
                    add("-T")
                    add("--run-all-tests")
                    add("--regular-tests")
                    add("--keep-going")
                    add("--test-prepare")
                    add("--keep-temps")
                    add("--prefetch")
                    add("--ignore-recurses")
                    addAll(settings.configurationArgs())
                    add(target.toString())
                },
                settings.projectRoot(),
            )
        } catch (error: Throwable) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Ya Make")
                .createNotification(error.message ?: "Failed to prepare Ya test", NotificationType.ERROR)
                .notify(project)
            false
        }
        return built && verifyTestBinary(configuration)
    }

    // A cancelled link can leave a truncated file behind the ya symres symlink; the
    // debugger then fails with a cryptic "not in executable format". Catch it here
    // with an actionable message instead.
    private fun verifyTestBinary(configuration: RunConfiguration): Boolean {
        val binaryPath = (configuration as? CLionExternalRunConfiguration)
            ?.executableData?.path?.takeIf(String::isNotBlank)?.let(Paths::get)
            ?: return true
        if (isExecutableImage(binaryPath)) return true
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Ya Make")
            .createNotification(
                "Test binary is missing or corrupted (interrupted build?): $binaryPath — rerun the test to rebuild it",
                NotificationType.ERROR,
            )
            .notify(configuration.project)
        return false
    }

    private fun isExecutableImage(path: Path): Boolean {
        val magic = try {
            Files.newInputStream(path).use { it.readNBytes(4) }
        } catch (_: Exception) {
            return false
        }
        if (magic.size < 4) return false
        return magic.contentEquals(byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte())) ||
            magic.contentEquals(byteArrayOf(0xCF.toByte(), 0xFA.toByte(), 0xED.toByte(), 0xFE.toByte())) ||
            magic.contentEquals(byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte()))
    }

    companion object {
        val ID: Key<YaPrepareTestTask> = Key.create("YaPrepareTestTask")

        fun getInstance(project: Project): YaPrepareTestBeforeRunTaskProvider? =
            BeforeRunTaskProvider.getProvider(project, ID) as? YaPrepareTestBeforeRunTaskProvider
    }
}

class YaPrepareTestTask : BeforeRunTask<YaPrepareTestTask>(YaPrepareTestBeforeRunTaskProvider.ID) {
    var targetPath: String = ""

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.setAttribute("targetPath", targetPath)
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        targetPath = element.getAttributeValue("targetPath").orEmpty()
    }
}
