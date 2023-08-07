/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.regions

import com.intellij.openapi.util.TextRange
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.regions.Scope.*

/**
 * Represents a statically-describable scope that can be used to bound the lifetime/region for values.
 *
 * [Node]: Any AST node that has any scope at all has the [Node] scope.
 * Other variants represent special cases not immediately derivable from the abstract syntax tree structure.
 *
 * [Destruction] represents the scope of destructors implicitly-attached to [element] that run immediately after the
 * expression for [element] itself. Not every AST node carries a [Destruction], but those that are `terminatingScopes`
 * do; see discussion with [ScopeTree].
 *
 * [Remainder] represents the scope of user code running immediately after the initializer expression for the indexed
 * statement, until the end of the block.
 *
 * So: the following code can be broken down into the scopes beneath:
 *
 * ```text
 * let a = f().g( 'b: { let x = d(); let y = d(); x.h(y)  }   ) ;
 *
 *                                                              +-+ (D12.)
 *                                                        +-+       (D11.)
 *                                              +---------+         (R10.)
 *                                              +-+                  (D9.)
 *                                   +----------+                    (M8.)
 *                                 +----------------------+          (R7.)
 *                                 +-+                               (D6.)
 *                      +----------+                                 (M5.)
 *                    +-----------------------------------+          (M4.)
 *         +--------------------------------------------------+      (M3.)
 *         +--+                                                      (M2.)
 * +-----------------------------------------------------------+     (M1.)
 *
 *  (M1.): Node scope of the whole `let a = ...;` statement.
 *  (M2.): Node scope of the `f()` expression.
 *  (M3.): Node scope of the `f().g(..)` expression.
 *  (M4.): Node scope of the block labeled `'b:`.
 *  (M5.): Node scope of the `let x = d();` statement
 *  (D6.): Destruction scope for temporaries created during M5.
 *  (R7.): Remainder scope for block `'b:`, stmt 0 (let x = ...).
 *  (M8.): Node scope of the `let y = d();` statement.
 *  (D9.): DestructionScope for temporaries created during M8.
 * (R10.): Remainder scope for block `'b:`, stmt 1 (let y = ...).
 * (D11.): Destruction scope for temporaries and bindings from block `'b:`.
 * (D12.): Destruction scope for temporaries created during M1 (e.g., f()).
 * ```
 *
 * Note that while the above picture shows the destruction scopes as following their corresponding node scopes, in the
 * internal data structures of the compiler the destruction scopes are represented as enclosing parents. This is sound
 * because we use the enclosing parent relationship just to ensure that referenced values live long enough; phrased
 * another way, the starting point of each range is not really the important thing in the above picture, but rather the
 * ending point.
 */
sealed class Scope {
    /** Returns an element associated with this scope. */
    abstract val element: RsElement

    data class Node(override val element: RsElement) : Scope()

    /** Scope of the call-site for a function or closure (outlives the arguments as well as the body). */
    data class CallSite(override val element: RsElement) : Scope()

    /** Scope of arguments passed to a function or closure (they outlive its body). */
    data class Arguments(override val element: RsElement) : Scope()

    /** Scope of destructors for temporaries of node-id. */
    data class Destruction(override val element: RsElement) : Scope()

    /**
     * Scope of the condition and then block of an if expression.
     * Used for variables introduced in an if-let expression.
     */
    data class IfThen(override val element: RsBlock) : Scope()

    /** Scope following a `let id = expr;` binding in a block. */
    data class Remainder(override val element: RsBlock, val letDecl: RsLetDecl) : Scope()
}

/** Returns the span of this [Scope]. */
val Scope.span: TextRange
    get() {
        val span = element.textRange
        if (this is Remainder) {
            // Want span for scope starting after the let binding and ending at end of the block;
            // reuse span of block and shift `startOffset` forward to end of indexed stmt.
            val letSpan = letDecl.textRange

            // To avoid issues with macro-generated spans, the span of the let binding must be nested.
            if (span.startOffset <= letSpan.startOffset && letSpan.endOffset <= span.endOffset) {
                return TextRange(letSpan.endOffset, span.endOffset)
            }
        }
        return span
    }

