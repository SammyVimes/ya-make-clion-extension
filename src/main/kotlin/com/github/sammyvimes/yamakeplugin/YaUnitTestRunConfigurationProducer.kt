package com.github.sammyvimes.yamakeplugin

import com.intellij.execution.RunManager
import com.intellij.execution.RunManagerEx
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.ForeignLeafPsiElement
import com.jetbrains.cidr.lang.psi.OCMacroCall
import com.jetbrains.cidr.lang.psi.visitors.OCVisitor

class YaUnitTestRunConfigurationProducer :
    LazyRunConfigurationProducer<YaUnitTestRunConfiguration>() {

    val support: YaMakeConfigurationSupport = YaMakeConfigurationSupport()

    override fun setupConfigurationFromContext(
        config: YaUnitTestRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {
        val element = context.location?.psiElement

        if (element == null) {
            return false
        }

        val cachedTestObject = support.findCachedTestObject(element)

        if (cachedTestObject != null) {
            return true
        }

        var name: String? = null

        if (element is ForeignLeafPsiElement) {
            val macro = getYaUnitTestMacro(element) ?: return false

            name = macro.firstChild?.nextSibling?.nextSibling?.firstChild?.text ?: return false
        } else {
            val macro = getYaUnitTestMacro(element) ?: return false

            name = macro.firstChild?.nextSibling?.nextSibling?.text ?: return false
        }

        config.testName = name

        config.name = name

        val runManager = RunManagerEx.getInstanceEx(context.project)

        val settings = runManager.findSettings(config) ?: return true // fallback

        // Check if task already exists
        val existingTasks = runManager.getBeforeRunTasks(config)
        val hasYaMakeTask = existingTasks.any { it.providerId == YaMakeBeforeTestRunTaskProvider.ID }
        if (!hasYaMakeTask) {
            val provider = YaMakeBeforeTestRunTaskProvider.getInstance(context.project)
            val task: YaMakeBeforeRunTask? = provider?.createTask(config)
            if (task != null) {
                task.targetFile = "path/to/your/ya.make" // Set this dynamically if needed
                runManager.setBeforeRunTasks(config, existingTasks + task)
            }
        }

        return true
    }

    override fun isConfigurationFromContext(
        config: YaUnitTestRunConfiguration,
        context: ConfigurationContext
    ): Boolean {
        val psi = context.psiLocation ?: return false
        val macro = getYaUnitTestMacro(psi) ?: return false

        return config.testName == macro.text
    }

    override fun getConfigurationFactory(): ConfigurationFactory {
        return YaUnitTestRunConfigurationType.getInstance().getFactory()
    }
}
