package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.completion.RustCompletionEngine
import org.rust.lang.core.psi.RustMethodCallExprElement
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.resolve.RustResolveEngine

class RustMethodCallReferenceImpl(
    element: RustMethodCallExprElement
) : RustReferenceBase<RustMethodCallExprElement>(element),
    RustReference {

    override val RustMethodCallExprElement.referenceAnchor: PsiElement get() = referenceNameElement

    override fun getVariants(): Array<out Any> = RustCompletionEngine.completeMethod(element)

    override fun resolveInner(): List<RustNamedElement> =
        listOfNotNull(RustResolveEngine.resolveMethodCallExpr(element))

}
