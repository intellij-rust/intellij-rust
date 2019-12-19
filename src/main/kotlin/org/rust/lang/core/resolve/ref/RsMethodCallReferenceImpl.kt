/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsFieldLookup
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsMethodCall
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsFieldDecl
import org.rust.lang.core.psi.ext.isMethod
import org.rust.lang.core.resolve.*
import org.rust.lang.core.types.inference
import org.rust.lang.core.types.ty.Ty


class RsMethodCallReferenceImpl(
    element: RsMethodCall
) : RsReferenceBase<RsMethodCall>(element),
    RsReference {

    override val RsMethodCall.referenceAnchor: PsiElement get() = referenceNameElement

    override fun multiResolve(): List<RsElement> =
        element.inference?.getResolvedMethod(element)?.map { it.element } ?: emptyList()

    override fun isReferenceTo(element: PsiElement): Boolean =
        element is RsFunction && element.isMethod && super.isReferenceTo(element)
}

class RsFieldLookupReferenceImpl(
    element: RsFieldLookup
) : RsReferenceBase<RsFieldLookup>(element),
    RsReference {

    override val RsFieldLookup.referenceAnchor: PsiElement get() = referenceNameElement

    override fun multiResolve(): List<RsElement> =
        element.inference?.getResolvedField(element) ?: emptyList()

    override fun handleElementRename(newName: String): PsiElement {
        val ident = element.identifier
        if (ident != null) doRename(ident, newName)
        return element
    }

    override fun isReferenceTo(element: PsiElement): Boolean =
        element is RsFieldDecl && super.isReferenceTo(element)
}

fun resolveMethodCallReferenceWithReceiverType(
    lookup: ImplLookup,
    receiverType: Ty,
    methodCall: RsMethodCall
): List<MethodResolveVariant> {
    return collectResolveVariantsAsScopeEntries(methodCall.referenceName) {
        processMethodCallExprResolveVariants(lookup, receiverType, it)
    }
}

fun resolveFieldLookupReferenceWithReceiverType(
    lookup: ImplLookup,
    receiverType: Ty,
    expr: RsFieldLookup
): List<FieldResolveVariant> {
    return collectResolveVariantsAsScopeEntries(expr.referenceName) {
        processFieldExprResolveVariants(lookup, receiverType, it)
    }
}

interface DotExprResolveVariant : ScopeEntry {
    /** The receiver type after possible derefs performed */
    val selfTy: Ty
    /** The number of `*` dereferences should be performed on receiver to match `selfTy` */
    val derefCount: Int
}

data class FieldResolveVariant(
    override val name: String,
    override val element: RsElement,
    override val selfTy: Ty,
    val derefSequence: List<Ty>
) : DotExprResolveVariant {
    override val derefCount: Int get() = derefSequence.size
}

data class MethodResolveVariant(
    override val name: String,
    override val element: RsFunction,
    override val selfTy: Ty,
    override val derefCount: Int,
    /**
     * If the method defined in impl, this contains the impl. If the method inherited from
     * trait definition, this contains the impl of the actual trait for the receiver type.
     * Otherwise it's just a trait the method defined in
     */
    override val source: TraitImplSource
) : DotExprResolveVariant, AssocItemScopeEntryBase<RsFunction>
