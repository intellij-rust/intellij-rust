/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.configurable

import com.intellij.ide.DataManager
import com.intellij.openapi.externalSystem.service.settings.ExternalSystemGroupConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import org.rust.RsBundle
import org.rust.cargo.project.model.isNewProjectModelImportEnabled
import org.rust.cargo.project.settings.rustSettings
import java.awt.Component

class CargoConfigurable(
    project: Project,
    private val isPlaceholder: Boolean
) : RsConfigurableBase(project, RsBundle.message("settings.rust.cargo.name")) {

    override fun createPanel(): DialogPanel {
        return if (isPlaceholder) createPlaceholderPanel() else createSettingsPanel()
    }

    private fun createSettingsPanel(): DialogPanel = panel {
        val settings = project.rustSettings
        val state = settings.state.copy()

        // Rider doesn't provide `Build, Execution, Deployment | Build Tools` settings panel at all.
        // Let's add the corresponding settings manually as a temporary workaround
        if (isNewProjectModelImportEnabled && !buildToolsConfigurableExists(project)) {
            val panel = ExternalSystemGroupConfigurable(project).createPanel()
            row {
                cell(panel)
                    .onApply { panel.apply() }
                    .onIsModified { panel.isModified() }
                    .onReset { panel.reset() }
            }.bottomGap(BottomGap.MEDIUM)
        }

        row {
            checkBox(RsBundle.message("settings.rust.cargo.show.first.error.label"))
                .bindSelected(state::autoShowErrorsInEditor)
        }
        // Project model updates is controlled with `Preferences | Build, Execution, Deployment | Build Tools` settings
        // in case of new approach
        if (!isNewProjectModelImportEnabled) {
            row {
                checkBox(RsBundle.message("settings.rust.cargo.auto.update.project.label"))
                    .bindSelected(state::autoUpdateEnabled)
            }
        }
        row {
            checkBox(RsBundle.message("settings.rust.cargo.compile.all.targets.label"))
                .comment(RsBundle.message("settings.rust.cargo.compile.all.targets.comment"))
                .bindSelected(state::compileAllTargets)
        }
        row {
            checkBox(RsBundle.message("settings.rust.cargo.offline.mode.label"))
                .comment(RsBundle.message("settings.rust.cargo.offline.mode.comment"))
                .bindSelected(state::useOffline)
        }

        onApply {
            settings.modify {
                it.autoShowErrorsInEditor = state.autoShowErrorsInEditor
                it.autoUpdateEnabled = state.autoUpdateEnabled
                it.compileAllTargets = state.compileAllTargets
                it.useOffline = state.useOffline
            }
        }
    }

    private fun createPlaceholderPanel(): DialogPanel {
        var callback = { }

        val panel = panel {
            row {
                link(RsBundle.message("settings.rust.cargo.moved.label")) { callback() }
                    .resizableColumn()
                    .horizontalAlign(HorizontalAlign.CENTER)
            }.resizableRow()
        }

        callback = { openCargoSettings(panel) }

        return panel
    }

    private fun openCargoSettings(component: Component) {
        val dataContext = DataManager.getInstance().getDataContext(component)
        val settings = Settings.KEY.getData(dataContext)
        if (settings != null) {
            val configurable = settings.find("language.rust.build.tool.cargo")
            settings.select(configurable)
        }
    }

    companion object {
        fun buildToolsConfigurableExists(project: Project): Boolean {
            val buildToolsConfigurable = Configurable.PROJECT_CONFIGURABLE.findFirstSafe(project) { it.id == "build.tools" }
            return buildToolsConfigurable != null
        }
    }
}
