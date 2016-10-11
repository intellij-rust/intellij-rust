package org.rust.lang.core.resolve.ref

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult
import com.intellij.psi.impl.source.resolve.ResolveCache
import org.rust.lang.core.psi.RustElementFactory
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustReferenceElement
import org.rust.lang.core.psi.RustTokenElementTypes
import org.rust.lang.core.psi.util.elementType
import org.rust.lang.core.psi.util.parentRelativeRange
import org.rust.lang.core.resolve.RustResolveEngine

abstract class RustReferenceBase<T : RustReferenceElement>(
    element: T
) : PsiPolyVariantReferenceBase<T>(element),
    RustReference {

    abstract fun resolveVerbose(): RustResolveEngine.ResolveResult

    final override fun multiResolve(incompleteCode: Boolean): Array<out ResolveResult> =
        ResolveCache.getInstance(element.project)
            .resolveWithCaching(this, { r, incomplete ->
                r.resolveVerbose().element?.let {
                    arrayOf(PsiElementResolveResult(it))
                } ?: ResolveResult.EMPTY_ARRAY
            },
                /* needToPreventRecursion = */ false,
                /* incompleteCode = */ false)

    final override fun multiResolve(): List<RustNamedElement> =
        multiResolve(false).asList().mapNotNull { it.element as? RustNamedElement }

    abstract val T.referenceAnchor: PsiElement

    final override fun getRangeInElement(): TextRange = super.getRangeInElement()

    final override fun calculateDefaultRangeInElement(): TextRange {
        check(element.referenceAnchor.parent === element)
        return element.referenceAnchor.parentRelativeRange
    }

    override fun handleElementRename(newName: String): PsiElement {
        doRename(element.referenceNameElement, newName)
        return element
    }

    override fun equals(other: Any?): Boolean = other is RustReferenceBase<*> && element === other.element

    override fun hashCode(): Int = element.hashCode()

    companion object {
        @JvmStatic protected fun doRename(identifier: PsiElement, newName: String) {
            check(identifier.elementType == RustTokenElementTypes.IDENTIFIER)
            identifier.replace(RustElementFactory.createIdentifier(identifier.project, newName.replace(".rs", "")))
        }
    }
}
