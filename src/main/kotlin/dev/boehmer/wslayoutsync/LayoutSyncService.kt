package dev.boehmer.wslayoutsync

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.openapi.wm.impl.DesktopLayout
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jdom.Element
import java.awt.Rectangle

/**
 * Application-wide source of truth for the tool window layout.
 *
 * Whenever a synced project changes its layout, a snapshot is stored here, persisted, and pushed to every other
 * open project. Projects that open later receive the stored snapshot once their tool windows are registered.
 *
 * Only the layout *structure* is synced (anchor, order, type, size, split, auto-hide, stripe button visibility).
 * Whether a tool window is currently open or focused stays per project, so opening the terminal in one window
 * does not pop it open everywhere.
 *
 * All mutating calls must happen on the EDT; the tool window manager requires it and it keeps the state simple.
 */
@Service(Service.Level.APP)
@State(name = "LayoutSync", storages = [Storage("layoutSync.xml")])
class LayoutSyncService(private val scope: CoroutineScope) : PersistentStateComponent<Element> {

    /** Serialized [DesktopLayout] every window should follow. Null until the first project reports its layout. */
    private var storedLayout: Element? = null

    /** Set while this service is applying a layout, so the resulting change events are not re-broadcast. */
    private var applying = false

    private var broadcastJob: Job? = null

    var enabled: Boolean = true

    // ---- entry points (any thread; the actual work is scheduled on the EDT) ----

    /** Called when a project's layout changed by user action. Debounced, since drags fire many events. */
    fun onLayoutChanged(project: Project) {
        if (!enabled || applying || project.getUserData(SYNCED) != true) return
        broadcastJob?.cancel()
        broadcastJob = scope.launch(Dispatchers.EDT) {
            delay(DEBOUNCE_MS)
            if (!project.isDisposed) broadcastFrom(project)
        }
    }

    /** Called when a project (re)registers tool windows or finished starting up. Applies the stored layout to it. */
    fun onProjectReady(project: Project) {
        if (!enabled) return
        project.getUserData(APPLY_JOB)?.cancel()
        project.putUserData(APPLY_JOB, scope.launch(Dispatchers.EDT) {
            delay(DEBOUNCE_MS)
            project.putUserData(APPLY_JOB, null)
            if (!project.isDisposed) applyStoredTo(project)
        })
    }

    /** Makes [project]'s current layout the shared one and pushes it to all other open projects. */
    fun broadcastFrom(project: Project) {
        ThreadingAssertions.assertEventDispatchThread()
        val snapshot = ToolWindowManagerEx.getInstanceEx(project).getLayout().copy()
        storedLayout = snapshot.writeExternal(DesktopLayout.TAG)
        project.putUserData(SYNCED, true)
        for (other in ProjectManager.getInstance().openProjects) {
            if (other !== project && !other.isDisposed) applyTo(other, snapshot)
        }
    }

    // ---- internals ----

    private fun applyStoredTo(project: Project) {
        val element = storedLayout
        if (element == null) {
            // Nothing recorded yet: the first project defines the layout.
            broadcastFrom(project)
            return
        }
        applyTo(project, DesktopLayout().also { it.readExternal(element, true) })
    }

    private fun applyTo(project: Project, source: DesktopLayout) {
        val manager = ToolWindowManagerEx.getInstanceEx(project)
        val merged = mergeStructure(target = manager.getLayout().copy(), source = source)
        applying = true
        try {
            manager.setLayout(merged)
        } catch (e: Exception) {
            thisLogger().warn("Failed to apply synced layout to ${project.name}", e)
        } finally {
            applying = false
        }
        project.putUserData(SYNCED, true)
    }

    /**
     * Copies layout geometry from [source] into [target] for every tool window both know about, keeping each
     * window's own visibility and activation state. Windows only [target] knows keep their current state, which
     * matters because the tool window manager resets windows missing from a new layout to factory defaults.
     */
    private fun mergeStructure(target: DesktopLayout, source: DesktopLayout): DesktopLayout {
        for ((id, src) in source.getInfos()) {
            val dst = target.getInfo(id) ?: continue
            dst.anchor = src.anchor
            dst.order = src.order
            dst.type = src.type
            dst.internalType = src.internalType
            dst.isSplit = src.isSplit
            dst.isAutoHide = src.isAutoHide
            dst.weight = src.weight
            dst.sideWeight = src.sideWeight
            dst.contentUiType = src.contentUiType
            dst.isShowStripeButton = src.isShowStripeButton
            dst.floatingBounds = src.floatingBounds?.let { Rectangle(it) }
        }
        for (anchor in ANCHORS) {
            target.setUnifiedAnchorWeight(anchor, source.getUnifiedAnchorWeight(anchor))
        }
        return target
    }

    // ---- persistence ----

    override fun getState(): Element {
        val state = Element("state")
        state.setAttribute("enabled", enabled.toString())
        storedLayout?.let { state.addContent(it.clone()) }
        return state
    }

    override fun loadState(state: Element) {
        enabled = state.getAttributeValue("enabled")?.toBoolean() ?: true
        storedLayout = state.getChild(DesktopLayout.TAG)?.clone()
        // Also reached when Settings Sync delivers a newer file; push it to whatever is open.
        scope.launch(Dispatchers.EDT) {
            for (project in ProjectManager.getInstance().openProjects) {
                if (!project.isDisposed) onProjectReady(project)
            }
        }
    }

    companion object {
        private const val DEBOUNCE_MS = 300L
        private val ANCHORS = listOf(ToolWindowAnchor.LEFT, ToolWindowAnchor.RIGHT, ToolWindowAnchor.TOP, ToolWindowAnchor.BOTTOM)

        /** Marks projects whose layout has been aligned with the shared one; only those may broadcast changes. */
        private val SYNCED = Key.create<Boolean>("LayoutSync.synced")
        private val APPLY_JOB = Key.create<Job>("LayoutSync.applyJob")

        fun getInstance(): LayoutSyncService = service()
    }
}
