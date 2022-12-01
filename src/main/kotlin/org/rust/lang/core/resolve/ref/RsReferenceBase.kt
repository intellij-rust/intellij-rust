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
import org.rust.lang.core.macros.findExpansionElements
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.escapeIdentifierIfNeeded
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsReferenceElementBase
import org.rust.lang.core.psi.ext.ancestors
import org.rust.lang.core.psi.ext.elementType
import org.rust.openapiext.Testmark
import kotlin.LazyThreadSafetyMode.PUBLICATION

@Suppress("IfThenToElvis")
abstract class RsReferenceBase<T : RsReferenceElementBase>(
    element: T
) : PsiPolyVariantReferenceBase<T>(element),
    RsReference {

    final override fun resolve(): RsElement? {
        val delegates = expandedDelegates
        return if (delegates == null) {
            resolveInner()
        } else {
            delegates.flatMap { it.multiResolve() }.distinct().singleOrNull()
        }
    }

    protected open fun resolveInner(): RsElement? = super.resolve() as? RsElement

    final override fun multiResolve(incompleteCode: Boolean): Array<out ResolveResult> {
        val delegates = expandedDelegates
        return if (delegates == null) {
            multiResolveInner(incompleteCode)
        } else {
            delegates.flatMap { it.multiResolve(incompleteCode).toList() }.distinct().toTypedArray()
        }
    }

    protected open fun multiResolveInner(incompleteCode: Boolean): Array<out ResolveResult> =
        multiResolveInner().map { PsiElementResolveResult(it) }.toTypedArray()

    final override fun multiResolve(): List<RsElement> {
        val delegates = expandedDelegates
        return if (delegates == null) {
            multiResolveInner()
        } else {
            delegates.flatMap { it.multiResolve() }.distinct()
        }
    }

    protected abstract fun multiResolveInner(): List<RsElement>

    final override fun isReferenceTo(element: PsiElement): Boolean {
        val delegates = expandedDelegates
        return if (delegates == null) {
            isReferenceToInner(element)
        } else {
            delegates.any { it.isReferenceTo(element) }
        }
    }

    protected open fun isReferenceToInner(element: PsiElement): Boolean {
        return super.isReferenceTo(element)
    }

    override val expandedDelegates: List<RsReference>? by lazy(PUBLICATION) {
        val expandedElements = element.findExpansionElements() ?: return@lazy null

        Testmarks.DelegatedToMacroExpansion.hit()

        val delegates = expandedElements.mapNotNull { delegated ->
            delegated.ancestors
                .mapNotNull { it.reference }
                .firstOrNull() as? RsReference
        }

        delegates
    }

    open val T.referenceAnchor: PsiElement? get() = referenceNameElement

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
        @JvmStatic fun doRename(identifier: PsiElement, newName: String) {
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
                META_VAR_IDENTIFIER -> factory.createMetavarIdentifier(newName)
                else -> error("Unsupported identifier type for `$newName` (${identifier.elementType})")
            }
            identifier.replace(newId)
        }
    }

    object Testmarks {
        object DelegatedToMacroExpansion : Testmark()
    }
}
