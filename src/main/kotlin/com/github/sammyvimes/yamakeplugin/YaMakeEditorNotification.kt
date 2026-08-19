package com.github.sammyvimes.yamakeplugin

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.util.ui.JBUI
import java.nio.file.Paths
import java.util.function.Function
import javax.swing.JComponent

class YaMakeEditorNotification : EditorNotificationProvider {
    override fun collectNotificationData(
        project: Project,
        file: VirtualFile,
    ): Function<in FileEditor, out JComponent?>? {
        if (file.name != "ya.make") return null

        return Function {
            val settings = YaProjectSettings.getInstance(project)
            val target = Paths.get(file.parent.path).toAbsolutePath().normalize()
            val isSelected = settings.selectedTarget() == target
            val panel = EditorNotificationPanel(
                if (isSelected) {
                    JBUI.CurrentTheme.NotificationInfo.backgroundColor()
                } else {
                    JBUI.CurrentTheme.NotificationWarning.backgroundColor()
                },
            )

            if (isSelected) {
                panel.text = "Current Ya C++ index target"
                panel.createActionLabel("Refresh code model") {
                    YaProjectRefreshService.getInstance(project).refresh(target)
                }
            } else {
                panel.text = "This ya.make is not the current C++ index target"
                panel.createActionLabel("Use target and refresh") {
                    YaProjectRefreshService.getInstance(project).refresh(target)
                }
            }
            panel
        }
    }
}
