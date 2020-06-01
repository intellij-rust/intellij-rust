/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.runconfig

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.InstalledPluginsState
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginsAdvertiser
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.PlatformUtils
import org.rust.cargo.runconfig.RsDefaultProgramRunnerBase
import org.rust.cargo.runconfig.command.CargoCommandConfiguration

class RsDebugAdvertisingRunner : RsDefaultProgramRunnerBase() {

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        if (executorId != DefaultDebugExecutor.EXECUTOR_ID) return false
        if (profile !is CargoCommandConfiguration) return false
        if (!(SystemInfo.isMac || SystemInfo.isLinux)) return false
        if (!(PlatformUtils.isIdeaUltimate() || PlatformUtils.isRubyMine())) return false
        val id = PluginId.getId(NATIVE_DEBUG_PLUGIN_ID)
        val plugin = PluginManagerCore.getPlugin(id)
        val loadedPlugins = PluginManagerCore.getLoadedPlugins()
        return plugin !in loadedPlugins || plugin?.isEnabled != true
    }

    override fun execute(environment: ExecutionEnvironment) {
        val id = PluginId.getId(NATIVE_DEBUG_PLUGIN_ID)
        val plugin = PluginManagerCore.getPlugin(id)
        val pluginsState = InstalledPluginsState.getInstance()

        val action = when {
            // Not installed
            plugin == null && !pluginsState.wasInstalled(id) -> Action.INSTALL
            // Disabled
            plugin?.isEnabled == false -> Action.ENABLE
            // Restart required
            else -> Action.RESTART
        }

        val project = environment.project
        val options = Messages.showDialog(
            project,
            action.message,
            "Unable to run debugger",
            arrayOf(action.actionName),
            Messages.OK,
            Messages.getErrorIcon()
        )

        if (options == Messages.OK) {
            action.doOkAction(project, id)
        }
    }

    override fun getRunnerId(): String = RUNNER_ID

    companion object {
        const val RUNNER_ID: String = "RsDebugAdvertisingRunner"
        private const val NATIVE_DEBUG_PLUGIN_ID: String = "com.intellij.nativeDebug"
    }

    private enum class Action {
        INSTALL {
            override val message: String
                get() = "Native Debugging Support plugin is not installed"
            override val actionName: String
                get() = "Install"

            override fun doOkAction(project: Project, pluginId: PluginId) {
                PluginsAdvertiser.installAndEnable(project, setOf(pluginId), false) {}
            }
        },
        ENABLE {
            override val message: String
                get() = "Native Debugging Support plugin is not enabled"
            override val actionName: String
                get() = "Enable"

            override fun doOkAction(project: Project, pluginId: PluginId) {
                PluginsAdvertiser.installAndEnable(project, setOf(pluginId), false) {}
            }
        },
        RESTART {
            override val message: String
                get() = "Need to restart ${ApplicationNamesInfo.getInstance().fullProductName} to apply changes in plugins"
            override val actionName: String
                get() = IdeBundle.message("ide.restart.action")

            override fun doOkAction(project: Project, pluginId: PluginId) {
                ApplicationManagerEx.getApplicationEx().restart(true)
            }
        };

        abstract val message: String
        abstract val actionName: String
        abstract fun doOkAction(project: Project, pluginId: PluginId)
    }
}
