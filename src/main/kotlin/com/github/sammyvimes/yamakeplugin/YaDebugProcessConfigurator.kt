package com.github.sammyvimes.yamakeplugin

import com.jetbrains.cidr.cpp.execution.external.run.CLionExternalRunConfiguration
import com.jetbrains.cidr.execution.debugger.CidrDebugProcess
import com.jetbrains.cidr.execution.debugger.CidrDebugProcessConfigurator
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriver

class YaDebugProcessConfigurator : CidrDebugProcessConfigurator {
    override fun configure(process: CidrDebugProcess) = Unit

    override fun configureBeforeTargetLoaded(process: CidrDebugProcess) {
        val configuration = process.session.runProfile as? CLionExternalRunConfiguration ?: return
        if (!configuration.name.startsWith(YaUnitTestRunConfigurationProducer.CONFIGURATION_PREFIX)) return

        val settings = YaProjectSettings.getInstance(process.project)
        val sourceRoot = settings.projectRoot().toString().trimEnd('/') + "/"
        val codegenRoot = settings.codegenPath().toString().trimEnd('/') + "/"
        process.postCommand(object : CidrDebugProcess.VoidDebuggerCommand {
            override fun run(driver: DebuggerDriver) {
                driver.addPathMapping(0, "/-S/", sourceRoot)
                driver.addPathMapping(0, "/-B/", codegenRoot)
                driver.executeConsoleCommand("set filename-display absolute")
                driver.executeConsoleCommand("set print object on")
            }
        })
    }
}
