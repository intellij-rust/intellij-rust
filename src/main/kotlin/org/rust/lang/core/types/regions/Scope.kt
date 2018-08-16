/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.regions

import com.intellij.openapi.util.TextRange
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

fun getRegionScopeTree(contextOwner: RsInferenceContextOwner): ScopeTree {
    val visitor = RegionResolutionVisitor(contextOwner)
    contextOwner.accept(visitor)
    return visitor.scopeTree
}

/** Represents a statically-describable scope that can be used to bound the lifetime/region for values. */
sealed class Scope {
    abstract val element: RsElement

    /**
     * Any AST node that has any scope at all has the scope.
     * Other variants represent special cases not immediately derivable from the abstract syntax tree structure.
     */
    data class Node(override val element: RsElement) : Scope()

    /** Scope of the call-site for a function or closure (outlives the arguments as well as the body). */
    data class CallSite(override val element: RsElement) : Scope()

    /**
     * Represents the scope of user code running immediately after the initializer expression for the indexed stmt,
     * until the end of the block.
     */
    data class Remainder(override val element: RsLetDecl) : Scope()
}

/**
 * Returns the span of this Scope.
 * Note that in general the returned span may not correspond to the span of any node in the AST.
 */
val Scope.span: TextRange
    get() {
        val span = element.textRange
        if (this is Scope.Remainder) {
            // Want span for scope starting after the let binding and ending at end of block;
            // reuse span of block and shift `startOffset` forward to end of indexed stmt.
            val blockSpan = element.parent.textRange

            // To avoid issues with macro-generated spans, the span of the stmt must be nested.
            if (blockSpan.startOffset <= span.startOffset && span.endOffset <= blockSpan.endOffset) {
                return TextRange(span.endOffset, blockSpan.endOffset)
            }
        }
        return span
    }

data class ScopeInfo(val scope: Scope, val depth: Int)

/** The region scope tree encodes information about region relationships. */
data class ScopeTree(
    /** If not empty, this body is the root of this region hierarchy. */
    private val rootBody: RsElement? = null,

    /**
     * The parent of the root body owner, if the latter is an associated const or method, as impls/traits can also
     * have lifetime parameters free in this body.
     */
    private val rootParent: RsTraitOrImpl? = null
) {

    /** Maps from a scope to the enclosing scope. */
    private val parentMap: MutableMap<Scope, ScopeInfo> = hashMapOf()

    /** Maps from a variable or binding to the block in which that variable is declared. */
    private val variableMap: MutableMap<RsPatBinding, Scope> = hashMapOf()

    fun getVariableScope(variable: RsPatBinding): Scope? = variableMap[variable]

    fun recordVariableScope(variable: RsPatBinding, scope: Scope) {
        check(variable != scope.element)
        variableMap[variable] = scope
    }

    fun recordScopeParent(childScope: Scope, parentInfo: ScopeInfo) {
        check(!parentMap.contains(childScope))
        parentMap[childScope] = parentInfo
    }

    /** Returns true if [sub] is equal to or is lexically nested inside [sup] and false otherwise. */
    fun isSubScopeOf(sub: Scope, sup: Scope): Boolean {
        var currentScope = sub
        while (currentScope != sup) {
            currentScope = getEnclosingScope(currentScope) ?: return false
        }
        return true
    }

    fun intersects(scope1: Scope, scope2: Scope): Boolean =
        isSubScopeOf(scope1, scope2) || isSubScopeOf(scope2, scope1)

    /** Returns the narrowest scope that encloses [scope], if any. */
    private fun getEnclosingScope(scope: Scope): Scope? = parentMap[scope]?.scope

    /**
     * Finds the lowest common ancestor of two scopes.
     * That is, finds the smallest scope which is greater than or equal to both [scope1] and [scope2].
     */
    fun getLowestCommonAncestor(scope1: Scope, scope2: Scope): Scope {
        if (scope1 == scope2) return scope1

        var currentScope1 = scope1
        var currentScope2 = scope2

        // Get the depth of each scope's parent. If either scope has no parent, it must be the root, which means we can
        // stop immediately because the root must be the lowest common ancestor.
        val (parentScope1, parentDepth1) = parentMap[currentScope1] ?: return currentScope1
        val (parentScope2, parentDepth2) = parentMap[currentScope2] ?: return currentScope2

        when {
            parentDepth1 > parentDepth2 -> {
                // `parent1` is lower than `parent2`. Move `parent1` up until it's at the same depth  as `parent2`.
                currentScope1 = parentScope1
                repeat(parentDepth1 - parentDepth2 - 1) {
                    currentScope1 = checkNotNull(parentMap[currentScope1]).scope
                }
            }
            parentDepth2 > parentDepth1 -> {
                // `parent2` is lower than `parent1`.
                currentScope2 = parentScope2
                repeat(parentDepth2 - parentDepth1 - 1) {
                    currentScope2 = checkNotNull(parentMap[currentScope2]).scope
                }
            }
            else -> {
                // Both scopes are at the same depth, and we know they're not equal because that case was tested for at
                // the top of this function. So we can trivially move them both up one level now.
                check(parentDepth1 != 0)
                currentScope1 = parentScope1
                currentScope2 = parentScope2
            }
        }

        // Now both scopes are at the same level. We move upwards in lockstep  until they match.
        while (currentScope1 != currentScope2) {
            currentScope1 = checkNotNull(parentMap[currentScope1]).scope
            currentScope2 = checkNotNull(parentMap[currentScope2]).scope
        }

        return currentScope1
    }

    /**
     * Assuming that the provided region was defined within this [ScopeTree].
     * @returns the outermost [Scope] that the region outlives.
     */
    fun getEarlyFreeScope(region: ReEarlyBound): Scope {
        val parameterOwner = region.parameter.ancestorStrict<RsGenericDeclaration>()
        val body = if (parameterOwner is RsFunction) {
            parameterOwner.block
        } else {
            // The lifetime was defined on node that doesn't own a body, which in practice can only mean a trait or an
            // impl, that is the parent of a method, and that is enforced below.
            check(parameterOwner == rootParent)

            // The trait/impl lifetime is in scope for the method's body.
            rootBody
        }
        return Scope.CallSite(checkNotNull(body))
    }

    /**
     * Assuming that the provided region was defined within this [ScopeTree].
     * @returns the outermost [Scope] that the region outlives.
     */
    fun getFreeScope(region: ReFree): Scope {
        val parameterOwner = if (region.bound is BoundRegion.Named) {
            region.bound.contextOwner.ancestorStrict<RsInferenceContextOwner>()
        } else {
            region.contextOwner
        }
        check(parameterOwner === region.contextOwner)

        val body = parameterOwner?.body
        return Scope.CallSite(checkNotNull(body))
    }
}