typealias ScopeDepth = Int

data class ScopeInfo(val scope: Scope, val depth: ScopeDepth)

/** The region scope tree encodes information about region relationships. */
class ScopeTree {
    /** If not empty, this body is the root of this region hierarchy. */
    var rootBody: RsElement? = null

    /**
     * Maps from a scope to the enclosing scope;
     * this is usually corresponding to the lexical nesting, though in the case of closures the parent scope is the
     * innermost conditional expression or repeating block. (Note that the enclosing scope for the block associated
     * with a closure is the closure itself.)
     */
    private val parentMap: MutableMap<Scope, ScopeInfo> = hashMapOf()

    /** Maps from a variable or binding to the block in which that variable is declared. */
    private val varMap: MutableMap<RsElement, Scope> = hashMapOf()

    /** Maps from a `element` to the associated destruction scope (if any). */
    private val destructionScopes: MutableMap<RsElement, Scope> = hashMapOf()

    /**
     * Identifies expressions which, if captured into a temporary, ought to have a temporary whose lifetime extends to
     * the end of the enclosing *block*, and not the enclosing *statement*. Expressions that are not present in this
     * table are not rvalue candidates. The set of rvalue candidates is computed during type check based on a traversal
     * of the AST.
     */
    val rvalueCandidates: MutableMap<RsExpr, RvalueCandidateType> = mutableMapOf()

    fun recordScopeParent(childScope: Scope, parentInfo: ScopeInfo?) {
        if (parentInfo != null) {
            val prev = parentMap.put(childScope, parentInfo)
            check(prev == null)
        }

        // Record the destruction scopes for later so we can query them.
        if (childScope is Destruction) {
            destructionScopes[childScope.element] = childScope
        }
    }

    fun getDestructionScope(element: RsElement): Scope? = destructionScopes[element]

    fun recordVarScope(variable: RsPatBinding, lifetime: Scope) {
        check(variable != lifetime.element)
        varMap[variable] = lifetime
    }

    fun recordVarScope(variable: RsSelfParameter, lifetime: Scope) {
        check(variable != lifetime.element)
        varMap[variable] = lifetime
    }

    fun recordRvalueCandidate(expr: RsExpr, candidateType: RvalueCandidateType) {
        val lifetime = candidateType.lifetime
        if (lifetime != null) {
            check(expr != lifetime.element)
        }
        rvalueCandidates[expr] = candidateType
    }

    /** Returns the narrowest scope that encloses [scope], if any. */
    fun getEnclosingScope(scope: Scope): Scope? = parentMap[scope]?.scope

    /** Returns the lifetime of the local [variable], if any. */
    fun getVariableScope(variable: RsPatBinding): Scope? = varMap[variable]

    /** Returns the lifetime of the local [variable], if any. */
    fun getVariableScope(variable: RsSelfParameter): Scope? = varMap[variable]

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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ScopeTree

        if (rootBody != other.rootBody) return false
        if (parentMap != other.parentMap) return false
        if (varMap != other.varMap) return false
        if (destructionScopes != other.destructionScopes) return false
        return rvalueCandidates == other.rvalueCandidates
    }

    override fun hashCode(): Int {
        var result = rootBody?.hashCode() ?: 0
        result = 31 * result + parentMap.hashCode()
        result = 31 * result + varMap.hashCode()
        result = 31 * result + destructionScopes.hashCode()
        result = 31 * result + rvalueCandidates.hashCode()
        return result
    }
}

/**
 * Identifies the reason that a given expression is a rvalue candidate (see the `rvalueCandidates` field for more
 * information what rvalue candidates in general). In constants, the [lifetime] field is None to indicate that certain
 * expressions escape into 'static and should have no local cleanup scope.
 */
