/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.filters

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.serviceContainer.NonInjectable
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.runconfig.filters.RegexpFileLinkFilter.Companion.FILE_POSITION_RE
import org.rust.lang.core.resolve.resolveStringPath
import java.util.*
import java.util.regex.Pattern

/**
 * Adds features to stack backtraces:
 * - Wrap function calls into hyperlinks to source code.
 * - Turn source code links into hyperlinks.
 * - Dims function hash codes to reduce noise.
 */
class RsBacktraceFilter @NonInjectable constructor(
    private val project: Project,
    private val cargoProjectDir: VirtualFile?,
    private val workspace: CargoWorkspace?
) : Filter {
    private val backtraceItemFilters: List<RsBacktraceItemFilter>
        get() {
            if (workspace == null) {
                val filters = project.cargoProjects.allProjects
                    .mapNotNull { it.workspace }
                    .map { RsBacktraceItemFilter(project, it) }
                if (filters.isNotEmpty()) return filters
            }
            return listOf(RsBacktraceItemFilter(project, workspace))
        }

    private val sourceLinkFilters: List<RegexpFileLinkFilter>
        get() {
            if (cargoProjectDir == null) {
                return project.cargoProjects.allProjects
                    .mapNotNull { it.rootDir }
                    .map { RegexpFileLinkFilter(project, it, LINE_REGEX) }
            }
            return listOf(RegexpFileLinkFilter(project, cargoProjectDir, LINE_REGEX))
        }


    constructor(project: Project) : this(project, null, null)

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? =
        (backtraceItemFilters.asSequence() + sourceLinkFilters.asSequence())
            .mapNotNull { it.applyFilter(line, entireLength) }
            .firstOrNull()

    companion object {
        val LINE_REGEX: String = "\\s+at $FILE_POSITION_RE"
    }
}

/**
 * Adds hyperlinks to function names in backtraces
 */
class RsBacktraceItemFilter(
    val project: Project,
    val workspace: CargoWorkspace?
) : Filter {
    private val docManager = PsiDocumentManager.getInstance(project)

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val (header, funcName, funcHash) = parseBacktraceRecord(line) ?: return null
        val normFuncName = FilterUtils.normalizeFunctionPath(funcName)
        val resultItems = ArrayList<Filter.ResultItem>(2)

        // Add hyperlink to the function name
        val funcStart = entireLength - line.length + header.length
        val funcEnd = funcStart + funcName.length
        if (SKIP_PREFIXES.none { normFuncName.startsWith(it) }) {
            extractFnHyperlink(normFuncName, funcStart, funcEnd)?.let { resultItems.add(it) }
        }

        // Dim the hashcode
        if (funcHash != null) {
            resultItems.add(Filter.ResultItem(funcEnd, funcEnd + funcHash.length, null, DIMMED_TEXT))
        }

        return Filter.Result(resultItems)
    }

    private fun extractFnHyperlink(funcName: String, start: Int, end: Int): Filter.ResultItem? {
        val workspace = workspace ?: return null
        val (element, pkg) = resolveStringPath(funcName, workspace, project) ?: return null
        val funcFile = element.containingFile
        val doc = docManager.getDocument(funcFile) ?: return null
        val link = OpenFileHyperlinkInfo(project, funcFile.virtualFile, doc.getLineNumber(element.textOffset))
        return Filter.ResultItem(start, end, link, pkg.origin != PackageOrigin.WORKSPACE)
    }

    companion object {
        val DIMMED_TEXT = EditorColorsManager.getInstance().globalScheme
            .getAttributes(TextAttributesKey.createTextAttributesKey("org.rust.DIMMED_TEXT"))!!
        val SKIP_PREFIXES = arrayOf(
            "std::rt::lang_start",
            "std::panicking",
            "std::sys::backtrace",
            "std::sys::imp::backtrace",
            "core::panicking")

        data class BacktraceRecord(val header: String, val functionName: String, val functionHash: String?)

        private val FUNCTION_PATTERN = Pattern.compile("^(\\s*\\d+:\\s+(?:0x[a-f0-9]+ - )?)(.+?)(::h[0-9a-f]+)?$")!!

        fun parseBacktraceRecord(line: String): BacktraceRecord? {
            val matcher = FUNCTION_PATTERN.matcher(line)
            if (!matcher.find()) return null
            val header = matcher.group(1)
            val funcName = matcher.group(2)
            val funcHash = matcher.group(3)
            return BacktraceRecord(header, funcName, funcHash)
        }
    }
}
