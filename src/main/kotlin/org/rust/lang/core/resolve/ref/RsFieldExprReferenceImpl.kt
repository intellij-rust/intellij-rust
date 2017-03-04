package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.completion.CompletionEngine
import org.rust.lang.core.psi.RsFieldExpr
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.resolve.ResolveEngine

class RsFieldExprReferenceImpl(
    fieldExpr: RsFieldExpr
) : RsReferenceBase<RsFieldExpr>(fieldExpr),
    RsReference {

    override val RsFieldExpr.referenceAnchor: PsiElement get() = referenceNameElement

    override fun getVariants(): Array<out Any> = CompletionEngine.completeFieldOrMethod(element)

    override fun resolveInner(): List<RsCompositeElement> =
        ResolveEngine.resolveFieldExpr(element)

    override fun handleElementRename(newName: String): PsiElement {
        element.fieldId.identifier?.let { doRename(it, newName) }
        return element
    }
}
