package com.github.sammyvimes.yamakeplugin

import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener

/**
 * TEMP diagnostics for the duplicate-gutter investigation; remove afterwards.
 *
 * Installs the markup watcher the moment an editor is created — before the first
 * highlighting pass — so the birth stack of the orphaned duplicate marker is captured.
 */
class YaGutterDebugEditorFactoryListener : EditorFactoryListener {
    override fun editorCreated(event: EditorFactoryEvent) {
        val project = event.editor.project ?: return
        YaGutterMarkupWatcher.install(event.editor.document, project)
    }
}
