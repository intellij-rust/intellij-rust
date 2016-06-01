package org.rust.lang.core.resolve.ref

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import org.rust.lang.core.psi.RustCompositeElement
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.util.parentRelativeRange
import org.rust.lang.core.resolve.RustResolveEngine

abstract class RustReferenceBase<T : RustCompositeElement>(
    element: T,
    refAnchorTextRange: TextRange
) : PsiReferenceBase<T>(element, refAnchorTextRange)
  , RustReference {

    constructor(element: T, refAnchor: PsiElement) : this(element, refAnchor.parentRelativeRange) {
        check(refAnchor.parent == element)
    }

    abstract fun resolveVerbose(): RustResolveEngine.ResolveResult

    final override fun resolve(): RustNamedElement? =
        resolveVerbose().let {
            when (it) {
                is RustResolveEngine.ResolveResult.Resolved -> it.element
                else -> null
            }
        }

    // enforce not nullability
    final override fun getRangeInElement(): TextRange = super.getRangeInElement()
}
