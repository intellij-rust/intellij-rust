/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.project.Project
import org.rust.cargo.project.settings.RustProjectSettingsService.MacroExpansionEngine
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.rustfmtSettings
import org.rust.cargo.toolchain.ExternalLinter
import org.rust.cargo.toolchain.RustChannel
import java.util.*

@Suppress("UnstableApiUsage")
class RsSettingsUsagesCollector : ProjectUsagesCollector() {
    override fun getGroup(): EventLogGroup = GROUP

    override fun getMetrics(project: Project): Set<MetricEvent> {
        val metrics = mutableSetOf<MetricEvent>()

        val rustSettings = project.rustSettings
        val rustfmtSettings = project.rustfmtSettings

        metrics += PROJECT.metric(
            rustSettings.macroExpansionEngine,
            rustSettings.doctestInjectionEnabled
        )

        metrics += CARGO.metric(
            CARGO_AUTO_SHOW_ERRORS_IN_EDITOR.with(rustSettings.autoShowErrorsInEditor.toBoolean()),
            CARGO_AUTO_UPDATE.with(rustSettings.autoUpdateEnabled),
            CARGO_COMPILE_ALL_TARGETS.with(rustSettings.compileAllTargets),
            CARGO_OFFLINE.with(rustSettings.doctestInjectionEnabled)
        )

        metrics += RUSTFMT.metric(
            rustfmtSettings.state.useRustfmt,
            rustfmtSettings.state.runRustfmtOnSave,
            rustfmtSettings.state.channel
        )

        metrics += EXTERNAL_LINTER.metric(
            rustSettings.externalLinter,
            rustSettings.runExternalLinterOnTheFly
        )

        return metrics
    }

    companion object {
        private val GROUP = EventLogGroup("rust.settings", 1)

        private val PROJECT = GROUP.registerEvent(
            "project",
            EventFields.Enum<MacroExpansionEngine>("macro_expansion_engine") { it.toString().lowercase(Locale.ENGLISH) },
            EventFields.Boolean("doctest_injection")
        )

        private val CARGO_AUTO_SHOW_ERRORS_IN_EDITOR = EventFields.Boolean("auto_show_errors_in_editor")
        private val CARGO_AUTO_UPDATE = EventFields.Boolean("auto_update")
        private val CARGO_COMPILE_ALL_TARGETS = EventFields.Boolean("compile_all_targets")
        private val CARGO_OFFLINE = EventFields.Boolean("offline")

        private val CARGO = GROUP.registerVarargEvent(
            "cargo",
            CARGO_AUTO_SHOW_ERRORS_IN_EDITOR,
            CARGO_AUTO_UPDATE,
            CARGO_COMPILE_ALL_TARGETS,
            CARGO_OFFLINE
        )

        private val RUSTFMT = GROUP.registerEvent(
            "rustfmt",
            EventFields.Boolean("enabled"),
            EventFields.Boolean("run_on_save"),
            EventFields.Enum<RustChannel>("channel")
        )

        private val EXTERNAL_LINTER = GROUP.registerEvent(
            "external_linter",
            EventFields.Enum<ExternalLinter>("tool") { it.toString().lowercase(Locale.ENGLISH) },
            EventFields.Boolean("run_on_fly")
        )
    }
}
