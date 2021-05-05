/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.internal.statistic.utils.StatisticsUtil.getNextPowerOfTwo
import com.intellij.openapi.project.Project
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.PackageOrigin

@Suppress("UnstableApiUsage")
class RsProjectUsagesCollector : ProjectUsagesCollector() {
    override fun getGroup(): EventLogGroup = GROUP

    override fun getMetrics(project: Project): Set<MetricEvent> {
        val metrics = mutableSetOf<MetricEvent>()
        val cargoProjects = project.cargoProjects.allProjects
        metrics += CARGO_PROJECTS_EVENT.metric(getNextPowerOfTwo(cargoProjects.size))

        val workspaceInfo = PackagesInfo()
        val dependenciesInfo = PackagesInfo()
        for (cargoProject in cargoProjects) {
            for (pkg in cargoProject.workspace?.packages.orEmpty()) {
                val info = when (pkg.origin) {
                    PackageOrigin.WORKSPACE -> workspaceInfo
                    PackageOrigin.DEPENDENCY -> dependenciesInfo
                    else -> continue
                }
                info.packageCount += 1
                for (target in pkg.targets) {
                    when  {
                        target.kind.isCustomBuild -> info.buildScriptCount += 1
                        target.kind.isProcMacro -> info.procMacroLibCount += 1
                    }
                }
            }
        }

        metrics += PACKAGES.metric(
            getNextPowerOfTwo(workspaceInfo.packageCount),
            getNextPowerOfTwo(dependenciesInfo.packageCount)
        )
        metrics += COMPILE_TIME_TARGETS.metric(
            BUILD_SCRIPT_WORKSPACE.with(getNextPowerOfTwo(workspaceInfo.buildScriptCount)),
            BUILD_SCRIPT_DEPENDENCY.with(getNextPowerOfTwo(dependenciesInfo.buildScriptCount)),
            PROC_MACRO_WORKSPACE.with(getNextPowerOfTwo(workspaceInfo.procMacroLibCount)),
            PROC_MACRO_DEPENDENCY.with(getNextPowerOfTwo(dependenciesInfo.procMacroLibCount))
        )
        return metrics
    }

    private data class PackagesInfo(
        var packageCount: Int = 0,
        var buildScriptCount: Int = 0,
        var procMacroLibCount: Int = 0
    )

    // BACKCOMPAT: 2021.1. use `EventFields.RoundedInt` instead of `EventFields.Int`
    companion object {
        private val GROUP = EventLogGroup("rust.project", 1)

        private val CARGO_PROJECTS_EVENT = GROUP.registerEvent("cargo_projects", EventFields.Count)

        private val PACKAGES = GROUP.registerEvent("packages",
            EventFields.Int("workspace"),
            EventFields.Int("dependency")
        )

        private val PROC_MACRO_WORKSPACE = EventFields.Int("proc_macro_workspace")
        private val PROC_MACRO_DEPENDENCY = EventFields.Int("proc_macro_dependency")
        private val BUILD_SCRIPT_WORKSPACE = EventFields.Int("build_script_workspace")
        private val BUILD_SCRIPT_DEPENDENCY = EventFields.Int("build_script_dependency")

        private val COMPILE_TIME_TARGETS = GROUP.registerVarargEvent("compile_time_targets",
            BUILD_SCRIPT_WORKSPACE,
            BUILD_SCRIPT_DEPENDENCY,
            PROC_MACRO_WORKSPACE,
            PROC_MACRO_DEPENDENCY,
        )
    }
}