sealed class RvalueCandidateType {
    abstract val target: RsExpr
    abstract val lifetime: Scope?

    data class Borrow(override val target: RsExpr, override val lifetime: Scope?) : RvalueCandidateType()
    data class Pattern(override val target: RsExpr, override val lifetime: Scope?) : RvalueCandidateType()
}

private data class Context(
    /** The scope that contains any new variables declared, plus its depth in the scope tree. */
    var varParent: ScopeInfo? = null,

    /** Region parent of expressions, etc., plus its depth in the scope tree. */
    var parent: ScopeInfo? = null
)

private class RegionResolutionVisitor {
    /** The generated scope tree. */
    val scopeTree: ScopeTree = ScopeTree()

    var ctx: Context = Context()

    /**
     * [terminatingScopes] is a set containing each statement, or conditional/repeating expression.
     * These scopes are calling "terminating scopes" because, when attempting to find the scope of a temporary, by
     * default we search up the enclosing scopes until we encounter the terminating scope. A conditional/repeating
     * expression is one which is not guaranteed to execute exactly once upon entering the parent scope. This could be
     * because the expression only executes conditionally, such as the expression `b` in `a && b`, or because the
     * expression may execute many times, such as a loop body. The reason that we distinguish such expressions is that,
     * upon exiting the parent scope, we cannot statically know how many times the expression executed, and thus if the
     * expression creates temporaries we cannot know statically how many such temporaries we would have to cleanup.
     * Therefore, we ensure that the temporaries never outlast the conditional/repeating expression, preventing the
     * need for dynamic checks and/or arbitrary amounts of stack space. Terminating scopes end up being contained in a
     * DestructionScope that contains the destructor's execution.
     */
    var terminatingScopes: MutableSet<RsElement> = hashSetOf()

    fun visitBlock(block: RsBlock) {
        val prevCtx = ctx.copy()

        // We treat the tail expression in the block (if any) somewhat differently from the statements. The issue has to
        // do with temporary lifetimes. Consider the following:
        //
        //    quux({
        //        let inner = ... (&bar()) ...;
        //
        //        (... (&foo()) ...) // (the tail expression)
        //    }, other_argument());
        //
        // Each of the statements within the block is a terminating scope, and thus a temporary (e.g., the result of
        // calling `bar()` in the initializer expression for `let inner = ...;`) will be cleaned up immediately after
        // its corresponding statement (i.e., `let inner = ...;`) executes.
        //
        // On the other hand, temporaries associated with evaluating the tail expression for the block are assigned
        // lifetimes so that they will be cleaned up as part of the terminating scope *surrounding* the block
        // expression. Here, the terminating scope for the block expression is the `quux(..)` call; so those temporaries
        // will only be cleaned up *after* both `other_argument()` has run and also the call to `quux(..)`
        // itself has returned.

        enterNodeScope(block)
        ctx.varParent = ctx.parent

        val (stmts, expr) = block.expandedStmtsAndTailExpr
        for (stmt in stmts) {
            when {
                stmt is RsLetDecl && stmt.letElseBranch != null -> {
                    // Let-else has a special lexical structure for variables.
                    // First we take a checkpoint of the current scope context here.
                    var prevCtx2 = ctx.copy()

                    enterScope(Remainder(block, stmt))
                    ctx.varParent = ctx.parent
                    visitStmt(stmt)

                    // We need to back out temporarily to the last enclosing scope for the `else` block, so that even
                    // the temporaries receiving extended lifetime will be dropped inside this block.
                    val tmp = ctx
                    ctx = prevCtx2
                    prevCtx2 = tmp

                    val elseBlock = stmt.letElseBranch?.block
                    if (elseBlock != null) {
                        terminatingScopes.add(elseBlock)
                        visitBlock(elseBlock)
                    }

                    // From now on, we continue normally.
                    ctx = prevCtx2
                }

                stmt is RsLetDecl /* || stmt is RsItemElement */ -> {
                    // Each declaration introduces a subscope for bindings introduced by the declaration; this subscope
                    // covers a suffix of the block. Each subscope in a block has the previous subscope in the block as
                    // a parent, except for the first such subscope, which has the block itself as a parent.
                    enterScope(Remainder(block, stmt))
                    ctx.varParent = ctx.parent
                    visitStmt(stmt)
                }

                stmt is RsExprStmt || stmt is RsEmptyStmt -> visitStmt(stmt)
            }
        }
        expr?.let(::visitExpr)

        ctx = prevCtx
    }

