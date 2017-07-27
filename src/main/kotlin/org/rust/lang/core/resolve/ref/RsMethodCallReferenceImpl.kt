/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsMethodCallExpr
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.resolve.*
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyTypeParameter
import org.rust.lang.core.types.type

class RsMethodCallReferenceImpl(
    element: RsMethodCallExpr
) : RsReferenceBase<RsMethodCallExpr>(element),
    RsReference {

    override val RsMethodCallExpr.referenceAnchor: PsiElement get() = referenceNameElement

    override fun getVariants(): Array<out Any> =
        collectCompletionVariants { processMethodCallExprResolveVariants(ImplLookup.relativeTo(element), element.expr.type, it) }

    override fun resolveInner(): List<BoundElement<RsCompositeElement>> =
        resolveMethodCallReferenceWithReceiverType(ImplLookup.relativeTo(element), element.expr.type, element)
}

fun resolveMethodCallReferenceWithReceiverType(
    lookup: ImplLookup,
    receiverType: Ty,
    element: RsMethodCallExpr
):List<BoundElement<RsCompositeElement>> {
    val result = collectResolveVariants(element.referenceName) {
        processMethodCallExprResolveVariants(lookup, receiverType, it)
    }
    val typeArguments = element.typeArgumentList?.typeReferenceList.orEmpty().map { it.type }
    if (typeArguments.isEmpty()) return result

    return result.map { boundElement ->
        val method = boundElement.element
        if (method is RsFunction) {
            val parameters = method.typeParameterList?.typeParameterList.orEmpty().map { TyTypeParameter(it) }
            BoundElement(
                method,
                boundElement.subst + parameters.zip(typeArguments).toMap()
            )
        } else {
            boundElement
        }
    }
}
