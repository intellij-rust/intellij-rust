/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.configurable

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import org.rust.RsBundle
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.RustProjectSettingsService.*
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.ui.RustProjectSettingsPanel
import org.rust.cargo.toolchain.RsToolchainBase
import org.rust.openapiext.pathAsPath
import java.nio.file.Paths

class RsProjectConfigurable(project: Project) : RsConfigurableBase(project, RsBundle.message("settings.rust.toolchain.name")), Configurable.NoScroll {
    private val projectDir = project.cargoProjects.allProjects.firstOrNull()?.rootDir?.pathAsPath ?: Paths.get(".")
    // TODO: move creation of the panel inside `createPanel`
    //  and use `onIsModified`, `onApply` and `onReset` methods
    private val rustProjectSettings by lazy { RustProjectSettingsPanel(projectDir) }

    override fun createPanel(): DialogPanel = panel {
        val settings = project.rustSettings
        val state = settings.state.copy()

        rustProjectSettings.attachTo(this)

        row {
            checkBox(RsBundle.message("settings.rust.toolchain.expand.macros.checkbox"))
                .comment(RsBundle.message("settings.rust.toolchain.expand.macros.comment"))
                .bindSelected(
                    { state.macroExpansionEngine != MacroExpansionEngine.DISABLED },
                    { state.macroExpansionEngine = if (it) MacroExpansionEngine.NEW else MacroExpansionEngine.DISABLED }
                )
        }
        row {
            checkBox(RsBundle.message("settings.rust.toolchain.inject.rust.in.doc.comments.checkbox"))
                .bindSelected(state::doctestInjectionEnabled)
        }

        onApply {
            settings.modify {
                it.toolchain = rustProjectSettings.data.toolchain
                it.explicitPathToStdlib = rustProjectSettings.data.explicitPathToStdlib
                it.macroExpansionEngine = state.macroExpansionEngine
                it.doctestInjectionEnabled = state.doctestInjectionEnabled
            }
        }
        onReset {
            val newData = RustProjectSettingsPanel.Data(
                toolchain = settings.toolchain ?: RsToolchainBase.suggest(projectDir),
                explicitPathToStdlib = settings.explicitPathToStdlib
            )
            if (rustProjectSettings.data != newData) {
                rustProjectSettings.data = newData
            }
        }
        onIsModified {
            val data = rustProjectSettings.data
            data.toolchain?.location != settings.toolchain?.location
                || data.explicitPathToStdlib != settings.explicitPathToStdlib
        }
    }

    override fun disposeUIResources() {
        super.disposeUIResources()
        Disposer.dispose(rustProjectSettings)
    }

    @Throws(ConfigurationException::class)
    override fun apply() {
        rustProjectSettings.validateSettings()
        super.apply()
    }
}
