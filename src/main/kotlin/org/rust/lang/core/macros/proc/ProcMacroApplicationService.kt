/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.proc

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.util.Disposer
import org.rust.cargo.project.settings.RustProjectSettingsService
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.toolchain.RsToolchainBase
import org.rust.cargo.toolchain.wsl.RsWslToolchain
import org.rust.ide.experiments.RsExperiments
import org.rust.openapiext.isFeatureEnabled

@Service
class ProcMacroApplicationService : Disposable {
    private val servers: MutableMap<String, ProcMacroServerPool?> = hashMapOf()

    init {
        val connect = ApplicationManager.getApplication().messageBus.connect(this)

        connect.subscribe(RustProjectSettingsService.RUST_SETTINGS_TOPIC, object : RustProjectSettingsService.RustSettingsListener {
            override fun rustSettingsChanged(e: RustProjectSettingsService.RustSettingsChangedEvent) {
                if (e.oldState.toolchain?.distributionId != e.newState.toolchain?.distributionId) {
                    removeUnusableSevers()
                }
            }
        })

        connect.subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
            override fun projectClosed(project: Project) {
                removeUnusableSevers()
            }
        })
    }

    @Synchronized
    fun getServer(toolchain: RsToolchainBase): ProcMacroServerPool? {
        if (!isEnabled()) return null

        val id = toolchain.distributionId
        var server = servers[id]
        if (server == null) {
            server = ProcMacroServerPool.tryCreate(toolchain, this)
            servers[id] = server
        }
        return server
    }

    @Synchronized
    private fun removeUnusableSevers() {
        val distributionIds = ProjectManager.getInstance().openProjects
            .mapNotNull { it.rustSettings.toolchain?.distributionId }
        servers.keys
            .filterNot { it in distributionIds }
            .mapNotNull { servers.remove(it) }
            .forEach { Disposer.dispose(it) }
    }

    override fun dispose() {}

    companion object {
        fun getInstance(): ProcMacroApplicationService = service()
        fun isEnabled(): Boolean = isFeatureEnabled(RsExperiments.PROC_MACROS)
            && isFeatureEnabled(RsExperiments.EVALUATE_BUILD_SCRIPTS)

        private val RsToolchainBase.distributionId: String
            get() = if (this is RsWslToolchain) wslPath.distributionId else "Local"
    }
}
