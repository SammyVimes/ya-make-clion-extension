package com.github.sammyvimes.yamakeplugin

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyValue
import icons.CMakeIcons
import org.jetbrains.annotations.NonNls
import java.util.function.Supplier
import javax.swing.Icon

class YaUnitTestRunConfigurationType : ConfigurationTypeBase {
    private val myFactory = Factory(this)

    constructor() : super("YaUnitTestConfigurationType", "Ya Unit Test", "Bla", NotNullLazyValue.lazy<Icon?>(Supplier { CMakeIcons.CMakeDebug })) {
        this.addFactory(this.myFactory)
    }

    fun getFactory(): ConfigurationFactory {
        return this.myFactory
    }

    companion object {
        fun getInstance(): YaUnitTestRunConfigurationType {
            return ConfigurationType.CONFIGURATION_TYPE_EP.findExtensionOrFail<YaUnitTestRunConfigurationType?>(
                YaUnitTestRunConfigurationType::class.java
            ) as YaUnitTestRunConfigurationType
        }
    }

    class Factory(ct: YaUnitTestRunConfigurationType) : ConfigurationFactory(ct) {

        override fun createTemplateConfiguration(project: Project): RunConfiguration {
            return YaUnitTestRunConfiguration(project, this, "YaUnitTest")
        }

        override fun getId(): @NonNls String {
            return "YaUnitTestConfigurationFactory"
        }

    }
}