    fun visitBody(bodyAndOwner: Body) {
        val (body, owner) = bodyAndOwner

        // Save all state that is specific to the outer function body.
        // These will be restored once down below, once we've visited the body.
        val outerCtx = ctx.copy()
        val outerTerminatingScopes = terminatingScopes
        terminatingScopes = hashSetOf()

        terminatingScopes.add(body)

        enterScope(CallSite(body))
        enterScope(Arguments(body))

        // The arguments and `self` are parented to the fn.
        ctx.varParent = ctx.parent
        ctx.parent = null
        if (owner is RsFunctionOrLambda) {
            val parameters = owner.valueParameterList
            parameters?.selfParameter?.let(::visitSelfParam)
            for (param in parameters?.valueParameterList.orEmpty()) {
                param.pat?.let(::visitPat)
            }
        }

        // The body of the every fn is a root scope.
        ctx.parent = ctx.varParent
        when (owner) {
            is RsFunction -> visitBlock(body as RsBlock)
            is RsLambdaExpr -> visitExpr(body as RsExpr)
            else -> {
                // Only functions have an outer terminating (drop) scope, while temporaries in constant initializers may
                // be 'static, but only according to rvalue lifetime semantics, using the same syntactical rules used
                // for let initializers.
                //
                // e.g., in `let x = &f();`, the temporary holding the result from the `f()` call lives for the entirety
                // of the surrounding block.
                //
                // Similarly, `const X: ... = &f();` would have the result of `f()` live for `'static`, implying (if
                // Drop restrictions on constants ever get lifted) that the value *could* have a destructor, but it'd
                // get leaked instead of the destructor running during the evaluation of `X` (if at all allowed by
                // CTFE).
                //
                // However, `const Y: ... = g(&f());`, like `let y = g(&f());`, would *not* let the `f()` temporary
                // escape into an outer scope (i.e., `'static`), which means that after `g` returns, it drops, and all
                // the associated destruction scope rules apply.
                ctx.varParent = null
                (body as? RsExpr)?.let { resolveLocal(null, it) }
            }
        }

        // Restore context we had at the start.
        ctx = outerCtx
        terminatingScopes = outerTerminatingScopes
    }

    fun visitMatchArm(arm: RsMatchArm) {
        val prevCtx = ctx.copy()

        enterScope(Node(arm))
        ctx.varParent = ctx.parent

        arm.expr?.let { terminatingScopes.add(it) }

        val guard = arm.matchArmGuard
        if (guard != null) {
            guard.expr?.let { terminatingScopes.add(it) }
        }

        walkMatchArm(arm)

        ctx = prevCtx
    }

    fun visitPat(pat: RsPat) {
        recordChildScope(Node(pat))
        walkPat(pat)
    }

    fun visitSelfParam(self: RsSelfParameter) {
        recordChildScope(Node(self))
        val parentScope = ctx.varParent?.scope
        if (parentScope != null) {
            scopeTree.recordVarScope(self, parentScope)
        }
    }

    fun visitPatBinding(binding: RsPatBinding) {
        val parentScope = ctx.varParent?.scope
        if (parentScope != null) {
            scopeTree.recordVarScope(binding, parentScope)
        }
    }

    fun visitStmt(stmt: RsStmt) {
        // Every statement will clean up the temporaries created during execution of that statement. Therefore each
        // statement has an associated destruction scope that represents the scope of the statement plus its
        // destructors, and thus the scope for which regions referenced by the destructors need to survive.
        terminatingScopes.add(stmt)

        val prevParent = ctx.parent?.copy()
        enterNodeScope(stmt)

        walkStmt(stmt)

        ctx.parent = prevParent
    }

