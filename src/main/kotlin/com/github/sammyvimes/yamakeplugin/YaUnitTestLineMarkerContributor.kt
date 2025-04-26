package com.github.sammyvimes.yamakeplugin

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement


class YaUnitTestLineMarkerContributor : RunLineMarkerContributor(), DumbAware {
    override fun getInfo(element: PsiElement): Info? {
        val macro = getYaUnitTestMacro(element)

        if (macro == null) {
            return null
        }

        val actions: Array<AnAction> = ExecutorAction.getActions(Int.Companion.MAX_VALUE)
        return Info(AllIcons.RunConfigurations.TestState.Run, actions, { e -> ""})
    }
}