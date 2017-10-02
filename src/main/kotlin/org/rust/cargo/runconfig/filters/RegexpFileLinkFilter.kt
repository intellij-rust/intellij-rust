/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.filters

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.lang.annotations.Language
import org.rust.openapiext.findFileByMaybeRelativePath
import java.io.File

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
    }

    init {
        require(FILE_POSITION_RE in lineRegExp)
        require('^' !in lineRegExp && '$' !in lineRegExp)
    }

    private val linePattern = ("^$lineRegExp\\R?$").toRegex()

    // Line is a single sine, with line separator included
    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val match = linePattern.matchEntire(line)
            ?: return null
        val fileGroup = match.groups[1]!!
        val lineNumber = match.groups[2]?.let { zeroBasedNumber(it.value) } ?: 0
        val columnNumber = match.groups[3]?.let { zeroBasedNumber(it.value) } ?: 0

        val lineStart = entireLength - line.length
        return Filter.Result(
            lineStart + fileGroup.range.start,
            lineStart + fileGroup.range.endInclusive + 1,
            createOpenFileHyperlink(fileGroup.value, lineNumber, columnNumber)
        )
    }

    private fun zeroBasedNumber(number: String): Int {
        return try {
            Math.max(0, number.toInt() - 1)
        } catch (e: NumberFormatException) {
            0
        }
    }

    private fun createOpenFileHyperlink(fileName: String, line: Int, column: Int): HyperlinkInfo? {
        val platformNeutralName = fileName.replace(File.separatorChar, '/')
        val file = cargoProjectDirectory.findFileByMaybeRelativePath(platformNeutralName) ?: return null
        return OpenFileHyperlinkInfo(project, file, line, column)
    }
}
