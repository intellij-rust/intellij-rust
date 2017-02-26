package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.completion.CompletionEngine
import org.rust.lang.core.psi.RsMethodCallExpr
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.resolve.ResolveEngine

class RsMethodCallReferenceImpl(
    element: RsMethodCallExpr
) : RsReferenceBase<RsMethodCallExpr>(element),
    RsReference {

    override val RsMethodCallExpr.referenceAnchor: PsiElement get() = referenceNameElement

    override fun getVariants(): Array<out Any> = CompletionEngine.completeMethod(element)

    override fun resolveInner(): List<RsNamedElement> =
        listOfNotNull(ResolveEngine.resolveMethodCallExpr(element))

}
