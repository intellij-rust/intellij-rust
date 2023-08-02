/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValuesManager
import org.rust.lang.core.dfa.Cmt
import org.rust.lang.core.dfa.ControlFlowGraph
import org.rust.lang.core.dfa.MemoryCategorizationContext
import org.rust.lang.core.dfa.borrowck.BorrowCheckContext
import org.rust.lang.core.dfa.borrowck.BorrowCheckResult
import org.rust.lang.core.dfa.liveness.Liveness
import org.rust.lang.core.dfa.liveness.LivenessContext
import org.rust.lang.core.mir.borrowck.MirBorrowCheckResult
import org.rust.lang.core.mir.borrowck.doMirBorrowCheck
import org.rust.lang.core.mir.mirBody
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.resolve.KnownItems
import org.rust.lang.core.resolve.knownItems
import org.rust.lang.core.types.infer.*
import org.rust.lang.core.types.regions.getRegionScopeTree
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyTypeParameter
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.stdext.withNext


/**
 * A [Ty]pe of the type reference without normalization of normalizable associated type projections
 * ([org.rust.lang.core.types.ty.TyProjection]).
 *
 * Consider the code:
 *
 * ```
 * struct S;
 * trait Trait { type Type; }
 * impl Trait for S { type Type = i32; }
 * type A = <S as Trait>::Type;
 *                      //^ The type reference
 * ```
 *
 * [rawType] returns [org.rust.lang.core.types.ty.TyProjection] `<S as Trait>::Type` for the type
 * reference in this case. If you need [org.rust.lang.core.types.ty.TyInteger] `i32` instead, use [normType].
 */
val RsTypeReference.rawType: Ty
    get() = TyLowering.lowerTypeReference(this)

/**
 * A [Ty]pe of the type reference WITH normalization of normalizable associated type projections
 * ([org.rust.lang.core.types.ty.TyProjection]).
 */
val RsTypeReference.normType: Ty
    get() {
        val rawType = rawType
        return if (rawType.hasTyProjection) {
            // Creating `implLookup` is slow
            implLookup.ctx.fullyNormalizeAssociatedTypesIn(rawType)
        } else {
            rawType
        }
    }

fun RsTypeReference.normType(implLookup: ImplLookup): Ty =
    normType(implLookup.ctx)

fun RsTypeReference.normType(ctx: RsInferenceContext): Ty =
    ctx.fullyNormalizeAssociatedTypesIn(rawType)

val RsTypeReference.lifetimeElidable: Boolean
    get() {
        val typeOwner = owner.parent

        val isAssociatedConstant = typeOwner is RsConstant && typeOwner.owner.isImplOrTrait

        return typeOwner !is RsNamedFieldDecl && typeOwner !is RsTupleFieldDecl
            && typeOwner !is RsTypeAlias && !isAssociatedConstant
    }

private val TYPE_INFERENCE_KEY: Key<CachedValue<RsInferenceResult>> = Key.create("TYPE_INFERENCE_KEY")

val RsInferenceContextOwner.selfInferenceResult: RsInferenceResult
    get() {
        if (this is RsPath) {
            val parent = parent
            if (parent != null && !parent.isAllowedPathParent()) {
                return RsInferenceResult.EMPTY
            }
        }
        return CachedValuesManager.getCachedValue(this, TYPE_INFERENCE_KEY) {
            val inferred = inferTypesIn(this)

            createCachedResult(inferred)
        }
    }

val PsiElement.inferenceContextOwner: RsInferenceContextOwner?
    get() = contexts
        .withNext()
        .find { (it, next) ->
            if (it !is RsInferenceContextOwner) return@find false
            it !is RsPath || next != null && next.isAllowedPathParent()
        }?.first as? RsInferenceContextOwner

private fun PsiElement.isAllowedPathParent() =
    this is RsPathType || this is RsTraitRef || this is RsStructLiteral || this is RsAssocTypeBinding || this is RsPath

val PsiElement.inference: RsInferenceResult?
    get() = inferenceContextOwner?.selfInferenceResult

val RsPatBinding.type: Ty
    get() = inference?.getBindingType(this) ?: TyUnknown

val RsPat.type: Ty
    get() = inference?.getPatType(this) ?: TyUnknown

val RsPatField.type: Ty
    get() = inference?.getPatFieldType(this) ?: TyUnknown

val RsExpr.type: Ty
    get() = inference?.getExprType(this) ?: TyUnknown

val RsExpr.adjustedType: Ty
    get() = adjustments.lastOrNull()?.target ?: type

