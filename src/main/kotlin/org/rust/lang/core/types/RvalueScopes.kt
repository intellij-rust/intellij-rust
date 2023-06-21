/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types

import org.rust.lang.core.psi.RsDotExpr
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsIndexExpr
import org.rust.lang.core.psi.RsUnaryExpr
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.UnaryOperator
import org.rust.lang.core.psi.ext.containerExpr
import org.rust.lang.core.psi.ext.operatorType
import org.rust.lang.core.types.regions.Scope
import org.rust.lang.core.types.regions.ScopeTree

/**
 * [RvalueScopes] is a mapping from sub-expressions to _extended_ lifetime as determined by rules laid out in
 * `rustc_hir_analysis::check::rvalue_scopes`.
 */
class RvalueScopes {
    private val map: MutableMap<RsElement, Scope?> = mutableMapOf()

    /** Returns the scope when the temp created by [expr] will be cleaned up. */
    fun temporaryScope(regionScopeTree: ScopeTree, expr: RsExpr): Scope? {
        // Check for a designated rvalue scope.
        val scope = map[expr]
        if (scope != null) return scope

        // Otherwise, locate the innermost terminating scope if there's one.
        // Static items, for instance, won't have an enclosing scope, hence no scope will be returned.
        var id: Scope = Scope.Node(expr)
        for (p in generateSequence { regionScopeTree.getEnclosingScope(id) }) {
            if (p is Scope.Destruction) {
                return p
            } else {
                id = p
            }
        }

        return null
    }

    /** Make an association between a sub-expression and an extended lifetime. */
    fun recordRvalueScope(element: RsElement, lifetime: Scope?) {
        if (lifetime != null) {
            check(element != lifetime.element)
        }
        map[element] = lifetime
    }
}

fun resolveRvalueScopes(scopeTree: ScopeTree): RvalueScopes {
    val rvalueScopes = RvalueScopes()
    for ((expr, candidate) in scopeTree.rvalueCandidates) {
        recordRvalueScopeRec(rvalueScopes, expr, candidate.lifetime)
    }
    return rvalueScopes
}

/**
 * Applied to an expression [expr] if [expr] -- or something owned or partially owned by [expr] -- is going to be
 * indirectly referenced by a variable in a let statement. In that case, the "temporary lifetime" or [expr] is extended
 * to be the block enclosing the `let` statement.
 *
 * More formally, if [expr] matches the grammar `ET`, record the rvalue scope of the matching `<rvalue>` as `block`:
 *
 * ```text
 *     ET = *ET
 *        | ET[...]
 *        | ET.f
 *        | (ET)
 *        | <rvalue>
 * ```
 *
 * Note: ET is intended to match "rvalues or places based on rvalues".
 */
private fun recordRvalueScopeRec(rvalueScopes: RvalueScopes, expr: RsExpr, lifetime: Scope?) {
    var exprVar = expr
    while (true) {
        // Note: give all the expressions matching `ET` with the extended temporary lifetime, not just the innermost
        // rvalue, because in codegen if we must compile e.g., `*rvalue()` into a temporary, we request the temporary
        // scope of the outer expression.

        rvalueScopes.recordRvalueScope(exprVar, lifetime)

        exprVar = when {
            exprVar is RsIndexExpr -> exprVar.containerExpr
            exprVar is RsUnaryExpr && exprVar.operatorType in REF_AND_DEREF_OPS -> exprVar.expr ?: TODO()
            exprVar is RsDotExpr && exprVar.fieldLookup != null -> exprVar.expr
            // TODO: other cases
            else -> return
        }
    }
}

private val REF_AND_DEREF_OPS: Set<UnaryOperator> = setOf(
    UnaryOperator.REF,
    UnaryOperator.REF_MUT,
    UnaryOperator.DEREF,
)
