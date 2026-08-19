package com.github.sammyvimes.yamakeplugin

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class YaRefreshProjectAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val settings = YaProjectSettings.getInstance(project)
        val target = settings.selectedTarget()
        if (target == null) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Ya Make")
                .createNotification(
                    "Open a ya.make file and choose 'Use target and refresh' first",
                    NotificationType.WARNING,
                )
                .notify(project)
            return
        }

        YaProjectRefreshService.getInstance(project).refresh(target)
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
    }
}