    fun visitExpr(expr: RsExpr) {
        val prevCtx = ctx.copy()
        enterNodeScope(expr)

        when {
            // Conditional or repeating scopes are always terminating scopes, meaning that temporaries cannot outlive
            // them. This ensures fixed size stacks.
            expr is RsBinaryExpr && (expr.binaryOp.andand != null || expr.binaryOp.oror != null) -> {
                // expr is a short circuiting operator (|| or &&). As its functionality can't be overridden by traits,
                // it always processes bool sub-expressions. bools are Copy and thus we can drop any temporaries in
                // evaluation (read) order (with the exception of potentially failing let expressions). We achieve this
                // by enclosing the operands in a terminating scope, both the LHS and the RHS.

                // We optimize this a little in the presence of chains. Chains like a && b && c get lowered to
                // AND(AND(a, b), c). In here, b and c are RHS, while a is the only LHS operand in that chain. This
                // holds true for longer chains as well: the leading operand is always the only LHS operand that is not
                // a binop itself. Putting a binop like AND(a, b) into a terminating scope is not useful, thus we only
                // put the LHS into a terminating scope if it is not a binop.

                val lhs = expr.left
                val terminateLhs = when {
                    // let expressions can create temporaries that live on
                    lhs is RsLetExpr -> false
                    // binops already drop their temporaries, so there is no need to put them into a terminating scope.
                    // This is purely an optimization to reduce the number of terminating scopes.
                    lhs is RsBinaryExpr && (lhs.binaryOp.andand != null || lhs.binaryOp.oror != null) -> false
                    // otherwise: mark it as terminating
                    else -> true
                }
                if (terminateLhs) {
                    terminatingScopes.add(lhs)
                }

                // `Let` expressions (in a let-chain) shouldn't be terminating, as their temporaries should live beyond
                // the immediate expression
                val rhs = expr.right
                if (rhs != null && rhs !is RsLetExpr) {
                    terminatingScopes.add(rhs)
                }
            }

            expr is RsIfExpr -> {
                expr.block?.let { terminatingScopes.add(it) }
                expr.elseBranch?.block?.let { terminatingScopes.add(it) }
            }

            expr is RsLooplikeExpr -> {
                expr.block?.let { terminatingScopes.add(it) }
            }

            else -> {}
        }

        when {
            // Manually recurse over closures and inline consts, because they are the only case of nested bodies that
            // share the parent environment.
            expr is RsLambdaExpr -> {
                val body = expr.expr?.let { Body(it, expr) }
                body?.let(::visitBody)
            }

            expr is RsBinaryExpr && expr.isAssignBinaryExpr -> {
                expr.right?.let(::visitExpr)
                expr.left.let(::visitExpr)
            }

            expr is RsIfExpr -> {
                val exprCtx = ctx.copy()
                expr.block?.let { enterScope(IfThen(it)) }
                ctx.varParent = ctx.parent
                expr.condition?.expr?.let(::visitExpr)
                expr.block?.let(::visitBlock)
                ctx = exprCtx
                expr.elseBranch?.block?.let(::visitBlock)
                expr.elseBranch?.ifExpr?.let(::visitExpr)
            }

            else -> walkExpr(expr)
        }

        ctx = prevCtx
    }

    fun visitLetDecl(letDecl: RsLetDecl) {
        resolveLocal(letDecl.pat, letDecl.expr)
    }

    private fun walkStmt(stmt: RsStmt) {
        when (stmt) {
            is RsLetDecl -> visitLetDecl(stmt)
            is RsExprStmt -> visitExpr(stmt.expr)
        }
    }

    private fun walkMatchArm(arm: RsMatchArm) {
        visitPat(arm.pat)
        arm.matchArmGuard?.expr?.let(::visitExpr)
        arm.expr?.let(::visitExpr)
    }

