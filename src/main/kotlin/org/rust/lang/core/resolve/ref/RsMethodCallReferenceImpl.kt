/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsFieldLookup
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsMethodCall
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.receiver
import org.rust.lang.core.resolve.*
import org.rust.lang.core.types.inference
import org.rust.lang.core.types.ty.Substitution
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.emptySubstitution
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

    override fun multiResolve(): List<RsElement> =
        element.inference?.getResolvedMethod(element) ?: emptyList()
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

    override fun multiResolve(): List<RsElement> =
        element.inference?.getResolvedField(element) ?: emptyList()

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
): List<MethodCallee> {
    return collectMethodResolveVariants(methodCall.referenceName) {
        processMethodCallExprResolveVariants(lookup, receiverType, it)
    }
}

fun resolveFieldLookupReferenceWithReceiverType(
    lookup: ImplLookup,
    receiverType: Ty,
    expr: RsFieldLookup
): List<RsElement> {
    return collectResolveVariants(expr.referenceName) {
        processFieldExprResolveVariants(lookup, receiverType, false, it)
    }
}

data class MethodCallee(
    override val name: String,
    override val element: RsFunction,
    /**
     * If the method defined in impl, this is the impl. If the method inherited from
     * trait definition, this is the impl of the actual trait for the receiver type
     */
    val impl: RsImplItem?,
    /** The receiver type after possible derefs performed */
    val selfTy: Ty,
    /** The number of `*` dereferences should be performed on receiver to match `selfTy` */
    val derefCount: Int
) : ScopeEntry {
    /** Legacy subst. Do not really used */
    override val subst: Substitution
        get() = emptySubstitution
}

private fun collectMethodResolveVariants(referenceName: String, f: (RsMethodResolveProcessor) -> Unit): List<MethodCallee> {
    val result = mutableListOf<MethodCallee>()
    f { e ->
        if (e.name == referenceName) {
            result += e
        }
        false
    }
    return result
}
