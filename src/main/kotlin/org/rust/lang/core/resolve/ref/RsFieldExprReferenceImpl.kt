package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsFieldExpr
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.resolve.*

class RsFieldExprReferenceImpl(
    fieldExpr: RsFieldExpr
) : RsReferenceBase<RsFieldExpr>(fieldExpr),
    RsReference {

    override val RsFieldExpr.referenceAnchor: PsiElement get() = referenceNameElement

    override fun getVariants(): Array<out Any> =
        collectCompletionVariants {
            processFieldExprResolveVariants(element, true, it)
        }

    override fun resolveInner(): List<RsCompositeElement> =
        collectResolveVariants(element.referenceName) {
            processFieldExprResolveVariants(element, false, it)
        }

    override fun handleElementRename(newName: String): PsiElement {
        element.fieldId.identifier?.let { doRename(it, newName) }
        return element
    }
}