    private fun walkPat(pat: RsPat) {
        when (pat) {
            is RsPatTupleStruct -> pat.patList.forEach(::visitPat)
            is RsPatIdent -> {
                pat.patBinding.let(::visitPatBinding)
                pat.pat?.let(::visitPat)
            }

            is RsPatStruct -> {
                for (field in pat.patFieldList) {
                    field.patBinding?.let(::visitPatBinding)
                    field.patFieldFull?.pat?.let(::visitPat)
                }
            }

            is RsOrPat -> pat.patList.forEach(::visitPat)
            is RsPatTup -> pat.patList.forEach(::visitPat)
            is RsPatBox -> visitPat(pat.pat)
            is RsPatRef -> visitPat(pat.pat)
            is RsPatConst -> visitExpr(pat.expr)
            is RsPatRange -> pat.patConstList.forEach { visitExpr(it.expr) }
            is RsPatSlice -> pat.patList.forEach(::visitPat)
        }
    }

    private fun walkExpr(expr: RsExpr) {
        when (expr) {
            is RsArrayExpr -> expr.exprList.forEach(::visitExpr)
            is RsStructLiteral -> {
                expr.structLiteralBody.structLiteralFieldList.mapNotNull { it.expr }.forEach(::visitExpr)
                expr.structLiteralBody.expr?.let(::visitExpr)
            }

            is RsTupleExpr -> expr.exprList.forEach(::visitExpr)
            is RsCallExpr -> {
                expr.expr.let(::visitExpr)
                expr.valueArgumentList.exprList.forEach(::visitExpr)
            }

            is RsDotExpr -> {
                expr.expr.let(::visitExpr)
                expr.methodCall?.valueArgumentList?.exprList?.forEach(::visitExpr)
            }

            is RsBinaryExpr -> {
                if (expr.isAssignBinaryExpr) {
                    expr.right?.let(::visitExpr)
                    expr.left.let(::visitExpr)
                } else {
                    expr.left.let(::visitExpr)
                    expr.right?.let(::visitExpr)
                }
            }

            is RsUnaryExpr -> expr.expr?.let(::visitExpr)
            is RsCastExpr -> expr.expr.let(::visitExpr)
            is RsLetExpr -> {
                expr.expr?.let(::visitExpr)
                expr.pat?.let(::visitPat)
            }

            is RsIfExpr -> {
                expr.condition?.expr?.let(::visitExpr)
                expr.block?.let(::visitBlock)
                expr.elseBranch?.block?.let(::visitBlock)
                expr.elseBranch?.ifExpr?.let(::visitExpr)
            }

            is RsLoopExpr -> expr.block?.let(::visitBlock)

            is RsWhileExpr -> {
                expr.condition?.expr?.let(::visitExpr)
                expr.block?.let(::visitBlock)
            }

            is RsForExpr -> {
                expr.expr?.let(::visitExpr)
                expr.pat?.let(::visitPat)
                expr.block?.let(::visitBlock)
            }

            is RsMatchExpr -> {
                expr.expr?.let(::visitExpr)
                expr.matchBody?.matchArmList?.forEach(::visitMatchArm)
            }

            is RsBlockExpr -> visitBlock(expr.block)
            is RsIndexExpr -> expr.exprList.forEach(::visitExpr)
            is RsBreakExpr -> expr.expr?.let(::visitExpr)
            is RsRetExpr -> expr.expr?.let(::visitExpr)
            is RsMacroExpr -> Unit // TODO
            is RsYieldExpr -> expr.expr?.let(::visitExpr)
        }
    }

    /** Records the current parent (if any) as the parent of [childScope]. Returns the depth of [childScope]. */
    private fun recordChildScope(childScope: Scope): ScopeDepth {
        val parentInfo = ctx.parent
        scopeTree.recordScopeParent(childScope, parentInfo)
        // If [childScope] has no parent, it must be the root node, and so has a depth of 1.
        // Otherwise, its depth is one more than its parent's.
        return parentInfo?.let { (_, depth) -> depth + 1 } ?: 1
    }

