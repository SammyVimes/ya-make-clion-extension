package com.github.sammyvimes.yamakeplugin

import com.intellij.execution.ExecutionResult
import com.intellij.execution.configurations.*
import com.intellij.openapi.project.Project
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.options.SettingsEditor
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import org.jetbrains.debugger.DebuggableRunConfiguration
import java.net.InetSocketAddress
import javax.swing.JPanel

class YaUnitTestRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String
) : RunConfigurationBase<Any>(project, factory, name), DebuggableRunConfiguration {

    var testName: String = ""

    override fun getConfigurationEditor() = object : SettingsEditor<YaUnitTestRunConfiguration>() {
        override fun resetEditorFrom(s: YaUnitTestRunConfiguration) {}
        override fun applyEditorTo(s: YaUnitTestRunConfiguration) {}
        override fun createEditor() = JPanel()
    }

    override fun checkConfiguration() {
        if (testName.isBlank()) error("Test name is required")
    }

    override fun writeExternal(element: org.jdom.Element) {
        super<RunConfigurationBase>.writeExternal(element)
        element.setAttribute("testName", testName)
    }

    override fun readExternal(element: org.jdom.Element) {
        super<RunConfigurationBase>.readExternal(element)
        testName = element.getAttributeValue("testName") ?: ""
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        return YaUnitTestRunProfileState(project, testName, environment)
    }

    override fun createDebugProcess(
        socketAddress: InetSocketAddress,
        session: XDebugSession,
        executionResult: ExecutionResult?,
        environment: ExecutionEnvironment
    ): XDebugProcess {
        TODO("Not yet implemented")
    }
}

class YaUnitTestRunProfileState(
    private val project: Project,
    private val testName: String,
    environment: ExecutionEnvironment
) : CommandLineState(environment) {
    override fun startProcess(): ProcessHandler {
        val yaPath = YaSettings.getInstance().state.yaPath
        val commandLine = GeneralCommandLine(yaPath, "test", testName)
        return KillableProcessHandler(commandLine)
    }
}
