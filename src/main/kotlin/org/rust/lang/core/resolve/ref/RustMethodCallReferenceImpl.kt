package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustMethodCallExprElement
import org.rust.lang.core.resolve.RustResolveEngine

class RustMethodCallReferenceImpl(
    element: RustMethodCallExprElement
) : RustReferenceBase<RustMethodCallExprElement>(element)
  , RustReference {

    override val RustMethodCallExprElement.referenceAnchor: PsiElement get() = identifier!!

    override fun getVariants(): Array<out Any> = emptyArray()

    override fun resolveVerbose(): RustResolveEngine.ResolveResult =
        RustResolveEngine.resolveMethodCallExpr(element)

}
