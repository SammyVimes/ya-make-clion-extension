package com.github.sammyvimes.yamakeplugin

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeWithMe.ClientId
import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.execution.lineMarker.RunLineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement

// TEMP: YA-GUTTER logging below is diagnostics for the duplicate-gutter
// investigation; strip it once the cause is confirmed.
private val LOG = logger<YaUnitTestRunLineMarkerContributor>()

/**
 * Supplies the single standard Run marker consumed by CLion's RunLineMarkerProvider.
 *
 * Do not turn this into a LineMarkerProvider and do not call
 * RunLineMarkerProvider.createLineMarker here: that bypasses the normal contributor
 * aggregation and is projected as an additional marker in Remote Development.
 */
class YaUnitTestRunLineMarkerContributor : RunLineMarkerContributor(), DumbAware {
    override fun getInfo(element: PsiElement): Info? {
        val macro = getYaUnitTestMacro(element) ?: return null
        val fullTestName = getYaUnitTestFullName(macro) ?: return null

        // The nested standard pass sees every CLion contributor except this fallback.
        // This prevents a backend fallback from being projected next to a Nova-native
        // marker while still covering tests which CLion has not indexed natively.
        if (checkingNativeMarker.get()) {
            LOG.warn("YA-GUTTER nested: '$fullTestName' — self suppressed during native-marker check")
            return null
        }

        // Nova projects CLion's native test marker on the frontend. Publishing our
        // fallback from the backend as well would show two adjacent gutter icons.
        // The native marker still discovers our RunConfigurationProducer actions.
        val nativeMarker = findNativeClionMarker(element)
        LOG.warn(
            "YA-GUTTER getInfo: test='$fullTestName'" +
                " element=${element.javaClass.simpleName}@${element.textOffset}" +
                " dumb=${DumbService.isDumb(element.project)}" +
                " client=${ClientId.currentOrNull?.value ?: "local"}" +
                " thread=${Thread.currentThread().name.take(40)}" +
                " native=${nativeMarker?.let { "${it.javaClass.name} tooltip='${it.lineMarkerTooltip}'" } ?: "none"}" +
                " decision=${if (nativeMarker != null) "suppress" else "publish"}",
        )
        if (nativeMarker != null) return null

        return Info(
            AllIcons.RunConfigurations.TestState.Run,
            ExecutorAction.getActions(),
        ) { "Run Ya test '$fullTestName'" }
    }

    private fun findNativeClionMarker(element: PsiElement): LineMarkerInfo<*>? {
        checkingNativeMarker.set(true)
        return try {
            RunLineMarkerProvider().getLineMarkerInfo(element)
        } finally {
            checkingNativeMarker.remove()
        }
    }

    companion object {
        private val checkingNativeMarker = ThreadLocal.withInitial { false }
    }
}
