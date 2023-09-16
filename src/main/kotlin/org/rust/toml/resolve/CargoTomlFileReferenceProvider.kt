/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.resolve

import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext
import org.rust.lang.core.or
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.toml.CargoTomlPsiPattern
import org.rust.toml.resolve.PathPatternType.*
import org.toml.lang.psi.TomlHeaderOwner
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.ext.TomlLiteralKind
import org.toml.lang.psi.ext.kind
import org.toml.lang.psi.ext.name

class CargoTomlFileReferenceProvider(private val patternType: PathPatternType) : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<FileReference> {
        val kind = (element as? TomlLiteral)?.kind ?: return emptyArray()
        if (kind !is TomlLiteralKind.String) return emptyArray()

        val (completeDirs, completeRustFiles) = when (patternType) {
            WORKSPACE -> true to false
            GENERAL -> {
                val table = element.ancestorStrict<TomlHeaderOwner>()
                val completeRustFiles = table != null && table.header.key?.name in TARGET_TABLE_NAMES
                true to completeRustFiles
            }
            BUILD -> false to true
        }

        val ignoreGlobs = patternType == WORKSPACE &&
            element.parentOfType<TomlKeyValue>()?.key?.segments?.firstOrNull()?.name in KEYS_SUPPORTING_GLOB

        val referenceSet: FileReferenceSet = if (ignoreGlobs) {
            GlobIgnoringFileReferenceSet(element, completeDirs, completeRustFiles)
        } else {
            CargoTomlFileReferenceSet(element, completeDirs, completeRustFiles)
        }

        return referenceSet.allReferences
    }

    companion object {
        private val TARGET_TABLE_NAMES = listOf("lib", "bin", "test", "bench", "example")
        private val KEYS_SUPPORTING_GLOB = listOf("members", "default-members")
    }
}

enum class PathPatternType(val pattern: PsiElementPattern.Capture<out PsiElement>) {
    GENERAL(CargoTomlPsiPattern.path),
    WORKSPACE(CargoTomlPsiPattern.workspacePath or CargoTomlPsiPattern.packageWorkspacePath),
    BUILD(CargoTomlPsiPattern.buildPath)
}

private open class CargoTomlFileReferenceSet(
    element: TomlLiteral,
    private val completeDirs: Boolean,
    private val completeRustFiles: Boolean
) : FileReferenceSet(element) {
    override fun getReferenceCompletionFilter(): Condition<PsiFileSystemItem> {
        return Condition {
            when (it) {
                is PsiDirectory -> completeDirs
                is RsFile -> completeRustFiles
                else -> false
            }
        }
    }
}

private class GlobIgnoringFileReferenceSet(
    element: TomlLiteral,
    completeDirs: Boolean,
    completeRustFiles: Boolean
) : CargoTomlFileReferenceSet(element, completeDirs, completeRustFiles) {
    private var globPatternFound: Boolean = false

    override fun reparse() {
        globPatternFound = false
        super.reparse()
    }

    override fun createFileReference(range: TextRange, index: Int, text: String): FileReference? {
        if (!globPatternFound && isGlobPathFragment(text)) {
            globPatternFound = true
        }
        if (globPatternFound) return null
        return super.createFileReference(range, index, text)
    }
}

private fun isGlobPathFragment(text: String?): Boolean {
    if (text == null) return false
    return text.contains("?") || text.contains("*") || text.contains("[") || text.contains("]")
}
