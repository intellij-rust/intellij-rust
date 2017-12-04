/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter.processors

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.impl.source.codeStyle.PreFormatProcessor
import org.rust.lang.core.psi.RsElementTypes
import org.rust.lang.core.psi.RsUseGroup
import org.rust.lang.core.psi.RsUseSpeck
import org.rust.lang.core.psi.ext.elementType

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

        var numRemovedBraces = 0
        element.psi.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (range.contains(element.textRange)) {
                    super.visitElement(element)
                }

                if (element is RsUseGroup && removeBracesAroundSingleImport(element)) {
                    numRemovedBraces += 2
                }
            }
        })
        return range.grown(-numRemovedBraces)
    }

    fun removeBracesAroundSingleImport(group: RsUseGroup): Boolean {
        val (lbrace, rbrace) = group.asTrivial ?: return false
        lbrace.delete()
        rbrace.delete()
        return true
    }
}


data class TrivialUseGroup(
    val lbrace: PsiElement,
    val rbrace: PsiElement,
    val name: String
)

val RsUseGroup.asTrivial: TrivialUseGroup?
    get() {
        val lbrace = lbrace
        val rbrace = rbrace ?: return null
        if (!(lbrace.elementType == RsElementTypes.LBRACE && rbrace.elementType == RsElementTypes.RBRACE)) {
            return null
        }
        val speck = useSpeckList.singleOrNull() ?: return null
        if (!speck.isIdentifier) return null
        return TrivialUseGroup(lbrace, rbrace, speck.text)
    }

private val RsUseSpeck.isIdentifier: Boolean
    get() {
        val path = path
        if (!(path != null && path == firstChild && path == lastChild)) return false
        return (path.identifier != null && path.path == null && path.coloncolon == null)
    }
