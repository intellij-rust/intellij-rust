/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.rust.lang.core.dfa.Cmt
import org.rust.lang.core.dfa.ControlFlowGraph
import org.rust.lang.core.dfa.MemoryCategorizationContext
import org.rust.lang.core.dfa.borrowck.BorrowCheckContext
import org.rust.lang.core.dfa.borrowck.BorrowCheckResult
import org.rust.lang.core.dfa.liveness.Liveness
import org.rust.lang.core.dfa.liveness.LivenessContext
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


private fun <T> RsInferenceContextOwner.createResult(value: T): Result<T> {
    val structureModificationTracker = project.rustStructureModificationTracker

    return when {
        // The case of injected language. Injected PSI don't have it's own event system, so can only
        // handle evens from outer PSI. For example, Rust language is injected to Kotlin's string
        // literal. If a user change the literal, we can only be notified that the literal is changed.
        // So we have to invalidate the cached value on any PSI change
        containingFile.virtualFile is VirtualFileWindow -> Result.create(value, PsiModificationTracker.MODIFICATION_COUNT)

        // Invalidate cached value of code fragment on any PSI change
        this is RsCodeFragment -> Result.create(value, PsiModificationTracker.MODIFICATION_COUNT)

        // CachedValueProvider.Result can accept a ModificationTracker as a dependency, so the
        // cached value will be invalidated if the modification counter is incremented.
        else -> {
            val modificationTracker = contextOrSelf<RsModificationTrackerOwner>()?.modificationTracker
            Result.create(value, listOfNotNull(structureModificationTracker, modificationTracker))
        }
    }
}

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

val RsInferenceContextOwner.inference: RsInferenceResult
    get() = CachedValuesManager.getCachedValue(this, TYPE_INFERENCE_KEY) {
        val inferred = inferTypesIn(this)

        createResult(inferred)
    }

val PsiElement.inference: RsInferenceResult?
    get() = contextOrSelf<RsInferenceContextOwner>()?.inference

val RsPatBinding.type: Ty
    get() = inference?.getBindingType(this) ?: TyUnknown

val RsPat.type: Ty
    get() = inference?.getPatType(this) ?: TyUnknown

val RsPatField.type: Ty
    get() = inference?.getPatFieldType(this) ?: TyUnknown

val RsExpr.type: Ty
    get() = inference?.getExprType(this) ?: TyUnknown

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
        val bccx = BorrowCheckContext.buildFor(this)
        val borrowCheckResult = bccx?.check()
        createResult(borrowCheckResult)
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
        val regionScopeTree = getRegionScopeTree(this)
        val cfg = (body as? RsBlock)?.let { ControlFlowGraph.buildFor(it, regionScopeTree) }
        createResult(cfg)
    }

private val LIVENESS_KEY: Key<CachedValue<Liveness>> = Key.create("LIVENESS_KEY")

val RsInferenceContextOwner.liveness: Liveness?
    get() = CachedValuesManager.getCachedValue(this, LIVENESS_KEY) {
        val livenessContext = LivenessContext.buildFor(this)
        val livenessResult = livenessContext?.check()
        createResult(livenessResult)
    }
