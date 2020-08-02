/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

import com.intellij.execution.ConsoleFolding
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil.pluralize
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.runconfig.filters.FilterUtils
import org.rust.cargo.runconfig.filters.RegexpFileLinkFilter
import org.rust.cargo.runconfig.filters.RsBacktraceFilter
import org.rust.cargo.runconfig.filters.RsBacktraceItemFilter
import org.rust.lang.core.resolve.resolveStringPath

/**
 * Folds backtrace items (function names and source code locations) that do not belong to the
 * user's workspace.
 */
class RsConsoleFolding : ConsoleFolding() {
    override fun getPlaceholderText(project: Project, lines: List<String>): String? {
        // We assume that each stacktrace record has two lines (function name and source code location).
        // Single line folds also cannot be re-folded after they are opened as of 2020.2
        if (lines.size < 2) return null

        val count = lines.size / 2
        val callText = pluralize("call", count)
        return "<$count internal $callText>"
    }

    override fun shouldFoldLine(project: Project, line: String): Boolean {
        val functionNameFound = project.cargoProjects.allProjects.any {
            val functionName = RsBacktraceItemFilter.parseBacktraceRecord(line)?.functionName ?: return@any false
            val func = FilterUtils.normalizeFunctionPath(functionName)
            val workspace = it.workspace ?: return@any false
            val (_, pkg) = resolveStringPath(func, workspace, project) ?: return@any true

            pkg.origin != PackageOrigin.WORKSPACE
        }
        if (functionNameFound) return true

        return project.cargoProjects.allProjects.any {
            val dir = (it.workspaceRootDir ?: it.rootDir) ?: return@any false
            val filter = RegexpFileLinkFilter(project, dir, RsBacktraceFilter.LINE_REGEX)

            val filePath = filter.matchLine(line)?.groups?.get(1)?.value ?: return@any false
            val systemIndependentPath = FileUtil.toSystemIndependentName(filePath)

            dir.findFileByRelativePath(systemIndependentPath) == null
        }
    }

    override fun shouldBeAttachedToThePreviousLine(): Boolean = false
}
