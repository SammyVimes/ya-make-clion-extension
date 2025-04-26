package com.github.sammyvimes.yamakeplugin

import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.ui.EditorNotificationProvider
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.util.function.Function
import javax.swing.JComponent

class YaMakeEditorNotification : EditorNotificationProvider {

    override fun collectNotificationData(
        project: Project,
        file: VirtualFile
    ): Function<in FileEditor, out JComponent?>? {
        if (file.name != "ya.make") return null

        return Function { fileEditor ->
            val currentYaMake = YaSettings.getInstance().state.currentYaMake

            if (currentYaMake != file.path) {
                val panel = EditorNotificationPanel(JBUI.CurrentTheme.NotificationWarning.backgroundColor())

                panel.text = "Currently unused ya.make file"
                panel.createActionLabel("Select this as current") {
                    YaSettings.getInstance().state.currentYaMake = file.path
                    EditorNotifications.getInstance(project).updateNotifications(file)
                }

                panel
            } else {
                val panel = EditorNotificationPanel(JBUI.CurrentTheme.NotificationInfo.backgroundColor())

                panel.text = "This is the current ya.make file"
                panel.createActionLabel("Run code generation") {
                    runYaGenerate(project, file, YaCommandType.CODEGEN)
                }
                panel.createActionLabel("Generate compile_commands.json") {
                    runYaGenerate(project, file, YaCommandType.COMPILE_COMMANDS)
                }

                panel
            }
        }
    }

    private fun runYaGenerate(project: Project, yaMakeFile: VirtualFile, yaCommandType: YaCommandType) {
        val configurationType = ConfigurationType.CONFIGURATION_TYPE_EP
            .findExtension(YaGenerateCompileCommandsConfigurationType::class.java) ?: return

        val factory = configurationType.configurationFactories[0]
        val configuration: YaGenerateCompileCommandsConfiguration = factory.createTemplateConfiguration(project) as YaGenerateCompileCommandsConfiguration
        configuration.yaMakeFilePath = yaMakeFile.parent.path
        configuration.type = yaCommandType

        val runnerSettings = RunManager.getInstance(project)
            .createConfiguration(configuration, factory)

        ProgramRunnerUtil.executeConfiguration(runnerSettings, DefaultRunExecutor.getRunExecutorInstance())
    }
}
