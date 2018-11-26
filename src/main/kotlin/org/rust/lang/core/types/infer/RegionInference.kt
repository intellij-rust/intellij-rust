/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

import org.rust.ide.annotator.isMut
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.infer.BorrowKind.MutableBorrow
import org.rust.lang.core.types.infer.Categorization.*
import org.rust.lang.core.types.infer.PointerKind.BorrowedPointer
import org.rust.lang.core.types.infer.PointerKind.UnsafePointer
import org.rust.lang.core.types.infer.SubRegionOrigin.*
import org.rust.lang.core.types.infer.outlives.OutlivesEnvironment
import org.rust.lang.core.types.infer.outlives.RegionObligationCause
import org.rust.lang.core.types.infer.outlives.processRegisteredRegionObligations
import org.rust.lang.core.types.infer.outlives.typeMustOutlive
import org.rust.lang.core.types.regions.*
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type

/**
 * Region inference for a inference context owner's body.
 * Walks the body and adds various add'l constraints that are needed for region inference.
 */
fun RsFnInferenceContext.inferRegions(subject: RsInferenceContextOwner) {
    // region inference assumes that type inference succeeded
    if (ctx.diagnostics.isNotEmpty()) return

    val body = subject.body ?: return
    val rctx = RegionContext(this, body, subject, callerBounds)
    subject.accept(rctx)
    rctx.visitRegionObligations()
    rctx.resolveRegions()

    check(ctx.freeRegionMap == null)
    ctx.freeRegionMap = rctx.outlives.freeRegionMap
}

