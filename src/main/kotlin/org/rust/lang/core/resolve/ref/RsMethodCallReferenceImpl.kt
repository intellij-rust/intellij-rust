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
import org.rust.lang.core.types.infer.containsTyOfClass
import org.rust.lang.core.types.inference
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type


class RsMethodCallReferenceImpl(
    element: RsMethodCall
) : RsReferenceBase<RsMethodCall>(element),
    RsReference {

    override val RsMethodCall.referenceAnchor: PsiElement get() = referenceNameElement

    override fun getVariants(): Array<out Any> {
        val lookup = ImplLookup.relativeTo(element)
        val receiver = element.receiver.type
        return collectCompletionVariants {
            processMethodCallExprResolveVariants(lookup, receiver,
                filterCompletionVariantsByVisibility(
                    filterMethodCompletionVariantsByTraitBounds(it, lookup, receiver),
                    element.containingMod
                )
            )
        }
    }

    override fun multiResolve(): List<RsElement> =
        element.inference?.getResolvedMethod(element)?.map { it.element } ?: emptyList()
}

class RsFieldLookupReferenceImpl(
    element: RsFieldLookup
) : RsReferenceBase<RsFieldLookup>(element),
    RsReference {

    override val RsFieldLookup.referenceAnchor: PsiElement get() = referenceNameElement

    override fun getVariants(): Array<out Any> {
        val lookup = ImplLookup.relativeTo(element)
        val receiver = element.receiver.type
        return collectCompletionVariants {
            processDotExprResolveVariants(
                lookup,
                receiver,
                filterCompletionVariantsByVisibility(
                    filterMethodCompletionVariantsByTraitBounds(it, lookup, receiver),
                    element.containingMod
                )
            )
        }
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
): List<MethodResolveVariant> {
    return collectResolveVariants(methodCall.referenceName) {
        processMethodCallExprResolveVariants(lookup, receiverType, it)
    }
}

fun resolveFieldLookupReferenceWithReceiverType(
    lookup: ImplLookup,
    receiverType: Ty,
    expr: RsFieldLookup
): List<FieldResolveVariant> {
    return collectResolveVariants(expr.referenceName) {
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
    override val derefCount: Int
) : DotExprResolveVariant

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
    val source: TraitImplSource
) : DotExprResolveVariant

private fun <T : ScopeEntry> collectResolveVariants(referenceName: String, f: ((T) -> Boolean) -> Unit): List<T> {
    val result = mutableListOf<T>()
    f { e ->
        if (e.name == referenceName) {
            result += e
        }
        false
    }
    return result
}

private fun filterMethodCompletionVariantsByTraitBounds(
    processor: RsResolveProcessor,
    lookup: ImplLookup,
    receiver: Ty
): RsResolveProcessor {
    // Don't filter partially unknown types
    if (receiver.containsTyOfClass(TyUnknown::class.java)) return processor

    val cache = mutableMapOf<RsImplItem, Boolean>()
    return fun(it: ScopeEntry): Boolean {
        // If not a method (actually a field) or a trait method - just process it
        if (it !is MethodResolveVariant || it.source !is TraitImplSource.ExplicitImpl) return processor(it)
        // Filter methods by trait bounds (try to select all obligations for each impl)
        // We're caching evaluation results here because we can often complete methods
        // in the same impl and always have the same receiver type
        val canEvaluate = cache.getOrPut(it.source.value) {
            lookup.ctx.canEvaluateBounds(it.source.value, receiver)
        }
        if (canEvaluate) return processor(it)

        return false
    }
}
