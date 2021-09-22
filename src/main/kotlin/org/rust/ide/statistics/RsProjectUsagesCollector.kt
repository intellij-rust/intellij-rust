/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.PackageId
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.lang.core.crate.crateGraph

@Suppress("UnstableApiUsage")
class RsProjectUsagesCollector : ProjectUsagesCollector() {
    override fun getGroup(): EventLogGroup = GROUP

    override fun getMetrics(project: Project): Set<MetricEvent> {
        val cargoProjects = project.cargoProjects.allProjects
        if (cargoProjects.isEmpty()) return emptySet()

        val metrics = mutableSetOf<MetricEvent>()
        metrics += CARGO_PROJECTS_EVENT.metric(cargoProjects.size)

        val crates = runReadAction {
            project.crateGraph.topSortedCrates
        }

        val directDependencyIds = mutableSetOf<String>()
        val workspaceInfo = PackagesInfo()
        val dependenciesInfo = PackagesInfo()
        for (crate in crates) {
            val target = crate.cargoTarget ?: continue
            val pkg = target.pkg
            val info = when (pkg.origin) {
                PackageOrigin.WORKSPACE -> workspaceInfo
                PackageOrigin.DEPENDENCY -> dependenciesInfo
                else -> continue
            }
            info.packageIds += pkg.id
            when {
                target.kind.isCustomBuild -> info.buildScriptCount += 1
                target.kind.isProcMacro -> info.procMacroLibCount += 1
            }
            if (pkg.origin == PackageOrigin.WORKSPACE) {
                for ((_, dependencyCrate) in crate.dependencies) {
                    if (dependencyCrate.origin == PackageOrigin.DEPENDENCY) {
                        directDependencyIds += dependencyCrate.cargoTarget?.pkg?.id ?: continue
                    }
                }
            }
        }

        metrics += PACKAGES.metric(
            workspaceInfo.packageIds.size,
            directDependencyIds.size,
            dependenciesInfo.packageIds.size
        )
        metrics += COMPILE_TIME_TARGETS.metric(
            BUILD_SCRIPT_WORKSPACE.with(workspaceInfo.buildScriptCount),
            BUILD_SCRIPT_DEPENDENCY.with(dependenciesInfo.buildScriptCount),
            PROC_MACRO_WORKSPACE.with(workspaceInfo.procMacroLibCount),
            PROC_MACRO_DEPENDENCY.with(dependenciesInfo.procMacroLibCount)
        )
        return metrics
    }

    private data class PackagesInfo(
        val packageIds: MutableSet<PackageId> = mutableSetOf(),
        var buildScriptCount: Int = 0,
        var procMacroLibCount: Int = 0
    )

    companion object {
        private val GROUP = EventLogGroup("rust.project", 1)

        private val CARGO_PROJECTS_EVENT = GROUP.registerEvent("cargo_projects", EventFields.RoundedInt("count"))

        private val PACKAGES = GROUP.registerEvent("packages",
            EventFields.RoundedInt("workspace"),
            EventFields.RoundedInt("direct_dependency"),
            EventFields.RoundedInt("dependency")
        )

        private val PROC_MACRO_WORKSPACE = EventFields.RoundedInt("proc_macro_workspace")
        private val PROC_MACRO_DEPENDENCY = EventFields.RoundedInt("proc_macro_dependency")
        private val BUILD_SCRIPT_WORKSPACE = EventFields.RoundedInt("build_script_workspace")
        private val BUILD_SCRIPT_DEPENDENCY = EventFields.RoundedInt("build_script_dependency")

        private val COMPILE_TIME_TARGETS = GROUP.registerVarargEvent("compile_time_targets",
            BUILD_SCRIPT_WORKSPACE,
            BUILD_SCRIPT_DEPENDENCY,
            PROC_MACRO_WORKSPACE,
            PROC_MACRO_DEPENDENCY,
        )
    }
}
