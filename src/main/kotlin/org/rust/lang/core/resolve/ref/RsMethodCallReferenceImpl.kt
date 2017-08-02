/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsFieldLookup
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsMethodCall
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.psi.ext.parentDotExpr
import org.rust.lang.core.psi.ext.receiver
import org.rust.lang.core.resolve.*
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyTypeParameter
import org.rust.lang.core.types.type


class RsMethodCallReferenceImpl(
    element: RsMethodCall
) : RsReferenceBase<RsMethodCall>(element),
    RsReference {

    override val RsMethodCall.referenceAnchor: PsiElement get() = referenceNameElement

    override fun getVariants(): Array<out Any> {
        val lookup = ImplLookup.relativeTo(element)
        return collectCompletionVariants { processMethodCallExprResolveVariants(lookup, element.receiver.type, it) }
    }

    override fun resolveInner(): List<BoundElement<RsCompositeElement>> {
        val receiverType = element.parentDotExpr.expr.type
        val lookup = ImplLookup.relativeTo(element)
        return resolveMethodCallReferenceWithReceiverType(lookup, receiverType, element)
    }
}

class RsFieldLookupReferenceImpl(
    element: RsFieldLookup
) : RsReferenceBase<RsFieldLookup>(element),
    RsReference {

    override val RsFieldLookup.referenceAnchor: PsiElement get() = referenceNameElement

    override fun getVariants(): Array<out Any> {
        val lookup = ImplLookup.relativeTo(element)
        return collectCompletionVariants { processFieldExprResolveVariants(lookup, element.receiver.type, true, it) }
    }

    override fun resolveInner(): List<BoundElement<RsCompositeElement>> {
        val receiverType = element.parentDotExpr.expr.type
        val lookup = ImplLookup.relativeTo(element)
        return resolveFieldLookupReferenceWithReceiverType(lookup, receiverType, element)
    }

    override fun handleElementRename(newName: String): PsiElement {
        val ident = element.identifier
        if (ident != null) doRename(ident, newName)
        return element
    }
}

fun resolveMethodCallReferenceWithReceiverType(
    lookup: ImplLookup,
    receiverType: Ty,
    methodCall: RsMethodCall
): List<BoundElement<RsCompositeElement>> {
    val result = collectResolveVariants(methodCall.referenceName) {
        processMethodCallExprResolveVariants(lookup, receiverType, it)
    }
    val typeArguments = methodCall.typeArgumentList?.typeReferenceList.orEmpty().map { it.type }
    if (typeArguments.isEmpty()) return result

    return result.map { boundElement ->
        val method = boundElement.element
        if (method is RsFunction) {
            val parameters = method.typeParameterList?.typeParameterList.orEmpty().map { TyTypeParameter.named(it) }
            BoundElement(
                method,
                boundElement.subst + parameters.zip(typeArguments).toMap()
            )
        } else {
            boundElement
        }
    }
}

fun resolveFieldLookupReferenceWithReceiverType(
    lookup: ImplLookup,
    receiverType: Ty,
    expr: RsFieldLookup
): List<BoundElement<RsCompositeElement>> {
    return collectResolveVariants(expr.referenceName) {
        processFieldExprResolveVariants(lookup, receiverType, false, it)
    }
}


