package org.rust.cargo.runconfig

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.execution.filters.RegexpFilter
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDocumentManager
import org.rust.cargo.project.CargoProjectDescription
import org.rust.cargo.util.cargoProject
import org.rust.cargo.util.getPsiFor
import org.rust.cargo.util.modulesWithCargoProject
import org.rust.lang.core.psi.RustCompositeElement
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.resolve.RustResolveEngine
import org.rust.lang.core.symbols.RustPath
import org.rust.lang.core.symbols.RustPathHead
import org.rust.lang.core.symbols.RustPathSegment
import java.util.*
import java.util.regex.Pattern

/**
 * Adds features to stack backtraces:
 * - Wrap function calls into hyperlinks to source code.
 * - Turn source code links into hyperlinks.
 * - Dims funcion hashcodes to remove noise.
 */
class RustBacktraceFilter(
    project: Project,
    cargoProjectDir: VirtualFile
) : Filter {
    private val sourceLinkFilter = RegexpFileLinkFilter(project, cargoProjectDir, "^\\s+at ${RegexpFilter.FILE_PATH_MACROS}:${RegexpFilter.LINE_MACROS}$")
    private val backtraceItemFilter = RustBacktraceItemFilter(project)

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        backtraceItemFilter.applyFilter(line, entireLength)?.let { return it }
        sourceLinkFilter.applyFilter(line, entireLength)?.let { return it }
        return null
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
        val funcName = matcher.group(2)
        val resultItems = ArrayList<Filter.ResultItem>(2)

        // Add hyperlink to the function name
        val funcStart = entireLength - line.length + matcher.group(1).length
        if (SKIP_PREFIXES.none { funcName.startsWith(it) }) {
            extractFnHyperlink(funcName, funcStart)?.let { resultItems.add(it) }
        }

        // Dim the hashcode
        val funcEnd = funcStart + funcName.length
        resultItems.add(Filter.ResultItem(funcEnd, funcEnd + matcher.group(3).length, null, DIMMED_TEXT))

        return Filter.Result(resultItems)
    }

    private fun extractFnHyperlink(funcName: String, start: Int): Filter.ResultItem? {
        val func = ElementResolver.resolve(funcName, project) ?: return null
        val funcFile = func.element.containingFile
        val doc = docManager.getDocument(funcFile) ?: return null
        val link = OpenFileHyperlinkInfo(project, funcFile.virtualFile, doc.getLineNumber(func.element.textOffset))
        return Filter.ResultItem(start, start + funcName.length, link, func.pkg.isExternal)
    }

    private companion object {
        val DIMMED_TEXT = EditorColorsManager.getInstance().globalScheme
            .getAttributes(TextAttributesKey.createTextAttributesKey("org.rust.DIMMED_TEXT"))!!
        val SKIP_PREFIXES = arrayOf(
            // TODO: Put the whole std:: and core:: here?
//            "std::sys::backtrace::",
//            "std::panicking::",
            "std::rt::lang_start"
        )
    }
}


private object ElementResolver {
    private val vfm = VirtualFileManager.getInstance()

    data class Result (
        val element: RustNamedElement,
        val pkg: CargoProjectDescription.Package
    )

    // TODO: Move to RustResolveEngine
    fun resolve(pathStr: String, project: Project) : Result? {
        val segments = pathStr.segments
        if (segments.isEmpty()) return null
        val pkg = project.getPackage(segments[0].name) ?: return null
        val path = RustPath(RustPathHead.Absolute, segments.drop(1))
        val el = pkg.targets
            .mapNotNull { vfm.findFileByUrl(it.crateRootUrl) }
            .mapNotNull { project.getPsiFor(it) as? RustCompositeElement }
            .flatMap { RustResolveEngine.resolve(path, it) }
            .mapNotNull { it as? RustNamedElement }
            .firstOrNull() ?: return null

        return Result(el, pkg)
    }

    private fun Project.getPackage(name: String): CargoProjectDescription.Package? =
        modulesWithCargoProject
            .mapNotNull { it.cargoProject }
            .flatMap { it.packages }
            .filter { it.isModule && it.name == name }
            .firstOrNull()

    private val String.segments: List<RustPathSegment>
        get() = normalize()
            .splitToSequence("::")
            .filter { it != "{{closure}}" }
            .map { RustPathSegment(it, emptyList()) }
            .toList()

    private fun String.normalize(): String = when (this) {        // TODO: Implement conversion. And move to RustPath?
        "<core::option::Option<T>>::unwrap" -> "core::option::Option::unwrap"
        "<core::result::Result<T, E>>::unwrap" -> "core::result::Result::unwrap"
        else -> this
    }
}