private data class Context(
    /** The root of the current region tree. This is typically the innermost fn body. */
    var root: RsBlock? = null,

    /** The scope that contains any new variables declared, plus its depth in the scope tree. */
    var variableParent: ScopeInfo? = null,

    /** Region parent of expressions, etc., plus its depth in the scope tree. */
    var parent: ScopeInfo? = null
)

private class RegionResolutionVisitor(contextOwner: RsInferenceContextOwner) : RsVisitor() {
    var ctx: Context = Context()

    /** Generated scope tree. */
    val scopeTree: ScopeTree = run {
        val rootBody = contextOwner.body
        val rootParent = contextOwner.ancestorStrict<RsItemElement>() as? RsTraitOrImpl
        ScopeTree(rootBody, rootParent)
    }

    override fun visitFunction(fn: RsFunction) {
        val body = fn.block ?: return
        val outerCtx = ctx.copy()
        ctx.root = body

        enterScope(Scope.CallSite(body))

        // The arguments are parented to the fn.
        ctx.variableParent = ctx.parent
        ctx.parent = null
        for (parameter in fn.valueParameters) {
            parameter.pat?.let { visitPat(it) }
        }

        // The body of the every fn is a root scope.
        ctx.parent = ctx.variableParent
        visitBlock(body)

        // Restore context we had at the start.
        ctx = outerCtx
    }

    override fun visitConstant(constant: RsConstant) {
        val outerCtx = ctx.copy()
        ctx.root = (constant.expr as? RsBlockExpr)?.block
        constant.expr?.let { visitExpr(it) }
        ctx = outerCtx
    }

    override fun visitBlock(block: RsBlock) {
        val prevCtx = ctx.copy()

        enterScope(Scope.Node(block))
        ctx.variableParent = ctx.parent

        for (stmt in block.stmtList) {
            if (stmt is RsLetDecl) {
                // Each LetDecl introduces a subscope for bindings introduced by the declaration.
                // Each subscope in a block has the previous subscope in the block as a parent, except for the first
                // such subscope, which has the block itself as a parent.
                enterScope(Scope.Remainder(stmt))
                ctx.variableParent = ctx.parent
            }
            stmt.accept(this)
        }
        block.expr?.let { visitExpr(it) }

        ctx = prevCtx
    }

    override fun visitStmt(stmt: RsStmt) {
        val prevParent = ctx.parent?.copy()
        enterScope(Scope.Node(stmt))
        super.visitStmt(stmt)
        ctx.parent = prevParent
    }

    override fun visitExpr(expr: RsExpr) {
        val prevCtx = ctx.copy()
        enterScope(Scope.Node(expr))
        if (expr is RsMatchExpr) {
            ctx.variableParent = ctx.parent
        }
        super.visitExpr(expr)
        ctx = prevCtx
    }

    override fun visitPat(pat: RsPat) {
        recordChildScope(Scope.Node(pat))
        super.visitPat(pat)
    }

    override fun visitPatBinding(binding: RsPatBinding) {
        recordVariableScope(binding)
    }

    override fun visitElement(element: RsElement) {
        if (element is RsItemElement) return
        element.acceptChildren(this)
    }

    /**
     * Records the current parent (if any) as the parent of [childScope].
     * Returns the depth of [childScope].
     */
    private fun recordChildScope(childScope: Scope): Int {
        val parentInfo = ctx.parent ?: return 1
        scopeTree.recordScopeParent(childScope, parentInfo)
        return parentInfo.let { (_, depth) -> depth + 1 }
    }

    /**
     * Records the current parent as the parent of [childScope], and sets [childScope] as the new current parent.
     * If [childScope] has no parent, it must be the root node, and so has a depth of 1.
     * Otherwise, its depth is one more than its parent's.
     */
    private fun enterScope(childScope: Scope) {
        val childDepth = recordChildScope(childScope)
        ctx.parent = ScopeInfo(childScope, childDepth)
    }

    /** Records the scope of a local variable as `ctx.variableParent`. */
    private fun recordVariableScope(variable: RsPatBinding) {
        ctx.variableParent?.let { (parentScope, _) ->
            scopeTree.recordVariableScope(variable, parentScope)
        }
    }
}
