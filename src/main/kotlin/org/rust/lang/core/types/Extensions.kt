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
import org.rust.lang.core.cfg.ControlFlowGraph
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.resolve.KnownItems
import org.rust.lang.core.resolve.knownItems
import org.rust.lang.core.types.borrowck.BorrowCheckContext
import org.rust.lang.core.types.borrowck.BorrowCheckResult
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
        this is RsModificationTrackerOwner -> Result.create(value, structureModificationTracker, modificationTracker)

        else -> Result.create(value, structureModificationTracker)
    }
}

val RsTypeReference.type: Ty
    get() = inferTypeReferenceType(this)

val RsTypeElement.lifetimeElidable: Boolean
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

val RsExpr.adjustments: List<Adjustment>
    get() = inference?.getExprAdjustments(this) ?: emptyList()

val RsStructLiteralField.adjustments: List<Adjustment>
    get() = inference?.getExprAdjustments(this) ?: emptyList()

val RsPathExpr.expectedType: Ty?
    get() = inference?.getExpectedPathExprType(this)

val RsDotExpr.expectedType: Ty?
    get() = inference?.getExpectedDotExprType(this)

val RsExpr.declaration: RsElement?
    get() = when (this) {
        is RsPathExpr -> path.reference.resolve()
        is RsDotExpr -> expr.declaration
        is RsCallExpr -> expr.declaration
        is RsIndexExpr -> containerExpr?.declaration
        is RsStructLiteral -> path.reference.resolve()
        else -> null
    }

val RsTraitOrImpl.selfType: Ty
    get() = when (this) {
        is RsImplItem -> typeReference?.type ?: TyUnknown
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
