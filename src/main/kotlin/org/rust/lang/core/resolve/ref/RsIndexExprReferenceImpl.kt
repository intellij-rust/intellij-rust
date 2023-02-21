/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.openapi.util.TextRange
import com.intellij.psi.MultiRangeReference
import org.rust.lang.core.psi.RsBinaryExpr
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsIndexExpr
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.TraitRef
import org.rust.lang.core.types.implLookupAndKnownItems
import org.rust.lang.core.types.type

class RsIndexExprReferenceImpl(element: RsIndexExpr) : RsReferenceCached<RsIndexExpr>(element), MultiRangeReference {
    override val cacheDependency: ResolveCacheDependency get() = ResolveCacheDependency.LOCAL_AND_RUST_STRUCTURE

    override fun multiResolveUncached(): List<RsElement> {
        val parent = element.parent

        // Assume that IndexMut will be used if the indexed expression is on the left side of some assignment
        val isMaybeMutableContext = parent is RsBinaryExpr &&
            parent.operatorType is AssignmentOp &&
            parent.left == element

        val indexFn = findIndexFunction(element, isMaybeMutableContext)
            ?.takeIf { it.existsAfterExpansion }
        return listOfNotNull(indexFn)
    }

    override fun getRanges(): List<TextRange> = listOf(
        element.lbrack.textRangeInParent,
        element.rbrack.textRangeInParent
    )
}

/**
 * Tries to resolve a function that implements either `Index` or `IndexMut` for the given index expression.
 * If `preferMutable` is true, `IndexMut` will be attempted for resolution first.
 */
fun findIndexFunction(element: RsIndexExpr, preferMutable: Boolean): RsFunction? {
    val container = element.containerExpr
    val index = element.indexExpr ?: return null

    val (lookup, items) = element.implLookupAndKnownItems

    val candidates = mutableListOf(
        items.Index to "index",
        items.IndexMut to "index_mut"
    )
    if (preferMutable) {
        candidates.reverse()
    }

    return candidates.map { (trait, functionName) ->
        if (trait == null) return@map null
        val impl = lookup.select(TraitRef(container.type, trait.withSubst(index.type))).ok()?.impl ?: return@map null
        impl.expandedMembers
            .functions
            .find { it.name == functionName }
    }.firstOrNull()
}
