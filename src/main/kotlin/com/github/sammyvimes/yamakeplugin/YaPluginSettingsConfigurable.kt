package com.github.sammyvimes.yamakeplugin

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.nio.file.Paths
import javax.swing.JComponent
import javax.swing.JPanel

class YaPluginSettingsConfigurable(private val project: Project) : Configurable {
    private var component: JPanel? = null
    private var yaPathField: TextFieldWithBrowseButton? = null
    private var targetPathField: TextFieldWithBrowseButton? = null
    private var codegenPathField: TextFieldWithBrowseButton? = null
    private var buildTypeField: JBTextField? = null
    private var extraArgsField: JBTextField? = null

    override fun getDisplayName() = "Ya Make"

    override fun createComponent(): JComponent {
        if (project.isDefault || project.basePath == null) {
            return JPanel().also { component = it }
        }

        val settings = YaProjectSettings.getInstance(project)

        val yaField = TextFieldWithBrowseButton().apply {
            addBrowseFolderListener(
                "Select Ya executable",
                null,
                project,
                FileChooserDescriptorFactory.createSingleFileDescriptor(),
            )
            text = settings.yaPath().toString()
        }
        val targetField = TextFieldWithBrowseButton().apply {
            addBrowseFolderListener(
                "Select directory containing ya.make",
                null,
                project,
                FileChooserDescriptorFactory.createSingleFolderDescriptor(),
            )
            text = settings.selectedTarget()?.toString().orEmpty()
        }
        val codegenField = TextFieldWithBrowseButton().apply {
            addBrowseFolderListener(
                "Select code generation directory",
                null,
                project,
                FileChooserDescriptorFactory.createSingleFolderDescriptor(),
            )
            text = settings.codegenPath().toString()
        }

        val buildType = JBTextField(settings.buildType()).apply {
            emptyText.text = YaProjectSettings.DEFAULT_BUILD_TYPE
        }
        val extraArgs = JBTextField(settings.extraYaArgs()).apply {
            emptyText.text = "e.g. -DCONSISTENT_DEBUG=yes"
        }

        component = FormBuilder.createFormBuilder()
            .addLabeledComponent("Ya executable", yaField)
            .addLabeledComponent("Index target", targetField)
            .addLabeledComponent("Codegen directory", codegenField)
            .addLabeledComponent("Build type (--build)", buildType)
            .addLabeledComponent("Additional ya arguments", extraArgs)
            .getPanel()
        yaPathField = yaField
        targetPathField = targetField
        codegenPathField = codegenField
        buildTypeField = buildType
        extraArgsField = extraArgs
        return component!!
    }

    override fun isModified(): Boolean {
        val settings = YaProjectSettings.getInstance(project)
        return yaPathField?.text.orEmpty() != settings.yaPath().toString() ||
            targetPathField?.text.orEmpty() != settings.selectedTarget()?.toString().orEmpty() ||
            codegenPathField?.text.orEmpty() != settings.codegenPath().toString() ||
            buildTypeField?.text.orEmpty() != settings.buildType() ||
            extraArgsField?.text.orEmpty() != settings.extraYaArgs()
    }

    override fun apply() {
        val state = YaProjectSettings.getInstance(project).state
        state.yaPath = yaPathField?.text.orEmpty().trim()
        state.targetPath = targetPathField?.text.orEmpty().trim()
        state.codegenPath = codegenPathField?.text.orEmpty().trim()
        state.buildType = buildTypeField?.text.orEmpty().trim()
        state.extraYaArgs = extraArgsField?.text.orEmpty().trim()
    }

    override fun reset() {
        val settings = YaProjectSettings.getInstance(project)
        yaPathField?.text = settings.yaPath().toString()
        targetPathField?.text = settings.selectedTarget()?.toString().orEmpty()
        codegenPathField?.text = settings.codegenPath().toString()
        buildTypeField?.text = settings.buildType()
        extraArgsField?.text = settings.extraYaArgs()
    }

    override fun disposeUIResources() {
        component = null
        yaPathField = null
        targetPathField = null
        codegenPathField = null
        buildTypeField = null
        extraArgsField = null
    }
}
