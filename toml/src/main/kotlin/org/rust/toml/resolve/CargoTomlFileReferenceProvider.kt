/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.resolve

import com.intellij.openapi.util.Condition
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
import com.intellij.util.ProcessingContext
import org.rust.lang.core.or
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.toml.CargoTomlPsiPattern
import org.rust.toml.resolve.PathPatternType.*
import org.toml.lang.psi.TomlHeaderOwner
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.ext.TomlLiteralKind
import org.toml.lang.psi.ext.kind

class CargoTomlFileReferenceProvider(private val patternType: PathPatternType) : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<FileReference> {
        val kind = (element as? TomlLiteral)?.kind ?: return emptyArray()
        val (completeDirs, completeRustFiles) = when (patternType) {
            WORKSPACE -> true to false
            GENERAL -> {
                val table = element.ancestorStrict<TomlHeaderOwner>()
                val completeRustFiles = table != null && table.header.names.singleOrNull()?.text in TARGET_TABLE_NAMES
                true to completeRustFiles
            }
            BUILD -> false to true
        }

        if (kind !is TomlLiteralKind.String) return emptyArray()
        return CargoTomlFileReferenceSet(element, completeDirs, completeRustFiles).allReferences
    }

    companion object {
        private val TARGET_TABLE_NAMES = listOf("lib", "bin", "test", "bench", "example")
    }
}

enum class PathPatternType(val pattern: PsiElementPattern.Capture<out PsiElement>) {
    GENERAL(CargoTomlPsiPattern.path),
    WORKSPACE(CargoTomlPsiPattern.workspacePath or CargoTomlPsiPattern.packageWorkspacePath),
    BUILD(CargoTomlPsiPattern.buildPath)
}

private class CargoTomlFileReferenceSet(
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
