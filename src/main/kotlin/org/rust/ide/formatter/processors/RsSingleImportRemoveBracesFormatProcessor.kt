/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter.processors

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.impl.source.codeStyle.PreFormatProcessor
import org.rust.lang.core.psi.RsElementTypes
import org.rust.lang.core.psi.RsUseGroup
import org.rust.lang.core.psi.RsUseSpeck
import org.rust.lang.core.psi.ext.childrenOfType

/**
 * Pre format processor ensuring that if an import statement only contains a single import from a crate that
 * there are no curly braces surrounding it.
 *
 * For example the following would change like so:
 *
 * `use getopts::{optopt};` --> `use getopts::optopt;`
 *
 * While this wouldn't change at all:
 *
 * `use getopts::{optopt, optarg};`
 *
 */
class RsSingleImportRemoveBracesFormatProcessor : PreFormatProcessor {
    override fun process(element: ASTNode, range: TextRange): TextRange {
        if (!shouldRunPunctuationProcessor(element)) return range

        var deletedSymbolsCount = 0
        element.psi.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (range.contains(element.textRange)) {
                    super.visitElement(element)
                }

                if (element is RsUseGroup) {
                    val speck = element.asTrivial ?: return
                    deletedSymbolsCount = element.textLength - speck.textLength
                    element.replace(speck.copy())
                }
            }
        })
        return range.grown(-deletedSymbolsCount)
    }
}

val RsUseGroup.asTrivial: RsUseSpeck?
    get() {
        // Do not change use-groups with comments
        if (childrenOfType<PsiComment>().isNotEmpty()) return null
        val speck = useSpeckList.singleOrNull() ?: return null
        if (!speck.isIdentifier) return null
        return speck
    }

private val RsUseSpeck.isIdentifier: Boolean
    get() {
        val path = path
        if (!(path != null && path == firstChild && path == lastChild)) return false
        return (path.identifier != null && path.path == null && path.coloncolon == null)
    }
