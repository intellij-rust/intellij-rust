package org.rust.cargo.runconfig

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.execution.filters.RegexpFilter
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import org.rust.lang.core.resolve.RustResolveEngine
import java.awt.Color
import java.awt.Font
import java.util.*
import java.util.regex.Pattern

/**
 * Adds features to stack backtraces:
 * - Wrap function calls into hyperlinks to source code.
 * - Turn source code links into hyperlinks.
 * - Dims function hash codes to reduce noise.
 */
class RustBacktraceFilter(
    project: Project,
    cargoProjectDir: VirtualFile
) : Filter {
    private val sourceLinkFilter = RegexpFileLinkFilter(project, cargoProjectDir, "^\\s+at ${RegexpFilter.FILE_PATH_MACROS}:${RegexpFilter.LINE_MACROS}$")
    private val backtraceItemFilter = RustBacktraceItemFilter(project)

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        return backtraceItemFilter.applyFilter(line, entireLength)
            ?: sourceLinkFilter.applyFilter(line, entireLength)
    }
}

/**
 * Adds hyperlinks to function names in backtraces
 */
private class RustBacktraceItemFilter(
    val project: Project
) : Filter {
    private val pattern = Pattern.compile("^(\\s*\\d+:\\s+0x[a-f0-9]+ - )(.+)(::h[0-9a-f]+)$")!!
    private val docManager = PsiDocumentManager.getInstance(project)

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val matcher = pattern.matcher(line)
        if (!matcher.find()) return null
        val header = matcher.group(1)
        val funcName = matcher.group(2)
        val funcHash = matcher.group(3)
        val normFuncName = funcName.normalize()
        val resultItems = ArrayList<Filter.ResultItem>(2)

        // Add hyperlink to the function name
        val funcStart = entireLength - line.length + header.length
        val funcEnd = funcStart + funcName.length
        if (SKIP_PREFIXES.none { normFuncName.startsWith(it) }) {
            extractFnHyperlink(normFuncName, funcStart, funcEnd)?.let { resultItems.add(it) }
        }

        // Dim the hashcode
        resultItems.add(Filter.ResultItem(funcEnd, funcEnd + funcHash.length, null, DIMMED_TEXT))

        return Filter.Result(resultItems)
    }

    private fun extractFnHyperlink(funcName: String, start: Int, end: Int): Filter.ResultItem? {
        val func = RustResolveEngine.resolve(funcName, project) ?: return null
        val funcFile = func.element.containingFile
        val doc = docManager.getDocument(funcFile) ?: return null
        val link = OpenFileHyperlinkInfo(project, funcFile.virtualFile, doc.getLineNumber(func.element.textOffset))
        val linkAttr = if (func.pkg.isExternal) GRAYED_LINK else null
        return Filter.ResultItem(start, end, link, linkAttr)
    }

    /**
     * Normalizes function path:
     * - Removes angle brackets from the element path, including enclosed contents when necessary.
     * - Removes closure markers.
     * Examples:
     * - <core::option::Option<T>>::unwrap -> core::option::Option::unwrap
     * - std::panicking::default_hook::{{closure}} -> std::panicking::default_hook
     */
    private fun String.normalize(): String {
        var str = this
        while (str.endsWith("::{{closure}}")) {
            str = str.substringBeforeLast("::")
        }
        while (true) {
            val range = str.findAngleBrackets() ?: break
            val idx = str.indexOf("::", range.start + 1)
            if (idx < 0 || idx > range.endInclusive) {
                str = str.removeRange(range)
            } else {
                str = str.removeRange(IntRange(range.endInclusive, range.endInclusive))
                    .removeRange(IntRange(range.start, range.start))
            }
            println("$this -> $str")
        }
        return str
    }

    /**
     * Finds the range of the first matching angle brackets within the string.
     */
    private fun String.findAngleBrackets(): IntRange? {
        var start = -1
        var counter = 0
        loop@ for (i in 0..(length - 1)) {
            when (this[i]) {
                '<' -> {
                    if (start < 0) {
                        start = i
                    }
                    counter += 1
                }
                '>' -> counter -= 1
                else -> continue@loop
            }
            if (counter == 0) {
                val range = IntRange(start, i)
                return range
            }
        }
        return null
    }

    private companion object {
        val DIMMED_TEXT = EditorColorsManager.getInstance().globalScheme
            .getAttributes(TextAttributesKey.createTextAttributesKey("org.rust.DIMMED_TEXT"))!!
        val GRAYED_LINK_COLOR = Color(135, 135, 135)
        val GRAYED_LINK = TextAttributes(GRAYED_LINK_COLOR, null, GRAYED_LINK_COLOR, EffectType.LINE_UNDERSCORE, Font.PLAIN)
        val SKIP_PREFIXES = arrayOf(
            "std::rt::lang_start",
            "std::panicking",
            "std::sys::backtrace",
            "core::panicking")
    }
}
