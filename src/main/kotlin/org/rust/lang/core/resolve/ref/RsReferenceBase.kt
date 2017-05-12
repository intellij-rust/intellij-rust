package org.rust.lang.core.resolve.ref

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult
import com.intellij.psi.impl.source.resolve.ResolveCache
import org.rust.lang.core.psi.RsElementTypes.IDENTIFIER
import org.rust.lang.core.psi.RsElementTypes.QUOTE_IDENTIFIER
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.psi.ext.RsReferenceElement
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.core.types.BoundElement

abstract class RsReferenceBase<T : RsReferenceElement>(
    element: T
) : PsiPolyVariantReferenceBase<T>(element),
    RsReference {

    abstract protected fun resolveInner(): List<BoundElement<RsCompositeElement>>

    override fun resolve(): RsCompositeElement? = super.resolve() as? RsCompositeElement

    override fun advancedResolve(): BoundElement<RsCompositeElement>? =
        advancedCachedMultiResolve().firstOrNull()

    final override fun multiResolve(incompleteCode: Boolean): Array<out ResolveResult> =
        advancedCachedMultiResolve().toTypedArray()

    final override fun multiResolve(): List<RsNamedElement> =
        advancedCachedMultiResolve().mapNotNull { it.element as? RsNamedElement }

    private fun advancedCachedMultiResolve(): List<BoundElement<RsCompositeElement>> {
        return ResolveCache.getInstance(element.project)
            .resolveWithCaching(this, Resolver,
                /* needToPreventRecursion = */ true,
                /* incompleteCode = */ false).orEmpty()
    }

    abstract val T.referenceAnchor: PsiElement

    final override fun getRangeInElement(): TextRange = super.getRangeInElement()

    final override fun calculateDefaultRangeInElement(): TextRange {
        val anchor = element.referenceAnchor
        check(anchor.parent === element)
        return TextRange.from(anchor.startOffsetInParent, anchor.textLength)
    }

    override fun handleElementRename(newName: String): PsiElement {
        doRename(element.referenceNameElement, newName)
        return element
    }

    override fun equals(other: Any?): Boolean = other is RsReferenceBase<*> && element === other.element

    override fun hashCode(): Int = element.hashCode()

    private object Resolver : ResolveCache.AbstractResolver<RsReferenceBase<*>, List<BoundElement<RsCompositeElement>>> {
        override fun resolve(ref: RsReferenceBase<*>, incompleteCode: Boolean): List<BoundElement<RsCompositeElement>> {
            return ref.resolveInner()
        }
    }

    companion object {
        @JvmStatic protected fun doRename(identifier: PsiElement, newName: String) {
            val factory = RsPsiFactory(identifier.project)
            val newId = when (identifier.elementType) {
                IDENTIFIER -> factory.createIdentifier(newName.replace(".rs", ""))
                QUOTE_IDENTIFIER -> factory.createQuoteIdentifier(newName)
                else -> error("Unsupported identifier type for `$newName` (${identifier.elementType})")
            }
            identifier.replace(newId)
        }
    }
}
