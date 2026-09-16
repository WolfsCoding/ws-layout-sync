package dev.boehmer.wslayoutsync

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.ex.ToolWindowManagerListener.ToolWindowManagerEventType

/** Forwards layout-relevant tool window events of one project to [LayoutSyncService]. */
class LayoutSyncListener(private val project: Project) : ToolWindowManagerListener {

    override fun stateChanged(toolWindowManager: ToolWindowManager, changeType: ToolWindowManagerEventType) {
        if (changeType in STRUCTURAL_EVENTS) {
            LayoutSyncService.getInstance().onLayoutChanged(project)
        }
    }

    /** Fired on startup and whenever a tool window appears later (e.g. Run after the first run configuration). */
    override fun toolWindowsRegistered(ids: List<String>, toolWindowManager: ToolWindowManager) {
        LayoutSyncService.getInstance().onProjectReady(project)
    }

    private companion object {
        /** Events that change where or how a tool window is laid out, as opposed to it merely being shown or focused. */
        val STRUCTURAL_EVENTS = setOf(
            ToolWindowManagerEventType.SetToolWindowAnchor,
            ToolWindowManagerEventType.SetSideTool,
            ToolWindowManagerEventType.SetSideToolAndAnchor,
            ToolWindowManagerEventType.SetToolWindowType,
            ToolWindowManagerEventType.SetToolWindowAutoHide,
            ToolWindowManagerEventType.SetContentUiType,
            ToolWindowManagerEventType.SetShowStripeButton,
            ToolWindowManagerEventType.SetVisibleOnLargeStripe,
            ToolWindowManagerEventType.MovedOrResized,
            ToolWindowManagerEventType.SetLayout,
        )
    }
}
