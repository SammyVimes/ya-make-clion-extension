package com.github.sammyvimes.yamakeplugin

import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.NopProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.options.SettingsEditorGroup
import com.intellij.openapi.project.Project

enum class YaCommandType {
    CODEGEN,
    COMPILE_COMMANDS
}

class YaGenerateCompileCommandsConfiguration(
    project: Project,
    factory: ConfigurationFactory
) : RunConfigurationBase<Any>(project, factory, "Generate CompileCommands") {

    var yaMakeFilePath: String = ""
    var type: YaCommandType = YaCommandType.CODEGEN

    override fun getConfigurationEditor() = SettingsEditorGroup<YaGenerateCompileCommandsConfiguration>()

    override fun checkConfiguration() {
        if (yaMakeFilePath.isBlank()) {
            throw RuntimeConfigurationException("Path to ya.make must be set")
        }
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        return object : CommandLineState(environment) {
            override fun startProcess(): ProcessHandler {
                return if (type == YaCommandType.CODEGEN) {
                    // Start codegen
                    CompileCommandsGenerator.generateCode(project, yaMakeFilePath)
                } else {
                    // Start compile_commands.json generation
                    CompileCommandsGenerator.generateCompileCommands(project, yaMakeFilePath)
                }
            }
        }
    }
}

class YaGenerateCompileCommandsConfigurationType : ConfigurationType {
    override fun getDisplayName() = "Generate CompileCommands"
    override fun getConfigurationTypeDescription() = "Runs ya make to generate compile_commands.json"
    override fun getId() = "YA_GEN_COMPILE_COMMANDS"
    override fun getIcon() = null

    override fun getConfigurationFactories(): Array<ConfigurationFactory> = arrayOf(object : ConfigurationFactory(this) {
        override fun createTemplateConfiguration(project: Project): RunConfiguration {
            return YaGenerateCompileCommandsConfiguration(project, this)
        }
    })
}
