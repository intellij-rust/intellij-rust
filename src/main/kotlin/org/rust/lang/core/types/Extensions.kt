/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.resolve.knownItems
import org.rust.lang.core.types.borrowck.BorrowCheckContext
import org.rust.lang.core.types.borrowck.BorrowCheckResult
import org.rust.lang.core.types.infer.*
import org.rust.lang.core.types.ty.Mutability
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyTypeParameter
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.openapiext.recursionGuard


private fun <T> RsInferenceContextOwner.createResult(value: T): Result<T> {
    val structureModificationTracker = project.rustStructureModificationTracker

    return when {
        // The case of injected language. Injected PSI don't have it's own event system, so can only
        // handle evens from outer PSI. For example, Rust language is injected to Kotlin's string
        // literal. If a user change the literal, we can only be notified that the literal is changed.
        // So we have to invalidate the cached value on any PSI change
        containingFile.virtualFile is VirtualFileWindow -> Result.create(value, PsiModificationTracker.MODIFICATION_COUNT)

        // CachedValueProvider.Result can accept a ModificationTracker as a dependency, so the
        // cached value will be invalidated if the modification counter is incremented.
        this is RsModificationTrackerOwner -> Result.create(value, structureModificationTracker, modificationTracker)

        else -> Result.create(value, structureModificationTracker)
    }
}

val RsTypeReference.type: Ty
    get() = recursionGuard(this, Computable { inferTypeReferenceType(this) })
        ?: TyUnknown

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

val RsExpr.type: Ty
    get() = inference?.getExprType(this) ?: TyUnknown

val RsExpr.declaration: RsElement?
    get() = when (this) {
        is RsPathExpr -> path.reference.resolve()
        is RsCallExpr -> expr.declaration
        is RsStructLiteral -> path.reference.resolve()
        else -> null
    }

val RsTraitOrImpl.selfType: Ty
    get() = when (this) {
        is RsImplItem -> typeReference?.type ?: TyUnknown
        is RsTraitItem -> TyTypeParameter.self(this)
        else -> error("Unreachable")
    }

val RsExpr.cmt: Cmt?
    get() {
        val items = this.knownItems
        val lookup = ImplLookup(this.project, items)
        val inference = this.inference ?: return null
        return MemoryCategorizationContext(lookup, inference).processExpr(this)
    }

val RsExpr.isMutable: Boolean
    get() = cmt?.isMutable ?: Mutability.DEFAULT_MUTABILITY.isMut

private val BORROW_CHECKER_KEY: Key<CachedValue<BorrowCheckResult>> = Key.create("BORROW_CHECKER_KEY")

val RsInferenceContextOwner.borrowCheckResult: BorrowCheckResult?
    get() = CachedValuesManager.getCachedValue(this, BORROW_CHECKER_KEY) {
        val bccx = BorrowCheckContext.buildFor(this)
        val borrowCheckResult = bccx?.check()
        createResult(borrowCheckResult)
    }

fun RsNamedElement?.asTy(): Ty =
    (this as? RsTypeDeclarationElement)?.declaredType ?: TyUnknown
