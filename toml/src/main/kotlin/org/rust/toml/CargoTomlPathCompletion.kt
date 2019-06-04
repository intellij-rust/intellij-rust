/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ProcessingContext
import org.rust.lang.core.completion.DEFAULT_PRIORITY
import org.rust.lang.core.completion.withPriority
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.toml.lang.psi.TomlValue

class CargoTomlPathCompletion private constructor(
    private val target: Target
) : CompletionProvider<CompletionParameters>() {

    private enum class Target {
        File, Directory, Any;

        fun priorityOf(file: VirtualFile): Double = when (this) {
            File -> if (file.isDirectory) DEFAULT_PRIORITY else 1.0
            Directory -> if (file.isDirectory) 1.0 else DEFAULT_PRIORITY
            Any -> DEFAULT_PRIORITY
        }
    }

    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val tomlValue = parameters.position.ancestorOrSelf<TomlValue>() ?: return
        val containingFile = tomlValue.containingFile.originalFile.virtualFile ?: return
        val path = CompletionUtil.getOriginalElement(tomlValue)?.text
            ?.removeSurrounding("\"")
            ?.replace('\\', '/') ?: return

        val parent = containingFile.parent ?: return
        // Substring before last '/' in order to convert from a path like a/b/c/Cargo.toml to a/b/c
        val dirPath = path.substringBeforeLast('/', missingDelimiterValue = "")
        val file = parent.findFileByRelativePath(dirPath) ?: parent
        val elements = file.children?.map {
            LookupElementBuilder.create(if (it.isDirectory) "${it.name}/" else it.name)
                .withIcon(if (it.isDirectory) AllIcons.Nodes.Folder else it.fileType.icon ?: AllIcons.FileTypes.Unknown)
                .withPriority(target.priorityOf(it))
        } ?: return
        // Before substring: a/b/c/Cargo.t    after: Cargo.t
        result.withPrefixMatcher(path.substringAfterLast('/')).addAllElements(elements)
    }

    companion object {
        /**
         * Prioritizes files
         */
        fun ofFiles() = CargoTomlPathCompletion(Target.File)

        /**
         * Prioritizes directories
         */
        fun ofDirectories() = CargoTomlPathCompletion(Target.Directory)

        /**
         * Prioritizes files and directories equally
         */
        fun ofAny() = CargoTomlPathCompletion(Target.Any)
    }
}
