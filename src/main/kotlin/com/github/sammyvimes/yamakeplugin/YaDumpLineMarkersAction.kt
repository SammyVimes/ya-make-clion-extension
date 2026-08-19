package com.github.sammyvimes.yamakeplugin

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeWithMe.ClientId
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import java.awt.datatransfer.StringSelection

private val LOG = logger<YaDumpLineMarkersAction>()

private val WATCHER_INSTALLED = Key.create<Boolean>("YaGutterDebugMarkupWatcher")

/**
 * TEMP diagnostics for the duplicate-gutter investigation; remove afterwards.
 *
 * Dumps every LineMarkerInfo the backend daemon produced for the focused editor.
 * A gutter icon visible in the editor but absent from this dump is rendered
 * outside the backend daemon (Nova frontend / Remote Dev projection).
 */
class YaDumpLineMarkersAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null && e.getData(CommonDataKeys.EDITOR) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val filePath = e.getData(CommonDataKeys.VIRTUAL_FILE)?.path ?: "<unknown>"
        val document = editor.document
        YaGutterMarkupWatcher.install(document, project)

        val report = runReadAction {
            val markers = DaemonCodeAnalyzerImpl.getLineMarkers(document, project)
            val rows = markers.mapNotNull { info ->
                val element = info.element ?: return@mapNotNull null
                val line = document.getLineNumber(element.textRange.startOffset) + 1
                val text = element.text.replace('\n', ' ').take(48)
                line to ("line=$line marker=${info.javaClass.name}" +
                    " info=@${System.identityHashCode(info)}" +
                    " hl=@${info.highlighter?.let(System::identityHashCode) ?: "none"}" +
                    " hlValid=${info.highlighter?.isValid}" +
                    " element=${element.javaClass.simpleName}@${element.textRange.startOffset}" +
                    " text='$text' tooltip='${info.lineMarkerTooltip}'")
            }.sortedBy { it.first }
            val crowdedLines = rows.groupingBy { it.first }.eachCount().filterValues { it > 1 }.keys.sorted()
            buildString {
                appendLine(
                    "YA-GUTTER dump: file=$filePath" +
                        " dumb=${DumbService.isDumb(project)}" +
                        " markersInBackendDaemon=${rows.size}",
                )
                appendLine("lines with >1 backend marker: ${crowdedLines.ifEmpty { "none" }}")
                rows.forEach { appendLine(it.second) }
            }
        }

        LOG.warn(report)
        CopyPasteManager.getInstance().setContents(StringSelection(report))
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Ya Make")
            .createNotification(
                "Dumped line markers to idea.log (grep YA-GUTTER) and clipboard",
                NotificationType.INFORMATION,
            )
            .notify(project)
    }

}

// Logs the creation stack of every "Run Ya test" gutter highlighter, so the writer
// that produces the duplicate can be identified. Events go to idea.log and to
// ~/ya-gutter-debug.log — the latter survives the idea.log rotation on backend
// restart. The disposable intentionally leaks: debug-only code.
internal object YaGutterMarkupWatcher {
    private val LOG = logger<YaGutterMarkupWatcher>()
    private val timeFormat = java.text.SimpleDateFormat("HH:mm:ss.SSS")
    private val sideFile = java.io.File(System.getProperty("user.home"), "ya-gutter-debug.log")

    private fun emit(message: String) {
        LOG.warn(message)
        runCatching { sideFile.appendText("${timeFormat.format(java.util.Date())} $message\n") }
    }

    fun install(document: com.intellij.openapi.editor.Document, project: com.intellij.openapi.project.Project) {
        if (document.getUserData(WATCHER_INSTALLED) == true) return
        document.putUserData(WATCHER_INSTALLED, true)
        val markup = DocumentMarkupModel.forDocument(document, project, true) as MarkupModelEx
        markup.addMarkupModelListener(
            Disposer.newDisposable("YaGutterDebugMarkupWatcher"),
            object : MarkupModelListener {
                // Tooltip access can throw for Nova-projected highlighters whose markup
                // is being disposed — never let that propagate out of a markup listener.
                override fun afterAdded(highlighter: RangeHighlighterEx) {
                    val tooltip = runCatching { highlighter.gutterIconRenderer?.tooltipText }.getOrNull() ?: return
                    if (!tooltip.contains("Run Ya test")) return
                    val stack = Throwable().stackTraceToString()
                        .lines().drop(2).take(22).joinToString("\n")
                    emit(
                        "YA-GUTTER added: hl=@${System.identityHashCode(highlighter)}" +
                            " range=${highlighter.textRange}" +
                            " client=${ClientId.currentOrNull?.value ?: "local"}" +
                            " thread=${Thread.currentThread().name.take(40)}" +
                            " tooltip='${tooltip.substringAfter("Run Ya test ").take(60)}'\n$stack",
                    )
                }

                override fun beforeRemoved(highlighter: RangeHighlighterEx) {
                    val tooltip = runCatching { highlighter.gutterIconRenderer?.tooltipText }.getOrNull() ?: return
                    if (!tooltip.contains("Run Ya test")) return
                    emit(
                        "YA-GUTTER removed: hl=@${System.identityHashCode(highlighter)}" +
                            " range=${highlighter.textRange}" +
                            " tooltip='${tooltip.substringAfter("Run Ya test ").take(60)}'",
                    )
                }
            },
        )
        emit("YA-GUTTER watcher installed for $document")
    }
}