class RegionContext private constructor(
    val fctx: RsFnInferenceContext,
    val regionScopeTree: ScopeTree,
    val outlives: OutlivesEnvironment,

    /** Innermost body. */
    var body: RsElement,

    /** Call site scope of innermost fn body. */
    var callSiteScope: Scope.CallSite?,

    /** Element being analyzed (the subject of the analysis). */
    val subject: RsInferenceContextOwner
) : RsVisitor() {

    constructor(
        fctx: RsFnInferenceContext,
        initialBody: RsElement,
        subject: RsInferenceContextOwner,
        callerBounds: List<Predicate>
    ) : this(
        fctx,
        getRegionScopeTree(subject),
        OutlivesEnvironment(callerBounds),
        initialBody,
        null,
        subject
    )

    /**
     * This is the "main" function when region-inference for a function item.
     * It begins by updating various fields (e.g., [callSiteScope] and [outlives]) to be appropriate to
     * the function and then adds constraints and [outlives]) to be appropriate to the function and then
     * adds constraints derived from the function body.
     */
    override fun visitFunction(fn: RsFunction) {
        val oldBody = body
        val oldCallSiteScope = callSiteScope
        val currentBody = fn.block ?: return
        body = currentBody
        callSiteScope = Scope.CallSite(body)

        outlives.addImpliedBounds(fctx.ctx, fn.typeOfValue, currentBody)
        linkFnArgs(Scope.Node(currentBody), fn.selfParameter, fn.valueParameters)
        visitBlock(currentBody)
        visitRegionObligations()

        val callSiteRegion = ReScope(checkNotNull(callSiteScope))
        val returnExpr = currentBody.expr
        typeOfNodeMustOutlive(CallReturn(fn), currentBody, callSiteRegion)
        if (returnExpr != null) {
            typeOfNodeMustOutlive(CallReturn(returnExpr), returnExpr, callSiteRegion)
        }

        // TODO: handle anon types

        body = oldBody
        callSiteScope = oldCallSiteScope
    }

    override fun visitMatchArm(arm: RsMatchArm) {
        for (pat in arm.patList) {
            constrainBindingsInPat(pat)
        }
        super.visitMatchArm(arm)
    }

    override fun visitLetDecl(let: RsLetDecl) {
        let.pat?.let { constrainBindingsInPat(it) }
        linkLocal(let)
        super.visitLetDecl(let)
    }

    override fun visitExpr(expr: RsExpr) {
        // No matter what, the type of each expression must outlive the
        // scope of that expression. This also guarantees basic WF.
        val exprTy = resolveNodeType(expr)
        // the region corresponding to this expression
        val exprRegion = ReScope(Scope.Node(expr))
//        typeMustOutlive(ExprTypeIsNotInScope(expr, exprTy), exprTy, exprRegion)  // TODO

        val isMethodCall = fctx.ctx.isMethodCall(expr)

        // TODO: if we are calling a method check that all of the type provided as arguments are well-formed.

        when {
            // TODO: handle RsPathExpr
            expr is RsCallExpr -> {
                val callee = if (isMethodCall) expr.expr else null
                constrainCall(expr, callee, expr.valueArgumentList.exprList)
                super.visitExpr(expr)
            }
            expr is RsDotExpr && isMethodCall -> {
                val args = expr.methodCall?.valueArgumentList?.exprList.orEmpty()
                constrainCall(expr, expr.expr, args)
                super.visitExpr(expr)
            }
            expr is RsBinaryExpr && expr.isAssignBinaryExpr -> {
                if (isMethodCall) {
                    val args = expr.right?.let { listOf(it) } ?: emptyList()
                    constrainCall(expr, expr.left, args)
                }
                super.visitExpr(expr)
            }
            expr is RsIndexExpr && isMethodCall -> {
                val args = expr.indexExpr?.let { listOf(it) } ?: emptyList()
                constrainCall(expr, expr.containerExpr, args)
                super.visitExpr(expr)
            }
            expr is RsIndexExpr -> {
                // For a[b], the lifetime of a must enclose the deref
                val containerTy = expr.containerExpr?.let { resolveExprTypeAdjusted(it) } ?: TyUnknown
                constrainIndex(expr, containerTy)
                super.visitExpr(expr)
            }
            expr is RsBinaryExpr && isMethodCall -> {
                val args = expr.right?.let { listOf(it) } ?: emptyList()
                constrainCall(expr, expr.left, args)
                super.visitExpr(expr)
            }
            expr is RsBinaryExpr -> {
                // If you do `x OP y`, then the types of `x` and `y` must outlive the operation you are performing.
                val leftTy = resolveExprTypeAdjusted(expr.left)
                val rightTy = expr.right?.let { resolveExprTypeAdjusted(it) } ?: TyUnknown
                typeMustOutlive(Operand(expr), leftTy, exprRegion)
                typeMustOutlive(Operand(expr), rightTy, exprRegion)
                super.visitExpr(expr)
            }
            expr is RsUnaryExpr && expr.mul != null -> {
                if (isMethodCall) {
                    constrainCall(expr, expr.expr, emptyList())
                }
                val baseTy = expr.expr?.let { resolveExprTypeAdjusted(it) }
                if (baseTy is TyReference) {
                    makeSubRegionDueToDereference(expr, exprRegion, baseTy.region)
                }
                super.visitExpr(expr)
            }
            expr is RsUnaryExpr && expr.and != null -> {
                expr.expr?.let { linkAddrOf(expr, Mutability.valueOf(expr.isMut), it) }
                val ty = resolveNodeType(expr)
                typeMustOutlive(AddrOf(expr), ty, exprRegion)
                super.visitExpr(expr)
            }
            expr is RsUnaryExpr && isMethodCall -> {
                constrainCall(expr, expr.expr, emptyList())
                super.visitExpr(expr)
            }
            expr is RsCastExpr -> {
                // Determine if we are casting `source` to a trait instance.  If so, we have to be sure that the type
                // of the source obeys the trait's region bound.
                constrainCast(expr, expr.expr)
                super.visitExpr(expr)
            }
            expr is RsMatchExpr -> {
                expr.expr?.let { linkMatch(it, expr.matchBody?.matchArmList.orEmpty()) }
                super.visitExpr(expr)
            }
            expr is RsWhileExpr -> {
                expr.condition?.expr?.let { visitExpr(it) } ?: return
                expr.block?.let { visitBlock(it) } ?: return
            }
            expr is RsRetExpr -> {
                val returnExpr = expr.expr ?: return
                val callSiteRegion = callSiteScope?.let { ReScope(it) } ?: return
                typeOfNodeMustOutlive(CallReturn(returnExpr), returnExpr, callSiteRegion)
                super.visitExpr(expr)
            }
            else -> super.visitExpr(expr)
        }
    }

    override fun visitElement(element: RsElement) {
        if (element is RsItemElement) return
        element.acceptChildren(this)
    }

    fun visitRegionObligations() {
        // Region inference can introduce new pending obligations which, when processed, might generate new region
        // obligations. So make sure we process those.
        fctx.ctx.fulfill.selectUntilError()

        fctx.ctx.processRegisteredRegionObligations(
            outlives.regionBoundPairs,
            fctx.implicitRegionBound,
            fctx.callerBounds,
            body
        )
    }

    fun resolveRegions() = fctx.ctx.resolveRegions(subject, regionScopeTree, outlives)

    /** Try to resolve the type for the given node. */
    private fun resolveType(unresolved: Ty) =
        fctx.ctx.resolveTypeVarsIfPossible(unresolved)

    /** Try to resolve the type for the given node. */
    private fun resolveNodeType(element: RsElement) =
        resolveType(fctx.ctx.getNodeType(element))

    /** Try to resolve the type for the given node. */
    private fun resolveExprTypeAdjusted(expr: RsExpr) =
        resolveType(fctx.ctx.getExprTypeAdjusted(expr))

    private fun constrainBindingsInPat(pat: RsPat) {
        pat.forEachBinding { binding ->
            val varScope = regionScopeTree.getVariableScope(binding)
            val varRegion = checkNotNull(varScope?.let { ReScope(it) })
            val origin = BindingTypeIsNotValidAtDecl(binding)
            typeOfNodeMustOutlive(origin, binding, varRegion)
        }
    }

    private fun constrainCast(castExpr: RsExpr, sourceExpr: RsExpr) {
        val sourceTy = resolveNodeType(sourceExpr)
        val targetTy = resolveNodeType(castExpr)
        walkCast(castExpr, sourceTy, targetTy)
    }

    private fun walkCast(castExpr: RsExpr, fromTy: Ty, toTy: Ty) {
        when {
            fromTy is TyReference && toTy is TyReference -> {
                // Target cannot outlive source, naturally.
                fctx.ctx.makeSubRegion(Reborrow(castExpr), toTy.region, fromTy.region)
                walkCast(castExpr, fromTy.referenced, toTy.referenced)
            }
            toTy is TyTraitObject -> {
                // When T is existentially quantified as a trait `Foo + 'to`, it must outlive the region bound `'to`.
                typeMustOutlive(RelateObjectBound(castExpr), fromTy, toTy.region)
            }
            // TODO: process `Box<T>`
        }
    }

    /**
     * Invoked on every call site (i.e., normal calls, method calls, and overloaded operators).
     * Constrains the regions which appear in the type of the function.
     * Also constrains the regions that appear in the arguments appropriately.
     */
    private fun constrainCall(callExpr: RsExpr, receiver: RsExpr?, argExprs: List<RsExpr>) {
        // [calleeRegion] is the scope representing the time in which the call occurs.
        val calleeRegion = ReScope(Scope.Node(callExpr))

        for (argExpr in argExprs) {
            // Ensure that any regions appearing in the argument type are valid for at least the lifetime of function:
//            typeOfNodeMustOutlive(CallArg(argExpr), argExpr, calleeRegion)  // TODO
        }

        // as loop above, but for receiver
        if (receiver != null) {
            typeOfNodeMustOutlive(CallRcvr(receiver), receiver, calleeRegion)
        }
    }

    private inline fun <T> withMemoryCategorizationContext(action: (MemoryCategorizationContext) -> T): T =
        action(MemoryCategorizationContext(fctx.ctx.lookup, fctx.ctx))

    private fun makeSubRegionDueToDereference(element: RsElement, minRegion: Region, maxRegion: Region) =
        fctx.ctx.makeSubRegion(DerefPointer(element), minRegion, maxRegion)

    /**
     * Invoked on any index expression that occurs.
     * Checks that if this is a slice being indexed, the lifetime of the pointer includes the deref expr.
     */
    private fun constrainIndex(indexExpr: RsExpr, indexTy: Ty) {
        if (indexTy is TyReference && (indexTy.referenced is TySlice || indexTy.referenced is TyStr)) {
            val indexExprRegion = ReScope(Scope.Node(indexExpr))
            fctx.ctx.makeSubRegion(IndexSlice(indexExpr), indexExprRegion, indexTy.region)
        }
    }

    /**
     * Guarantees that any lifetimes which appear in the type of the [element] (after applying adjustments) are valid
     * for at least [minRegion].
     */
    private fun typeOfNodeMustOutlive(origin: SubRegionOrigin, element: RsElement, minRegion: Region) {
        val ty = fctx.ctx
            .getAdjustments(element)
            .lastOrNull()
            ?.target
            ?.let { resolveType(it) }
            ?: resolveNodeType(element)
        typeMustOutlive(origin, ty, minRegion)
    }

    /**
     * Adds constraints to inference such that `T: 'a` holds (or reports an error if it cannot).
     *
     * @param origin the reason we need this constraint
     * @param ty     the type `T`
     * @param region the region `'a`
     */
    private fun typeMustOutlive(origin: SubRegionOrigin, ty: Ty, region: Region) {
        fctx.ctx.typeMustOutlive(
            outlives.regionBoundPairs,
            fctx.implicitRegionBound,
            fctx.callerBounds,
            origin,
            ty,
            region
        )
    }

    /**
     * Computes the guarantor for an expression `&base` and then ensures that the lifetime of the resulting pointer is
     * linked to the lifetime of its guarantor (if any).
     */
    private fun linkAddrOf(expr: RsExpr, mutability: Mutability, base: RsExpr) {
        val cmt = withMemoryCategorizationContext { it.processExpr(base) }
        linkRegionFromNodeType(expr, mutability, cmt)
    }

    /**
     * Computes the guarantors for any ref bindings in a `let` and then ensures that the lifetime of the resulting
     * pointer is linked to the lifetime of the initialization expression.
     */
    private fun linkLocal(local: RsLetDecl) {
        val initExpr = local.expr ?: return
        val pat = local.pat ?: return
        val discriminantCmt = withMemoryCategorizationContext { it.processExpr(initExpr) }
        linkPattern(discriminantCmt, pat)
    }

    /**
     * Computes the guarantors for any ref bindings in a match and then ensures that the lifetime of the resulting
     * pointer is linked to the lifetime of its guarantor (if any).
     */
    private fun linkMatch(discriminant: RsExpr, arms: List<RsMatchArm>) {
        val discriminantCmt = withMemoryCategorizationContext { it.processExpr(discriminant) }
        for (arm in arms) {
            for (rootPat in arm.patList) {
                linkPattern(discriminantCmt, rootPat)
            }
        }
    }

    /**
     * Computes the guarantors for any ref bindings in a match and then ensures that the lifetime of the resulting
     * pointer is linked to the lifetime of its guarantor (if any).
     */
    private fun linkFnArgs(bodyScope: Scope, selfArg: RsSelfParameter?, args: List<RsValueParameter>) {
        val bodyRegion = ReScope(bodyScope)

        fun linkFnArg(arg: RsElement) {
            val argTy = when (arg) {
                is RsSelfParameter -> arg.typeReference?.type
                is RsValueParameter -> arg.typeReference?.type
                else -> null
            } ?: TyUnknown
            val argCmt = withMemoryCategorizationContext { it.processRvalue(arg, bodyRegion, argTy) }
            (arg as? RsValueParameter)?.pat?.let { linkPattern(argCmt, it) }
        }

        selfArg?.let { linkFnArg(it) }
        for (arg in args) {
            linkFnArg(arg)
        }
    }

    /** Link lifetimes of any ref bindings in [rootPat] to the pointers found in the discriminant, if needed. */
    private fun linkPattern(discriminantCmt: Cmt, rootPat: RsPat) {
        withMemoryCategorizationContext { context ->
            context.walkPat(discriminantCmt, rootPat) { subCmt, subPat ->
                val kind = (subPat as? RsPatIdent)?.patBinding?.kind
                if (kind is RsBindingModeKind.BindByReference) {
                    linkRegionFromNodeType(subPat, kind.mutability, subCmt)
                }
            }
        }
    }

    /**
     * Like [linkRegion], except that the region is extracted from the type of [element], which must be some reference
     * (`&T`, `&str`, etc).
     */
    private fun linkRegionFromNodeType(element: RsElement, mutability: Mutability, borrowCmt: Cmt) {
        val ty = resolveNodeType(element)
        if (ty is TyReference) {
            linkRegion(element, ty.region, BorrowKind.from(mutability), borrowCmt)
        }
    }

    /**
     * Informs the inference engine that [borrowCmt] is being borrowed with [borrowKind] and [borrowRegion].
     * In order to ensure borrowck is satisfied, this may create constraints between regions, as explained in
     * [linkReborrowedRegion].
     */
    private fun linkRegion(element: RsElement, borrowRegion: Region, borrowKind: BorrowKind, borrowCmt: Cmt) {
        val origin = DataBorrowed(element, borrowCmt.ty)
        typeMustOutlive(origin, borrowCmt.ty, borrowRegion)

        var tempBorrowKind = borrowKind
        var borrowCmtCategory = borrowCmt.category ?: return

        if (borrowCmtCategory is Local) {
            val maybePat = borrowCmtCategory.element
            if (maybePat is RsPatBinding) {
                val cause = Reborrow(element)
                val scope = regionScopeTree.getVariableScope(maybePat)
                scope?.let { fctx.ctx.makeSubRegion(cause, borrowRegion, ReScope(it)) }
            }
        }

        while (true) {
            when {
                borrowCmtCategory is Deref && borrowCmtCategory.pointerKind is BorrowedPointer -> {
                    val pointerKind = borrowCmtCategory.pointerKind as BorrowedPointer
                    val (cmt, kind) = linkReborrowedRegion(
                        element,
                        borrowRegion,
                        tempBorrowKind,
                        borrowCmtCategory.cmt,
                        pointerKind.region,
                        pointerKind.borrowKind
                    ) ?: return
                    borrowCmtCategory = cmt.category ?: return
                    tempBorrowKind = kind
                }

                // Borrowing interior or owned data requires the base to be valid and borrowable in the same fashion.
                borrowCmtCategory is Downcast -> borrowCmtCategory = borrowCmtCategory.cmt.category ?: return
                borrowCmtCategory is Interior -> borrowCmtCategory = borrowCmtCategory.cmt.category ?: return

                borrowCmtCategory is Deref && borrowCmtCategory.pointerKind is UnsafePointer
                    || borrowCmtCategory is StaticItem
                    || borrowCmtCategory is Local
                    || borrowCmtCategory is Rvalue -> {
                    // These are all "base cases" with independent lifetimes that are not subject to inference
                    return
                }
            }
        }
    }

    private fun linkReborrowedRegion(
        element: RsElement,
        borrowRegion: Region,
        borrowKind: BorrowKind,
        refCmt: Cmt,
        refRegion: Region,
        refKind: BorrowKind
    ): Pair<Cmt, BorrowKind>? {
        val cause = Reborrow(element)
        fctx.ctx.makeSubRegion(cause, borrowRegion, refRegion)
        return (refKind as? MutableBorrow)?.let { Pair(refCmt, borrowKind) }
    }
}