    /**
     * Records the current parent (if any) as the parent of [childScope], and sets [childScope] as the new current parent.
     */
    private fun enterScope(childScope: Scope) {
        val childDepth = recordChildScope(childScope)
        ctx.parent = ScopeInfo(childScope, childDepth)
    }

    fun enterNodeScope(element: RsElement) {
        // If node was previously marked as a terminating scope during the recursive visit of its parent node in the
        // AST, then we need to account for the destruction scope representing the scope of the destructors that run
        // immediately after it completes.
        if (element in terminatingScopes) {
            enterScope(Destruction(element))
        }
        enterScope(Node(element))
    }

    private fun resolveLocal(pat: RsPat?, init: RsExpr?) {
        val blockScope = ctx.varParent?.scope

        // As an exception to the normal rules governing temporary lifetimes, initializers in a let have a temporary
        // lifetime of the enclosing block. This means that e.g., a program like the following is legal:
        //
        //     let ref x = HashMap::new();
        //
        // Because the hash map will be freed in the enclosing block.
        //
        // We express the rules more formally based on 3 grammars (defined fully in the helpers below that implement
        // them):
        //
        // 1. `E&`, which matches expressions like `&<rvalue>` that own a pointer into the stack.
        //
        // 2. `P&`, which matches patterns like `ref x` or `(ref x, ref y)` that produce ref bindings into the value
        //    they are matched against or something (at least partially) owned by the value they are matched against.
        //    (By partially owned, I mean that creating a binding into a ref-counted or managed value would still
        //    count.)
        //
        // 3. `ET`, which matches both rvalues like `foo()` as well as places based on rvalues like `foo().x[2].y`.
        //
        // A subexpression `<rvalue>` that appears in a let initializer `let pat [: ty] = expr` has an extended
        // temporary lifetime if any of the following conditions are met:
        //
        // A. `pat` matches `P&` and `expr` matches `ET`
        //    (covers cases where `pat` creates ref bindings into an rvalue produced by `expr`)
        // B. `ty` is a borrowed pointer and `expr` matches `ET`
        //    (covers cases where coercion creates a borrow)
        // C. `expr` matches `E&`
        //    (covers cases `expr` borrows an rvalue that is then assigned to memory owned by the binding)
        //
        // Here are some examples hopefully giving an intuition where each rule comes into play and why:
        //
        // Rule A. `let (ref x, ref y) = (foo().x, 44)`. The rvalue `(22, 44)` would have an extended lifetime, but not
        // `foo()`.
        //
        // Rule B. `let x = &foo().x`. The rvalue `foo()` would have extended lifetime.
        //
        // In some cases, multiple rules may apply (though not to the same rvalue). For example:
        //
        //     let ref x = [&a(), &b()];
        //
        // Here, the expression `[...]` has an extended lifetime due to rule A, but the inner rvalues `a()` and `b()`
        // have an extended lifetime due to rule C.

        if (init != null) {
            recordRvalueScopeIfBorrowExpr(init, blockScope)

            if (pat != null && isBindingPat(pat)) {
                scopeTree.recordRvalueCandidate(init, RvalueCandidateType.Pattern(init, blockScope))
            }
        }

        init?.let(::visitExpr)
        pat?.let(::visitPat)
    }

