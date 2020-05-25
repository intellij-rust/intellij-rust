/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.filters

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.lang.annotations.Language
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.rustSettings
import java.nio.file.Paths

/**
 * Base class for regexp-based output filters that extract
 * source code location from the output and add corresponding hyperlinks.
 *
 * Can't use [com.intellij.execution.filters.RegexpFilter] directly because it doesn't handle
 * relative paths in 2017.1
 */
open class RegexpFileLinkFilter(
    private val project: Project,
    private val cargoProjectDirectory: VirtualFile,
    lineRegExp: String
) : Filter, DumbAware {

    companion object {
        // TODO: named groups when Kotlin supports them
        @Language("RegExp")
        val FILE_POSITION_RE = """((?:\p{Alpha}:)?[0-9 a-z_A-Z\-\\./]+):([0-9]+)(?::([0-9]+))?"""

        @Language("RegExp")
        private val RUSTC_ABSOLUTE_PATH_RE = Regex("""/rustc/\w+/(.*)""")
    }

    init {
        require(FILE_POSITION_RE in lineRegExp)
        require('^' !in lineRegExp && '$' !in lineRegExp)
    }

    private val linePattern = ("^$lineRegExp\\R?$").toRegex()

    // Line is a single sine, with line separator included
    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val match = matchLine(line) ?: return null
        val fileGroup = match.groups[1]!!
        val lineNumber = match.groups[2]?.let { zeroBasedNumber(it.value) } ?: 0
        val columnNumber = match.groups[3]?.let { zeroBasedNumber(it.value) } ?: 0

        val lineStart = entireLength - line.length

        val file = resolveFilePath(fileGroup.value)
        val link = file?.let { OpenFileHyperlinkInfo(project, file.file, lineNumber, columnNumber) }

        val grayedOut = if (file == null) {
            false
        } else {
            file !is ResolvedPath.Workspace
        }

        val end = match.groups[3]?.range?.last
            ?: match.groups[2]?.range?.last
            ?: fileGroup.range.last
        return Filter.Result(
            lineStart + fileGroup.range.first,
            lineStart + end + 1,
            link,
            grayedOut
        )
    }

    fun matchLine(line: String): MatchResult? = linePattern.matchEntire(line)

    private fun zeroBasedNumber(number: String): Int {
        return try {
            Math.max(0, number.toInt() - 1)
        } catch (e: NumberFormatException) {
            0
        }
    }

    private fun resolveFilePath(fileName: String): ResolvedPath? {
        val path = FileUtil.toSystemIndependentName(fileName)
        val file = cargoProjectDirectory.findFileByRelativePath(path)
        if (file != null) return ResolvedPath.Workspace(file)

        val externalPath = resolveStdlibPath(fileName) ?: resolveCargoPath(fileName)
        if (externalPath != null) return externalPath

        // try to resolve absolute path
        return cargoProjectDirectory.fileSystem.findFileByPath(path)?.let { ResolvedPath.Unknown(it) }
    }

    private fun resolveCargoPath(path: String): ResolvedPath? {
        if (!path.startsWith("/cargo")) return null
        val fullPath = Paths.get(getCargoRoot(), path.removePrefix("/cargo")).toString()
        return cargoProjectDirectory.fileSystem.findFileByPath(fullPath)?.let { ResolvedPath.CargoDependency(it) }
    }

    private fun resolveStdlibPath(path: String): ResolvedPath? {
        val sysroot = getSysroot() ?: return null
        val normalizedPath = normalizeStdLibPath(path)
        val fullPath = "$sysroot/lib/rustlib/src/rust/$normalizedPath"
        return cargoProjectDirectory.fileSystem.findFileByPath(fullPath)?.let { ResolvedPath.Stdlib(it) }
    }

    // /rustc/<commit hash>/src/libstd/... -> src/libstd/...
    private fun normalizeStdLibPath(path: String): String {
        val match = RUSTC_ABSOLUTE_PATH_RE.matchEntire(path) ?: return path
        return match.groupValues[1]
    }

    private fun getSysroot(): String? = project.cargoProjects.allProjects.firstOrNull()?.rustcInfo?.sysroot
    private fun getCargoRoot(): String = project.rustSettings.toolchain?.location?.parent.toString()

    sealed class ResolvedPath(val file: VirtualFile) {
        class Workspace(file: VirtualFile) : ResolvedPath(file)
        class Stdlib(file: VirtualFile) : ResolvedPath(file)
        class CargoDependency(file: VirtualFile) : ResolvedPath(file)
        class Unknown(file: VirtualFile) : ResolvedPath(file)
    }
}
