/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult
import org.rust.ide.refactoring.isValidRustVariableIdentifier
import org.rust.lang.core.psi.RsElementTypes.IDENTIFIER
import org.rust.lang.core.psi.RsElementTypes.QUOTE_IDENTIFIER
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.escapeIdentifierIfNeeded
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsWeakReferenceElement
import org.rust.lang.core.psi.ext.elementType

abstract class RsReferenceBase<T : RsWeakReferenceElement>(
    element: T
) : PsiPolyVariantReferenceBase<T>(element),
    RsReference {

    override fun resolve(): RsElement? = super.resolve() as? RsElement

    override fun multiResolve(incompleteCode: Boolean): Array<out ResolveResult> =
        multiResolve().map { PsiElementResolveResult(it) }.toTypedArray()

    abstract val T.referenceAnchor: PsiElement?

    final override fun getRangeInElement(): TextRange = super.getRangeInElement()

    final override fun calculateDefaultRangeInElement(): TextRange {
        val anchor = element.referenceAnchor ?: return TextRange.EMPTY_RANGE
        check(anchor.parent === element)
        return TextRange.from(anchor.startOffsetInParent, anchor.textLength)
    }

    override fun handleElementRename(newName: String): PsiElement {
        val referenceNameElement = element.referenceNameElement
        if (referenceNameElement != null) {
            doRename(referenceNameElement, newName)
        }
        return element
    }

    override fun getVariants(): Array<out LookupElement> = LookupElement.EMPTY_ARRAY

    override fun equals(other: Any?): Boolean = other is RsReferenceBase<*> && element === other.element

    override fun hashCode(): Int = element.hashCode()

    companion object {
        @JvmStatic protected fun doRename(identifier: PsiElement, newName: String) {
            val factory = RsPsiFactory(identifier.project)
            val newId = when (identifier.elementType) {
                IDENTIFIER -> {
                    // Renaming files is tricky: we don't want to change `RenamePsiFileProcessor`,
                    // so we must be ready for invalid names here
                    val name = newName.replace(".rs", "").escapeIdentifierIfNeeded()
                    if (!isValidRustVariableIdentifier(name)) return
                    factory.createIdentifier(name)

                }
                QUOTE_IDENTIFIER -> factory.createQuoteIdentifier(newName)
                else -> error("Unsupported identifier type for `$newName` (${identifier.elementType})")
            }
            identifier.replace(newId)
        }
    }
}