    /**
     * Returns `true` if `pat` match the `P&` non-terminal.
     *
     * ```text
     *     P& = ref X
     *        | StructName { ..., P&, ... }
     *        | VariantName(..., P&, ...)
     *        | [ ..., P&, ... ]
     *        | ( ..., P&, ... )
     *        | ... "|" P& "|" ...
     *        | box P&
     * ```
     */
    private fun isBindingPat(pat: RsPat): Boolean {
        // Note that the code below looks for *explicit* refs only, that is, it won't know about *implicit* refs.
        //
        // This is not a problem. For example, consider
        //
        //      let (ref x, ref y) = (Foo { .. }, Bar { .. });
        //
        // Due to the explicit refs on the left hand side, the below code would signal that the temporary value on the
        // right hand side should live until the end of the enclosing block (as opposed to being dropped after the let
        // is complete).
        //
        // To create an implicit ref, however, you must have a borrowed value on the RHS already, as in this example:
        //
        //      let Foo { x, .. } = &Foo { x: ..., ... };
        //
        // in place of
        //
        //      let Foo { ref x, .. } = Foo { ... };
        //
        // In the former case (the implicit ref version), the temporary is created by the & expression, and its lifetime
        // would be extended to the end of the block (due to a different rule, not the below code).
        return when (pat) {
            is RsPatIdent -> pat.patBinding.kind is RsBindingModeKind.BindByReference
            is RsPatStruct -> pat.patFieldList.any { isBindingPat(it.patFieldFull?.pat ?: return@any false) }
            is RsPatSlice -> pat.patList.any(::isBindingPat)
            is RsOrPat -> pat.patList.any(::isBindingPat)
            is RsPatTupleStruct -> pat.patList.any(::isBindingPat)
            is RsPatTup -> pat.patList.any(::isBindingPat)
            is RsPatBox -> isBindingPat(pat.pat)
            else -> false
        }
    }

    /**
     * If `expr` matches the `E&` grammar, then records an extended rvalue scope as appropriate:
     *
     * ```text
     *     E& = & ET
     *        | StructName { ..., f: E&, ... }
     *        | [ ..., E&, ... ]
     *        | ( ..., E&, ... )
     *        | {...; E&}
     *        | box E&
     *        | E& as ...
     *        | ( E& )
     * ```
     */
    private fun recordRvalueScopeIfBorrowExpr(expr: RsExpr, lifetime: Scope?) {
        when (expr) {
            is RsUnaryExpr -> {
                val subexpr = expr.expr
                val op = expr.operatorType
                if ((op == UnaryOperator.REF || op == UnaryOperator.REF_MUT) && subexpr != null) {
                    recordRvalueScopeIfBorrowExpr(subexpr, lifetime)
                    scopeTree.recordRvalueCandidate(subexpr, RvalueCandidateType.Borrow(subexpr, lifetime))
                }
            }

            is RsStructLiteral -> {
                for (field in expr.structLiteralBody.structLiteralFieldList) {
                    recordRvalueScopeIfBorrowExpr(field.expr ?: continue, lifetime)
                }
            }

            is RsArrayExpr -> {
                for (subexpr in expr.exprList) {
                    recordRvalueScopeIfBorrowExpr(subexpr, lifetime)
                }
            }

            is RsTupleExpr -> {
                for (subexpr in expr.exprList) {
                    recordRvalueScopeIfBorrowExpr(subexpr, lifetime)
                }
            }

            is RsCastExpr -> {
                recordRvalueScopeIfBorrowExpr(expr.expr, lifetime)
            }

            is RsBlockExpr -> {
                val subexpr = expr.block.expandedTailExpr ?: return
                recordRvalueScopeIfBorrowExpr(subexpr, lifetime)
            }

            is RsCallExpr, is RsMethodCall -> {
                // TODO: choose call arguments here for candidacy for extended parameter rule application
            }

            is RsIndexExpr -> {
                // TODO: select the indices as candidate for rvalue scope rules
            }

            else -> Unit
        }
    }
}

private data class Body(val value: RsElement, val owner: RsElement)

/** Per-body [ScopeTree]. The [contextOwner]. The [contextOwner] should be the owner of the body. */
fun getRegionScopeTree(contextOwner: RsInferenceContextOwner): ScopeTree {
    val visitor = RegionResolutionVisitor()
    val body = contextOwner.body ?: return visitor.scopeTree
    visitor.scopeTree.rootBody = body
    visitor.visitBody(Body(body, contextOwner))
    return visitor.scopeTree
}