/** The origin of a `'a <= 'b` constraint. */
sealed class SubRegionOrigin {
    abstract val element: RsElement

    /** Dereference of reference must be within its lifetime. */
    data class DerefPointer(override val element: RsElement) : SubRegionOrigin()

    /** Index into slice must be within its lifetime. */
    data class IndexSlice(override val element: RsElement) : SubRegionOrigin()

    /** When casting `&'a T` to an `&'b Trait` object, relating `'a` to `'b`. */
    data class RelateObjectBound(override val element: RsElement) : SubRegionOrigin()

    /** Some type parameter was instantiated with the given type, and that type must outlive some region. */
    data class RelateParamBound(override val element: RsElement, val ty: Ty) : SubRegionOrigin()

    /** The given region parameter was instantiated with a region that must outlive some other region. */
    data class RelateRegionParamBound(override val element: RsElement) : SubRegionOrigin()

    /** Creating a pointer `b` to contents of another reference. */
    data class Reborrow(override val element: RsElement) : SubRegionOrigin()

    /** Data with type `Ty<'tcx>` was borrowed. */
    data class DataBorrowed(override val element: RsElement, val ty: Ty) : SubRegionOrigin()

    /** The type T of an expression E must outlive the lifetime for E. */
    data class ExprTypeIsNotInScope(override val element: RsElement, val ty: Ty) : SubRegionOrigin()

