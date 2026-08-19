package com.github.sammyvimes.yamakeplugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key

private val LOG = logger<YaRunMarkerDeduplicator>()
private val DEDUPLICATOR_INSTALLED = Key.create<Boolean>("YaMake.RunMarkerDeduplicator")

/**
 * Removes duplicated Ya run gutter markers.
 *
 * CLion 2026.1 runs LineMarkersPass once per CodeInsightContext of a file, and
 * LineMarkersUtil.setLineMarkersToEditor refuses to recycle highlighters created
 * under a different context (shared-source filtering). A file reachable from more
 * than one context therefore accumulates one copy of our run marker per context,
 * rendered as adjacent duplicate gutter icons in Remote Development. Until the
 * platform recycles cross-context markers, keep only the newest highlighter per
 * (range, tooltip) pair.
 */
class YaRunMarkerDeduplicator : EditorFactoryListener {
    override fun editorCreated(event: EditorFactoryEvent) {
        val project = event.editor.project ?: return
        install(event.editor.document, project)
    }

    private fun install(document: Document, project: Project) {
        if (document.getUserData(DEDUPLICATOR_INSTALLED) == true) return
        document.putUserData(DEDUPLICATOR_INSTALLED, true)
        val markup = DocumentMarkupModel.forDocument(document, project, true) as? MarkupModelEx ?: return
        markup.addMarkupModelListener(
            project,
            object : MarkupModelListener {
                // Tooltip access can throw for Nova-projected highlighters whose markup
                // is being disposed — never let that propagate out of a markup listener.
                override fun afterAdded(highlighter: RangeHighlighterEx) {
                    val tooltip = runCatching { highlighter.gutterIconRenderer?.tooltipText }.getOrNull() ?: return
                    if (!tooltip.contains(RUN_MARKER_TOOLTIP_FRAGMENT)) return
                    ApplicationManager.getApplication().invokeLater {
                        removeOlderTwins(markup, highlighter, tooltip)
                    }
                }
            },
        )
    }

    private fun removeOlderTwins(markup: MarkupModelEx, added: RangeHighlighterEx, tooltip: String) {
        if (!added.isValid) return
        val start = added.startOffset
        val end = added.endOffset
        val twins = mutableListOf<RangeHighlighter>()
        markup.processRangeHighlightersOverlappingWith(start, end) { candidate ->
            if (candidate !== added && candidate.isValid &&
                candidate.startOffset == start && candidate.endOffset == end &&
                runCatching { candidate.gutterIconRenderer?.tooltipText }.getOrNull() == tooltip
            ) {
                twins.add(candidate)
            }
            true
        }
        for (twin in twins) {
            markup.removeHighlighter(twin)
            LOG.info("Removed duplicated Ya run marker at $start..$end: $tooltip")
        }
    }

    companion object {
        private const val RUN_MARKER_TOOLTIP_FRAGMENT = "Run Ya test"
    }
}
