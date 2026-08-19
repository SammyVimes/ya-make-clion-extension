package com.github.sammyvimes.yamakeplugin

import com.intellij.execution.RunManagerEx
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.util.execution.ParametersListUtil
import com.jetbrains.cidr.cpp.execution.external.run.CLionExternalRunConfiguration
import com.jetbrains.cidr.cpp.execution.external.run.CLionExternalRunConfigurationType
import com.jetbrains.cidr.execution.BuildTargetAndConfigurationData
import com.jetbrains.cidr.execution.ExecutableData
import java.nio.file.Paths

class YaUnitTestRunConfigurationProducer : LazyRunConfigurationProducer<CLionExternalRunConfiguration>() {
    override fun setupConfigurationFromContext(
        configuration: CLionExternalRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>,
    ): Boolean {
        val element = context.location?.psiElement ?: return false
        val macro = getYaUnitTestMacro(element) ?: return false
        val fullTestName = getYaUnitTestFullName(macro) ?: return false
        val target = findNearestYaMakeDirectory(element.containingFile?.virtualFile) ?: return false
        val project = context.project
        configuration.name = "$CONFIGURATION_PREFIX$fullTestName"
        configuration.executableData = ExecutableData(YaProjectSettings.getInstance(project).yaPath().toString())
        configuration.programParameters = ParametersListUtil.join(listOf(fullTestName))
        configuration.workingDirectory = YaProjectSettings.getInstance(project).projectRoot().toString()
        configuration.envs = emptyMap()
        configuration.isPassParentEnvs = true
        configuration.putUserData(TARGET_PATH_KEY, target.toString())
        element.containingFile?.virtualFile?.path?.let { configuration.putUserData(SOURCE_FILE_KEY, it) }
        sourceElement.set(macro)
        return true
    }

    override fun onFirstRun(
        configurationFromContext: ConfigurationFromContext,
        context: ConfigurationContext,
        startRunnable: Runnable,
    ) {
        val configuration = configurationFromContext.configuration as? CLionExternalRunConfiguration ?: return
        val target = configuration.getUserData(TARGET_PATH_KEY)?.let(Paths::get) ?: return
        val sourceFile = configuration.getUserData(SOURCE_FILE_KEY)?.let(Paths::get)
        val fullTestName = configuration.name.removePrefix(CONFIGURATION_PREFIX)
        val project = context.project

        object : Task.Backgroundable(project, "Resolving Ya test", true) {
            private lateinit var metadata: YaTestMetadata

            override fun run(indicator: ProgressIndicator) {
                metadata = YaTestMetadataService.getInstance(project).resolve(target, sourceFile)
            }

            override fun onSuccess() {
                try {
                    configureResolvedTest(configuration, fullTestName, metadata)
                    startRunnable.run()
                } catch (error: Throwable) {
                    notifyError(project, error)
                }
            }

            override fun onThrowable(error: Throwable) {
                notifyError(project, error)
            }
        }.queue()
    }

    override fun isConfigurationFromContext(
        configuration: CLionExternalRunConfiguration,
        context: ConfigurationContext,
    ): Boolean {
        val macro = context.psiLocation?.let(::getYaUnitTestMacro) ?: return false
        val fullTestName = getYaUnitTestFullName(macro) ?: return false
        return configuration.name == "$CONFIGURATION_PREFIX$fullTestName"
    }

    override fun getConfigurationFactory(): ConfigurationFactory =
        CLionExternalRunConfigurationType.getInstance().configurationFactories.first()

    internal fun configureResolvedTest(
        configuration: CLionExternalRunConfiguration,
        fullTestName: String,
        metadata: YaTestMetadata,
    ) {
        val project = configuration.project
        val (buildTarget, buildConfiguration) = YaNativeTargetManager.ensure(project)
        configuration.executableData = ExecutableData(metadata.binaryPath.toString())
        configuration.programParameters = ParametersListUtil.join(listOf(fullTestName))
        configuration.workingDirectory = metadata.workingDirectory.toString()
        configuration.envs = mapOf("YA_TEST_CONTEXT_FILE" to metadata.contextFile.toString())
        configuration.isPassParentEnvs = true
        configuration.targetAndConfigurationData = BuildTargetAndConfigurationData(buildTarget, buildConfiguration)

        val provider = YaPrepareTestBeforeRunTaskProvider.getInstance(project)
            ?: throw IllegalStateException("Prepare Ya test provider is unavailable")
        val prepareTask = provider.createTask(configuration).apply {
            targetPath = metadata.targetPath.toString()
        }
        RunManagerEx.getInstanceEx(project).setBeforeRunTasks(configuration, listOf(prepareTask))
    }

    private fun notifyError(project: com.intellij.openapi.project.Project, error: Throwable) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Ya Make")
            .createNotification(error.message ?: "Unable to resolve Ya test", NotificationType.ERROR)
            .notify(project)
    }

    private fun findNearestYaMakeDirectory(file: VirtualFile?): java.nio.file.Path? {
        var directory = if (file?.isDirectory == true) file else file?.parent
        while (directory != null) {
            if (directory.findChild("ya.make") != null) {
                return Paths.get(directory.path).toAbsolutePath().normalize()
            }
            directory = directory.parent
        }
        return null
    }

    companion object {
        const val CONFIGURATION_PREFIX = "Ya Test: "
        private val TARGET_PATH_KEY = Key.create<String>("YaMake.TestTargetPath")
        private val SOURCE_FILE_KEY = Key.create<String>("YaMake.TestSourceFile")
    }
}