val RsStructLiteralField.type: Ty
    get() = (if (isShorthand) resolveToBinding()?.type else expr?.type) ?: TyUnknown

val RsExpr.adjustments: List<Adjustment>
    get() = inference?.getExprAdjustments(this) ?: emptyList()

val RsStructLiteralField.adjustments: List<Adjustment>
    get() = inference?.getExprAdjustments(this) ?: emptyList()

val RsExpr.expectedType: Ty?
    get() = expectedTypeCoercable?.ty

val RsExpr.expectedTypeCoercable: ExpectedType?
    get() = inference?.getExpectedExprType(this)

val RsExpr.declaration: RsElement?
    get() = when (this) {
        is RsPathExpr -> path.reference?.resolve()
        is RsDotExpr -> expr.declaration
        is RsCallExpr -> expr.declaration
        is RsIndexExpr -> containerExpr.declaration
        is RsStructLiteral -> path.reference?.resolve()
        else -> null
    }

val RsTraitOrImpl.selfType: Ty
    get() = when (this) {
        is RsImplItem -> typeReference?.rawType ?: TyUnknown
        is RsTraitItem -> TyTypeParameter.self(this)
        else -> error("Unreachable")
    }

val RsElement.implLookup: ImplLookup
    get() = ImplLookup.relativeTo(this)

val RsElement.implLookupAndKnownItems: Pair<ImplLookup, KnownItems>
    get() = implLookup to knownItems

val RsExpr.cmt: Cmt?
    get() {
        val lookup = implLookup
        val inference = this.inference ?: return null
        return MemoryCategorizationContext(lookup, inference).processExpr(this)
    }

val RsExpr.isMutable: Boolean
    get() = type !is TyUnknown && cmt?.isMutable == true

val RsExpr.isImmutable: Boolean
    get() = type !is TyUnknown && cmt?.isMutable == false

private val BORROW_CHECKER_KEY: Key<CachedValue<BorrowCheckResult>> = Key.create("BORROW_CHECKER_KEY")

val RsInferenceContextOwner.borrowCheckResult: BorrowCheckResult?
    get() = CachedValuesManager.getCachedValue(this, BORROW_CHECKER_KEY) {
        if (!existsAfterExpansion) return@getCachedValue createCachedResult(null)
        val bccx = BorrowCheckContext.buildFor(this)
        val borrowCheckResult = bccx?.check()
        createCachedResult(borrowCheckResult)
    }

private val MIR_BORROW_CHECKER_KEY: Key<CachedValue<MirBorrowCheckResult>> = Key.create("MIR_BORROW_CHECKER_KEY")

val RsInferenceContextOwner.mirBorrowCheckResult: MirBorrowCheckResult?
    get() = CachedValuesManager.getCachedValue(this, MIR_BORROW_CHECKER_KEY) {
        if (!existsAfterExpansion) return@getCachedValue createCachedResult(null)
        val mirBody = mirBody ?: return@getCachedValue createCachedResult(null)
        val result = doMirBorrowCheck(mirBody)
        createCachedResult(result)
    }

fun RsNamedElement?.asTy(): Ty =
    (this as? RsTypeDeclarationElement)?.declaredType ?: TyUnknown

fun RsNamedElement?.asTy(vararg subst: Ty): Ty {
    if (this !is RsTypeDeclarationElement) return TyUnknown
    val declaredType = declaredType
    return if (this is RsGenericDeclaration) {
        declaredType.substitute(withSubst(*subst).subst)
    } else {
        declaredType
    }
}

private val CONTROL_FLOW_KEY: Key<CachedValue<ControlFlowGraph>> = Key.create("CONTROL_FLOW_KEY")

val RsInferenceContextOwner.controlFlowGraph: ControlFlowGraph?
    get() = CachedValuesManager.getCachedValue(this, CONTROL_FLOW_KEY) {
        if (!existsAfterExpansion) return@getCachedValue createCachedResult(null)
        val regionScopeTree = getRegionScopeTree(this)
        val cfg = (body as? RsBlock)?.let { ControlFlowGraph.buildFor(it, regionScopeTree) }
        createCachedResult(cfg)
    }

private val LIVENESS_KEY: Key<CachedValue<Liveness>> = Key.create("LIVENESS_KEY")

val RsInferenceContextOwner.liveness: Liveness?
    get() = CachedValuesManager.getCachedValue(this, LIVENESS_KEY) {
        if (!existsAfterExpansion) return@getCachedValue createCachedResult(null)
        val livenessContext = LivenessContext.buildFor(this)
        val livenessResult = livenessContext?.check()
        createCachedResult(livenessResult)
    }
