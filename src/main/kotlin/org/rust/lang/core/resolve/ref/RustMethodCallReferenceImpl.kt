package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.completion.RustCompletionEngine
import org.rust.lang.core.psi.RsMethodCallExpr
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.resolve.RustResolveEngine

class RustMethodCallReferenceImpl(
    element: RsMethodCallExpr
) : RustReferenceBase<RsMethodCallExpr>(element),
    RustReference {

    override val RsMethodCallExpr.referenceAnchor: PsiElement get() = referenceNameElement

    override fun getVariants(): Array<out Any> = RustCompletionEngine.completeMethod(element)

    override fun resolveInner(): List<RustNamedElement> =
        listOfNotNull(RustResolveEngine.resolveMethodCallExpr(element))

}
