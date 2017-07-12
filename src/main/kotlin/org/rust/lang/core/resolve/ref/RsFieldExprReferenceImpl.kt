/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsFieldExpr
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.resolve.collectCompletionVariants
import org.rust.lang.core.resolve.collectResolveVariants
import org.rust.lang.core.resolve.processFieldExprResolveVariants
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.type

class RsFieldExprReferenceImpl(
    fieldExpr: RsFieldExpr
) : RsReferenceBase<RsFieldExpr>(fieldExpr),
    RsReference {

    override val RsFieldExpr.referenceAnchor: PsiElement get() = referenceNameElement

    override fun getVariants(): Array<out Any> =
        collectCompletionVariants {
            processFieldExprResolveVariants(element.project, element.expr.type, true, it)
        }

    override fun resolveInner(): List<BoundElement<RsCompositeElement>> =
        resolveFieldExprReferenceWithReceiverType(element.expr.type, element)

    override fun handleElementRename(newName: String): PsiElement {
        element.fieldId.identifier?.let { doRename(it, newName) }
        return element
    }
}

fun resolveFieldExprReferenceWithReceiverType(
    receiverType: Ty,
    expr: RsFieldExpr
): List<BoundElement<RsCompositeElement>> {
    return collectResolveVariants(expr.referenceName) {
        processFieldExprResolveVariants(expr.project, receiverType, false, it)
    }
}
