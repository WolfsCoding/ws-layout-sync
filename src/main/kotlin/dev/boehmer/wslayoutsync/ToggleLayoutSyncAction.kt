package dev.boehmer.wslayoutsync

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction

/** Window | Layouts | Sync Layout Across Windows. Re-enabling pushes the current window's layout everywhere. */
class ToggleLayoutSyncAction : DumbAwareToggleAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun isSelected(e: AnActionEvent) = LayoutSyncService.getInstance().enabled

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val service = LayoutSyncService.getInstance()
        service.enabled = state
        if (state) {
            e.project?.let(service::broadcastFrom)
        }
    }
}
