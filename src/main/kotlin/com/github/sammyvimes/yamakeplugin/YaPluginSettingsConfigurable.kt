// YaSettingsConfigurable.kt
package com.github.sammyvimes.yamakeplugin

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileTextField
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.util.ui.FormBuilder
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class YaPluginSettingsConfigurable : Configurable {
    private var component: JPanel? = null
    private var yaPathField: TextFieldWithBrowseButton? = null

    override fun getDisplayName() = "Ya Tool Settings"

    override fun createComponent(): JComponent {
        val field = TextFieldWithBrowseButton()
        field.addBrowseFolderListener("Select YaTool Binary", null, null,
            FileChooserDescriptorFactory.createSingleFileDescriptor());
        field.text = YaSettings.getInstance().state.yaPath

        val panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Script file", field)
            .getPanel();

        component = panel
        yaPathField = field

        return panel
    }

    override fun isModified(): Boolean {
        return yaPathField?.text != YaSettings.getInstance().state.yaPath
    }

    override fun apply() {
        YaSettings.getInstance().state.yaPath = yaPathField?.text ?: "ya"
    }

    override fun reset() {
        yaPathField?.text = YaSettings.getInstance().state.yaPath
    }

    override fun disposeUIResources() {
        component = null
        yaPathField = null
    }
}
