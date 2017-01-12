package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.completion.RustCompletionEngine
import org.rust.lang.core.psi.RsFieldExpr
import org.rust.lang.core.psi.RsCompositeElement
import org.rust.lang.core.resolve.RustResolveEngine

class RustFieldExprReferenceImpl(
    fieldExpr: RsFieldExpr
) : RustReferenceBase<RsFieldExpr>(fieldExpr),
    RustReference {

    override val RsFieldExpr.referenceAnchor: PsiElement get() = referenceNameElement

    override fun getVariants(): Array<out Any> = RustCompletionEngine.completeFieldOrMethod(element)

    override fun resolveInner(): List<RsCompositeElement> =
        RustResolveEngine.resolveFieldExpr(element)

    override fun handleElementRename(newName: String): PsiElement {
        element.fieldId.identifier?.let { doRename(it, newName) }
        return element
    }
}