    /** (&'a &'b T) where a >= b. */
    data class ReferenceOutlivesReferent(override val element: RsElement, val ty: Ty) : SubRegionOrigin()

    /** A `ref b` whose region does not enclose the decl site. */
    data class BindingTypeIsNotValidAtDecl(override val element: RsElement) : SubRegionOrigin()

    /** Regions appearing in a method receiver must outlive method call. */
    data class CallRcvr(override val element: RsElement) : SubRegionOrigin()

    /** Regions appearing in a function argument must outlive func call. */
    data class CallArg(override val element: RsElement) : SubRegionOrigin()

    /** Region in return type of invoked fn must enclose call. */
    data class CallReturn(override val element: RsElement) : SubRegionOrigin()

    /** Operands must be in scope. */
    data class Operand(override val element: RsElement) : SubRegionOrigin()

    /** Region resulting from a `&` expr must enclose the `&` expr. */
    data class AddrOf(override val element: RsElement) : SubRegionOrigin()

    companion object {
        fun fromObligationCause(cause: RegionObligationCause, default: () -> SubRegionOrigin): SubRegionOrigin {
            return when (cause) {
                is RegionObligationCause.ReferenceOutlivesReferent ->
                    SubRegionOrigin.ReferenceOutlivesReferent(cause.element, cause.ty)
                else -> default()
            }
        }
    }
}

/** Reasons to create a region inference variable. */
sealed class ReVarOrigin {

    /** Region variables created for ill-categorized reasons, mostly indicates places in need of refactoring. */
    data class MiscVariable(val element: RsElement) : ReVarOrigin()

    /** Regions created by `&` operator. */
    data class AddrOfRegion(val element: RsElement) : ReVarOrigin()

    /** Regions created as part of an automatic coercion. */
    data class Coercion(val element: RsElement) : ReVarOrigin()

    /** Region variables created as the values for early-bound regions. */
    data class EarlyBoundRegion(val element: RsElement) : ReVarOrigin()
}
