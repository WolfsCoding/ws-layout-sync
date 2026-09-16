package dev.boehmer.wslayoutsync

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Safety net for projects whose tool windows were registered before the listener could observe it,
 * e.g. when the plugin is installed into an already running IDE.
 */
class LayoutSyncStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        withContext(Dispatchers.EDT) {
            LayoutSyncService.getInstance().onProjectReady(project)
        }
    }
}
