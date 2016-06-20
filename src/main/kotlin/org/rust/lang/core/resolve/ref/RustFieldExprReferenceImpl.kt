package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.completion.RustCompletionEngine
import org.rust.lang.core.psi.RustFieldExprElement
import org.rust.lang.core.resolve.RustResolveEngine

class RustFieldExprReferenceImpl(
    fieldExpr: RustFieldExprElement
) : RustReferenceBase<RustFieldExprElement>(fieldExpr)
  , RustReference {

    override val RustFieldExprElement.referenceAnchor: PsiElement get() = fieldId

    override fun getVariants(): Array<out Any> = RustCompletionEngine.completeFieldOrMethod(element)

    override fun resolveVerbose(): RustResolveEngine.ResolveResult =
        RustResolveEngine.resolveFieldExpr(element)
}
