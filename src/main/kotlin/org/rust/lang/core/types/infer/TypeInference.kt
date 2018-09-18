/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.containers.isNullOrEmpty
import org.jetbrains.annotations.TestOnly
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.psi.ext.UnaryOperator.*
import org.rust.lang.core.resolve.*
import org.rust.lang.core.resolve.indexes.RsLangItemIndex
import org.rust.lang.core.resolve.ref.*
import org.rust.lang.core.stubs.RsStubLiteralType
import org.rust.lang.core.types.*
import org.rust.lang.core.types.infer.ReVarOrigin.AddrOfRegion
import org.rust.lang.core.types.infer.SubRegionOrigin.*
import org.rust.lang.core.types.infer.outlives.FreeRegionMap
import org.rust.lang.core.types.infer.outlives.OutlivesEnvironment
import org.rust.lang.core.types.infer.outlives.RegionObligation
import org.rust.lang.core.types.infer.outlives.RegionObligationCause
import org.rust.lang.core.types.regions.*
import org.rust.lang.core.types.infer.Adjustment.BorrowReference
import org.rust.lang.core.types.infer.Adjustment.Deref
import org.rust.lang.core.types.regions.Region
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.ty.Mutability.IMMUTABLE
import org.rust.lang.core.types.ty.Mutability.MUTABLE
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.snapshot.CombinedSnapshot
import org.rust.lang.utils.snapshot.Snapshot
import org.rust.lang.utils.snapshot.SnapshotList
import org.rust.openapiext.Testmark
import org.rust.openapiext.forEachChild
import org.rust.openapiext.recursionGuard
import org.rust.stdext.notEmptyOrLet
import org.rust.stdext.singleOrFilter
import org.rust.stdext.singleOrLet
import java.util.*

fun inferTypesIn(element: RsInferenceContextOwner): RsInferenceResult {
    val items = element.knownItems
    val lookup = ImplLookup(element.project, items)
    return recursionGuard(element, Computable { lookup.ctx.infer(element) })
        ?: error("Can not run nested type inference")
}

sealed class Adjustment(val target: Ty) {
    class Deref(target: Ty) : Adjustment(target)
    class BorrowReference(
        target: Ty,
        val region: Region? = (target as? TyReference)?.region,
        val mutability: Mutability? = (target as? TyReference)?.mutability
    ) : Adjustment(target)

    class BorrowPointer(target: Ty, val mutability: Mutability) : Adjustment(target)
}

interface RsInferenceData {
    fun getExprAdjustments(expr: RsExpr): List<Adjustment>
    fun getExprType(expr: RsExpr): Ty
    fun getBindingType(binding: RsPatBinding): Ty
    fun getResolvedPaths(expr: RsPathExpr): List<RsElement>
}

/**
 * [RsInferenceResult] is an immutable per-function map
 * from expressions to their types.
 */
class RsInferenceResult(
    private val bindings: Map<RsPatBinding, Ty>,
    private val exprTypes: Map<RsExpr, Ty>,
    private val resolvedPaths: Map<RsPathExpr, List<RsElement>>,
    private val resolvedMethods: Map<RsMethodCall, List<MethodResolveVariant>>,
    private val resolvedFields: Map<RsFieldLookup, List<RsElement>>,
    val diagnostics: List<RsDiagnostic>,
    val adjustments: Map<RsExpr, List<Adjustment>>
) : RsInferenceData {
    private val timestamp: Long = System.nanoTime()

    override fun getExprAdjustments(expr: RsExpr): List<Adjustment> =
        adjustments[expr] ?: emptyList()

    override fun getExprType(expr: RsExpr): Ty =
        exprTypes[expr] ?: TyUnknown

    override fun getBindingType(binding: RsPatBinding): Ty =
        bindings[binding] ?: TyUnknown

    override fun getResolvedPaths(expr: RsPathExpr): List<RsElement> =
        resolvedPaths[expr] ?: emptyList()

    fun getResolvedMethod(call: RsMethodCall): List<MethodResolveVariant> =
        resolvedMethods[call] ?: emptyList()

    fun getResolvedField(call: RsFieldLookup): List<RsElement> =
        resolvedFields[call] ?: emptyList()

    override fun toString(): String =
        "RsInferenceResult(bindings=$bindings, exprTypes=$exprTypes)"

    @TestOnly
    fun isExprTypeInferred(expr: RsExpr): Boolean =
        expr in exprTypes

    @TestOnly
    fun getTimestamp(): Long = timestamp
}

/**
 * A mutable object, which is filled while we walk function body top down.
 */
class RsInferenceContext(
    val lookup: ImplLookup,
    val items: KnownItems
) : RsInferenceData {
    val fulfill: FulfillmentContext = FulfillmentContext(this, lookup)
    private val bindings: MutableMap<RsPatBinding, Ty> = HashMap()
    private val exprTypes: MutableMap<RsExpr, Ty> = HashMap()
    private val resolvedPaths: MutableMap<RsPathExpr, List<RsElement>> = HashMap()
    private val resolvedMethods: MutableMap<RsMethodCall, List<MethodResolveVariant>> = HashMap()
    private val resolvedFields: MutableMap<RsFieldLookup, List<RsElement>> = HashMap()
    private val pathRefinements: MutableList<Pair<RsPathExpr, TraitRef>> = mutableListOf()
    private val methodRefinements: MutableList<Pair<RsMethodCall, TraitRef>> = mutableListOf()
    val diagnostics: MutableList<RsDiagnostic> = mutableListOf()
    val adjustments: MutableMap<RsExpr, MutableList<Adjustment>> = HashMap()

    private val intUnificationTable: UnificationTable<TyInfer.IntVar, TyInteger> = UnificationTable()
    private val floatUnificationTable: UnificationTable<TyInfer.FloatVar, TyFloat> = UnificationTable()
    private val varUnificationTable: UnificationTable<TyInfer.TyVar, Ty> = UnificationTable()
    private val projectionCache: ProjectionCache = ProjectionCache()

    var freeRegionMap: FreeRegionMap? = null
    val regionConstraints: RegionConstraintCollector = RegionConstraintCollector()
    val regionObligations: SnapshotList<Pair<RsElement, RegionObligation>> = SnapshotList()
    private var lexicalRegionResolutions: LexicalRegionResolutions? = null

    fun startSnapshot(): Snapshot = CombinedSnapshot(
        intUnificationTable.startSnapshot(),
        floatUnificationTable.startSnapshot(),
        varUnificationTable.startSnapshot(),
        projectionCache.startSnapshot(),
        regionConstraints.startSnapshot(),
        regionObligations.startSnapshot()
    )

    inline fun <T> probe(action: () -> T): T {
        val snapshot = startSnapshot()
        try {
            return action()
        } finally {
            snapshot.rollback()
        }
    }

    fun infer(element: RsInferenceContextOwner): RsInferenceResult {
        val callerBounds = if (element is RsGenericDeclaration) getOutlivePredicates(element) else emptyList()
        val fctx = if (element is RsFunction) {
            val fctx = RsFnInferenceContext(this, element.returnType, callerBounds, element.block)
            fctx.extractParameterBindings(element)
            element.block?.let { fctx.inferFnBody(it) }
            fctx
        } else {
            val (retTy, expr) = when (element) {
                is RsConstant -> element.typeReference?.type to element.expr
                is RsArrayType -> TyInteger.USize to element.expr
                is RsVariantDiscriminant -> {
                    // A repr attribute like #[repr(u16)] changes the discriminant type of an enum
                    // https://doc.rust-lang.org/nomicon/other-reprs.html#repru-repri
                    val enum = element.ancestorStrict<RsEnumItem>()
                    val reprType = enum?.queryAttributes?.reprAttributes
                        ?.flatMap { it.metaItemArgs?.metaItemList?.asSequence() ?: emptySequence() }
                        ?.mapNotNull { it.name?.let { TyInteger.fromName(it) } }
                        ?.lastOrNull()
                        ?: TyInteger.ISize

                    reprType to element.expr
                }
                else -> error("Type inference is not implemented for PSI element of type " +
                    "`${element.javaClass}` that implement `RsInferenceContextOwner`")
            }
            val fctx = expr?.let { RsFnInferenceContext(this, retTy ?: TyUnknown, callerBounds, it) }
            fctx?.inferLambdaBody(expr)
            fctx
        }

        fulfill.selectWherePossible()

        exprTypes.replaceAll { _, ty -> fullyResolve(ty) }
        bindings.replaceAll { _, ty -> fullyResolve(ty) }

        performPathsRefinement(lookup)

        fctx?.inferRegions(element)
        exprTypes.replaceAll { _, ty -> fullyResolve(ty) }
        bindings.replaceAll { _, ty -> fullyResolve(ty) }

        return RsInferenceResult(
            bindings,
            exprTypes,
            resolvedPaths,
            resolvedMethods,
            resolvedFields,
            diagnostics,
            adjustments
        )
    }

    private fun performPathsRefinement(lookup: ImplLookup) {
        for ((path, traitRef) in pathRefinements) {
            val fnName = resolvedPaths[path]?.firstOrNull()?.let { (it as? RsFunction)?.name }
            lookup.select(resolveTypeVarsIfPossible(traitRef)).ok()
                ?.impl?.members?.functionList?.find { it.name == fnName }
                ?.let { resolvedPaths[path] = listOf(it) }
        }
        for ((call, traitRef) in methodRefinements) {
            val variant = resolvedMethods[call]?.firstOrNull() ?: continue
            val impl = lookup.select(resolveTypeVarsIfPossible(traitRef)).ok()?.impl as? RsImplItem ?: continue
            val fn = impl.members?.functionList?.find { it.name == variant.name } ?: continue
            resolvedMethods[call] = listOf(variant.copy(element = fn, source = TraitImplSource.ExplicitImpl(impl)))
        }
    }

    override fun getExprAdjustments(expr: RsExpr): List<Adjustment> {
        return adjustments[expr] ?: emptyList()
    }

    override fun getExprType(expr: RsExpr): Ty {
        return exprTypes[expr] ?: TyUnknown
    }

    override fun getBindingType(binding: RsPatBinding): Ty {
        return bindings[binding] ?: TyUnknown
    }

    fun getNodeType(node: RsElement): Ty =
        when (node) {
            is RsExpr -> getExprType(node)
            is RsPatBinding -> getBindingType(node)
            else -> TyUnknown
        }

    fun getAdjustments(element: RsElement): List<Adjustment> {
        return (element as? RsExpr)?.let { adjustments[it] } ?: emptyList()
    }

    /** Returns the type of [expr], considering any [Adjustment] entry recorded for that expression. */
    fun getExprTypeAdjusted(expr: RsExpr): Ty {
        return getAdjustments(expr).lastOrNull()?.target ?: getExprType(expr)
    }

    override fun getResolvedPaths(expr: RsPathExpr): List<RsElement> {
        return resolvedPaths[expr] ?: emptyList()
    }

    fun isTypeInferred(expr: RsExpr): Boolean {
        return exprTypes.containsKey(expr)
    }

    fun writeExprTy(psi: RsExpr, ty: Ty) {
        exprTypes[psi] = ty
    }

    fun writeBindingTy(psi: RsPatBinding, ty: Ty) {
        bindings[psi] = ty
    }

    fun writePath(path: RsPathExpr, resolved: List<BoundElement<RsElement>>) {
        resolvedPaths[path] = resolved.map { it.element }
    }

    fun writeResolvedMethod(call: RsMethodCall, resolvedTo: List<MethodResolveVariant>) {
        resolvedMethods[call] = resolvedTo
    }

    fun writeResolvedField(lookup: RsFieldLookup, resolvedTo: List<RsElement>) {
        resolvedFields[lookup] = resolvedTo
    }

    fun registerPathRefinement(path: RsPathExpr, traitRef: TraitRef) {
        pathRefinements.add(Pair(path, traitRef))
    }

    fun registerMethodRefinement(path: RsMethodCall, traitRef: TraitRef) {
        methodRefinements.add(Pair(path, traitRef))
    }

    fun addDiagnostic(diagnostic: RsDiagnostic) {
        if (diagnostic.element.containingFile.isPhysical) {
            diagnostics.add(diagnostic)
        }
    }

    fun addAdjustment(expression: RsExpr, adjustment: Adjustment, count: Int = 1) {
        repeat(count) {
            adjustments.getOrPut(expression) { mutableListOf() }.add(adjustment)
        }
    }

    fun addGiven(sub: Region, sup: ReVar) {
        regionConstraints.addGiven(sub, sup)
    }

    fun makeSubRegion(origin: SubRegionOrigin, sub: Region, sup: Region) {
        regionConstraints.makeSubRegion(origin, sub, sup)
    }

    fun handleRegionOutlivesPredicate(cause: RegionObligationCause, predicate: Predicate.RegionOutlives) {
        val origin = SubRegionOrigin.fromObligationCause(cause) { RelateRegionParamBound(cause.element) }
        makeSubRegion(origin, predicate.sub, predicate.sup)  // `b: a` ==> `a <= b`
    }

    fun verifyGenericBound(origin: SubRegionOrigin, kind: GenericKind, region: Region, bound: VerifyBound) {
        regionConstraints.verifyGenericBound(origin, kind, region, bound)
    }

    fun reportTypeMismatch(expr: RsExpr, expected: Ty, actual: Ty) {
        addDiagnostic(RsDiagnostic.TypeError(expr, expected, actual))
    }

    fun reportRegionMismatch(element: RsElement) {
        addDiagnostic(RsDiagnostic.RegionError(element))
    }

    fun reportOutliveError(element: RsElement) {
        addDiagnostic(RsDiagnostic.OutliveError(element))
    }

    fun canCombineTypes(ty1: Ty, ty2: Ty): Boolean {
        return probe { combineTypesResolved(shallowResolve(ty1), shallowResolve(ty2)) }
    }

    fun combineTypesIfOk(ty1: Ty, ty2: Ty): Boolean {
        return combineTypesIfOkResolved(shallowResolve(ty1), shallowResolve(ty2))
    }

    private fun combineTypesIfOkResolved(ty1: Ty, ty2: Ty): Boolean {
        val snapshot = startSnapshot()
        val res = combineTypesResolved(ty1, ty2)
        if (res) {
            snapshot.commit()
        } else {
            snapshot.rollback()
        }
        return res
    }

    fun combineTypes(ty1: Ty, ty2: Ty): Boolean {
        return combineTypesResolved(shallowResolve(ty1), shallowResolve(ty2))
    }

    private fun combineTypesResolved(ty1: Ty, ty2: Ty): Boolean {
        return when {
            ty1 is TyInfer.TyVar -> combineTyVar(ty1, ty2)
            ty2 is TyInfer.TyVar -> combineTyVar(ty2, ty1)
            else -> when {
                ty1 is TyInfer -> combineIntOrFloatVar(ty1, ty2)
                ty2 is TyInfer -> combineIntOrFloatVar(ty2, ty1)
                else -> combineTypesNoVars(ty1, ty2)
            }
        }
    }

    private fun combineTyVar(ty1: TyInfer.TyVar, ty2: Ty): Boolean {
        when (ty2) {
            is TyInfer.TyVar -> varUnificationTable.unifyVarVar(ty1, ty2)
            else -> {
                val ty1r = varUnificationTable.findRoot(ty1)
                val isTy2ContainsTy1 = ty2.visitWith(object : TypeVisitor {
                    override fun visitTy(ty: Ty): Boolean = when {
                        ty is TyInfer.TyVar && varUnificationTable.findRoot(ty) == ty1r -> true
                        ty.hasTyInfer -> ty.superVisitWith(this)
                        else -> false
                    }
                })
                if (isTy2ContainsTy1) {
                    // "E0308 cyclic type of infinite size"
                    TypeInferenceMarks.cyclicType.hit()
                    varUnificationTable.unifyVarValue(ty1r, TyUnknown)
                } else {
                    varUnificationTable.unifyVarValue(ty1r, ty2)
                }
            }
        }
        return true
    }

    private fun combineIntOrFloatVar(ty1: TyInfer, ty2: Ty): Boolean {
        when (ty1) {
            is TyInfer.IntVar -> when (ty2) {
                is TyInfer.IntVar -> intUnificationTable.unifyVarVar(ty1, ty2)
                is TyInteger -> intUnificationTable.unifyVarValue(ty1, ty2)
                else -> return false
            }
            is TyInfer.FloatVar -> when (ty2) {
                is TyInfer.FloatVar -> floatUnificationTable.unifyVarVar(ty1, ty2)
                is TyFloat -> floatUnificationTable.unifyVarValue(ty1, ty2)
                else -> return false
            }
            is TyInfer.TyVar -> error("unreachable")
        }
        return true
    }

    private fun combineTypesNoVars(ty1: Ty, ty2: Ty): Boolean {
        return ty1 === ty2 || when {
            ty1 is TyPrimitive && ty2 is TyPrimitive && ty1 == ty2 -> true
            ty1 is TyTypeParameter && ty2 is TyTypeParameter && ty1 == ty2 -> true
            ty1 is TyReference && ty2 is TyReference && ty1.mutability == ty2.mutability -> {
                combineTypes(ty1.referenced, ty2.referenced)
            }
            ty1 is TyPointer && ty2 is TyPointer && ty1.mutability == ty2.mutability -> {
                combineTypes(ty1.referenced, ty2.referenced)
            }
            ty1 is TyArray && ty2 is TyArray &&
                (ty1.size == null || ty2.size == null || ty1.size == ty2.size) -> combineTypes(ty1.base, ty2.base)
            ty1 is TySlice && ty2 is TySlice -> combineTypes(ty1.elementType, ty2.elementType)
            ty1 is TyTuple && ty2 is TyTuple && ty1.types.size == ty2.types.size -> {
                combinePairs(ty1.types.zip(ty2.types))
            }
            ty1 is TyFunction && ty2 is TyFunction && ty1.paramTypes.size == ty2.paramTypes.size -> {
                combinePairs(ty1.paramTypes.zip(ty2.paramTypes)) && combineTypes(ty1.retType, ty2.retType)
            }
            ty1 is TyAdt && ty2 is TyAdt && ty1.item == ty2.item -> {
                combinePairs(ty1.typeArguments.zip(ty2.typeArguments))
            }
            ty1 is TyTraitObject && ty2 is TyTraitObject && ty1.trait == ty2.trait -> true
            ty1 is TyAnon && ty2 is TyAnon && ty1.definition == ty2.definition -> true
            ty1 is TyNever || ty2 is TyNever -> true
            else -> false
        }
    }

    fun combinePairs(pairs: List<Pair<Ty, Ty>>): Boolean {
        var canUnify = true
        for ((t1, t2) in pairs) {
            canUnify = combineTypes(t1, t2) && canUnify
        }
        return canUnify
    }

    fun combineTraitRefs(ref1: TraitRef, ref2: TraitRef): Boolean =
        ref1.trait.element == ref2.trait.element &&
            combineTypes(ref1.selfTy, ref2.selfTy) &&
            ref1.trait.subst.zipTypeValues(ref2.trait.subst).all { (a, b) ->
                combineTypes(a, b)
            }

    fun shallowResolve(ty: Ty): Ty {
        if (ty !is TyInfer) return ty

        return when (ty) {
            is TyInfer.IntVar -> intUnificationTable.findValue(ty) ?: ty
            is TyInfer.FloatVar -> floatUnificationTable.findValue(ty) ?: ty
            is TyInfer.TyVar -> varUnificationTable.findValue(ty)?.let(this::shallowResolve) ?: ty
        }
    }

    fun <T : TypeFoldable<T>> resolveTypeVarsIfPossible(ty: T): T {
        return ty.foldTyInferWith(this::shallowResolve)
    }

    private fun fullyResolve(ty: Ty): Ty {
        fun go(ty: Ty): Ty {
            if (ty !is TyInfer) return ty

            return when (ty) {
                is TyInfer.IntVar -> intUnificationTable.findValue(ty) ?: TyInteger.DEFAULT
                is TyInfer.FloatVar -> floatUnificationTable.findValue(ty) ?: TyFloat.DEFAULT
                is TyInfer.TyVar -> varUnificationTable.findValue(ty)?.let(::go) ?: TyUnknown
            }
        }

        return ty.foldTyInferWith(::go)
    }

    fun typeVarForParam(ty: TyTypeParameter): Ty {
        return TyInfer.TyVar(ty)
    }

    /** Deeply normalize projection types. See [normalizeProjectionType] */
    fun <T : TypeFoldable<T>> normalizeAssociatedTypesIn(ty: T, recursionDepth: Int = 0): TyWithObligations<T> {
        val obligations = mutableListOf<Obligation>()
        val normTy = ty.foldTyProjectionWith {
            val normTy = normalizeProjectionType(it, recursionDepth)
            obligations += normTy.obligations
            normTy.value
        }

        return TyWithObligations(normTy, obligations)
    }

    /**
     * Normalize a specific projection like `<T as Trait>::Item`.
     * The result is always a type (and possibly additional obligations).
     * If ambiguity arises, which implies that
     * there are unresolved type variables in the projection, we will
     * substitute a fresh type variable `$X` and generate a new
     * obligation `<T as Trait>::Item == $X` for later.
     */
    private fun normalizeProjectionType(projectionTy: TyProjection, recursionDepth: Int): TyWithObligations<Ty> {
        return optNormalizeProjectionType(projectionTy, recursionDepth) ?: run {
            val tyVar = TyInfer.TyVar(projectionTy)
            val obligation = Obligation(recursionDepth + 1, Predicate.Projection(projectionTy, tyVar))
            TyWithObligations(tyVar, listOf(obligation))
        }
    }

    /**
     * Normalize a specific projection like `<T as Trait>::Item`.
     * The result is always a type (and possibly additional obligations).
     * Returns `null` in the case of ambiguity, which indicates that there
     * are unbound type variables.
     */
    fun optNormalizeProjectionType(projectionTy: TyProjection, recursionDepth: Int): TyWithObligations<Ty>? =
        optNormalizeProjectionTypeResolved(resolveTypeVarsIfPossible(projectionTy) as TyProjection, recursionDepth)

    /** See [optNormalizeProjectionType] */
    private fun optNormalizeProjectionTypeResolved(projectionTy: TyProjection, recursionDepth: Int): TyWithObligations<Ty>? {
        if (projectionTy.type is TyInfer.TyVar) return null

        val cacheResult = projectionCache.tryStart(projectionTy)
        return when (cacheResult) {
            ProjectionCacheEntry.Ambiguous -> {
                // If we found ambiguity the last time, that generally
                // means we will continue to do so until some type in the
                // key changes (and we know it hasn't, because we just
                // fully resolved it).
                // TODO rustc has an exception for closure types here
                null
            }
            ProjectionCacheEntry.InProgress -> {
                // While normalized A::B we are asked to normalize A::B.
                // TODO rustc halts the compilation immediately (panics) here
                TyWithObligations(TyUnknown)
            }
            ProjectionCacheEntry.Error -> {
                // TODO report an error. See rustc's `normalize_to_error`
                TyWithObligations(TyUnknown)
            }
            is ProjectionCacheEntry.NormalizedTy -> {
                var ty = cacheResult.ty
                // If we find the value in the cache, then return it along
                // with the obligations that went along with it. Note
                // that, when using a fulfillment context, these
                // obligations could in principle be ignored: they have
                // already been registered when the cache entry was
                // created (and hence the new ones will quickly be
                // discarded as duplicated). But when doing trait
                // evaluation this is not the case.
                // (See rustc's https://github.com/rust-lang/rust/issues/43132 )
                if (!hasUnresolvedTypeVars(ty.value)) {
                    // Once we have inferred everything we need to know, we
                    // can ignore the `obligations` from that point on.
                    ty = TyWithObligations(ty.value)
                    projectionCache.putTy(projectionTy, ty)
                }
                ty
            }
            null -> {
                val selResult = lookup.selectProjection(projectionTy, recursionDepth)
                when (selResult) {
                    is SelectionResult.Ok -> {
                        val result = selResult.result ?: TyWithObligations(projectionTy)
                        projectionCache.putTy(projectionTy, pruneCacheValueObligations(result))
                        result
                    }
                    is SelectionResult.Err -> {
                        projectionCache.error(projectionTy)
                        // TODO report an error. See rustc's `normalize_to_error`
                        TyWithObligations(TyUnknown)
                    }
                    is SelectionResult.Ambiguous -> {
                        projectionCache.ambiguous(projectionTy)
                        null
                    }
                }
            }
        }
    }

    /**
     * If there are unresolved type variables, then we need to include
     * any subobligations that bind them, at least until those type
     * variables are fully resolved.
     */
    private fun pruneCacheValueObligations(ty: TyWithObligations<Ty>): TyWithObligations<Ty> {
        if (!hasUnresolvedTypeVars(ty.value)) return TyWithObligations(ty.value)

        // I don't completely understand why we leave the only projection
        // predicates here, but here is the comment from rustc about it
        //
        // If we found a `T: Foo<X = U>` predicate, let's check
        // if `U` references any unresolved type
        // variables. In principle, we only care if this
        // projection can help resolve any of the type
        // variables found in `result.value` -- but we just
        // check for any type variables here, for fear of
        // indirect obligations (e.g., we project to `?0`,
        // but we have `T: Foo<X = ?1>` and `?1: Bar<X =
        // ?0>`).
        //
        // We are only interested in `T: Foo<X = U>` predicates, where
        // `U` references one of `unresolved_type_vars`.
        val obligations = ty.obligations
            .filter { it.predicate is Predicate.Projection && hasUnresolvedTypeVars(it.predicate) }

        return TyWithObligations(ty.value, obligations)
    }

    private fun <T : TypeFoldable<T>> hasUnresolvedTypeVars(_ty: T): Boolean = _ty.visitWith(object : TypeVisitor {
        override fun visitTy(ty: Ty): Boolean {
            val resolvedTy = shallowResolve(ty)
            return when {
                resolvedTy is TyInfer -> true
                !resolvedTy.hasTyInfer -> false
                else -> resolvedTy.superVisitWith(this)
            }
        }
    })

    fun <T : TypeFoldable<T>> hasResolvableTypeVars(_ty: T): Boolean {
        return _ty.visitWith(object : TypeVisitor {
            override fun visitTy(ty: Ty): Boolean {
                return when {
                    ty is TyInfer -> ty != shallowResolve(ty)
                    !ty.hasTyInfer -> false
                    else -> ty.superVisitWith(this)
                }
            }
        })
    }

    /** Return true if [ty] was instantiated or unified with another type variable */
    fun isTypeVarAffected(ty: TyInfer.TyVar): Boolean =
        varUnificationTable.findRoot(ty) != ty || varUnificationTable.findValue(ty) != null

    fun instantiateBounds(
        bounds: List<TraitRef>,
        subst: Substitution = emptySubstitution,
        recursionDepth: Int = 0
    ): Sequence<Obligation> {
        return bounds.asSequence()
            .map { it.substitute(subst) }
            .map { normalizeAssociatedTypesIn(it, recursionDepth) }
            .flatMap { it.obligations.asSequence() + Obligation(recursionDepth, Predicate.Trait(it.value)) }
    }

    /** Checks that [selfTy] satisfies all trait bounds of the [impl] */
    fun canEvaluateBounds(impl: RsImplItem, selfTy: Ty): Boolean {
        val ff = FulfillmentContext(this, lookup)
        val subst = impl.paramsToVarsSubst
        return probe {
            instantiateBounds(impl.bounds, subst).forEach(ff::registerPredicateObligation)
            impl.typeReference?.type?.substitute(subst)?.let { combineTypes(selfTy, it) }
            ff.selectUntilError()
        }
    }

    fun getOutlivePredicates(declaration: RsGenericDeclaration): List<Predicate> {
        val body = (declaration as? RsInferenceContextOwner)?.body ?: return emptyList()
        val predicates = mutableListOf<Predicate>()

        for (parameter in declaration.typeParameters) {
            val subTy = TyTypeParameter.named(parameter)
            for (polybound in parameter.bounds) {
                val lifetime = polybound.bound.lifetime ?: continue
                val subRegion = lifetime.resolve()
                if (subRegion is ReUnknown) continue
                val predicate = Predicate.TypeOutlives(subRegion, subTy, body)
                predicates.add(predicate)
            }
        }

        for (parameter in declaration.lifetimeParameters) {
            val supRegion = ReEarlyBound.named(parameter)
            for (bound in parameter.bounds) {
                val subRegion = bound.resolve()
                if (subRegion is ReUnknown) continue
                val predicate = Predicate.RegionOutlives(subRegion, supRegion, body)
                predicates.add(predicate)
            }
        }

        loop@ for (wherePred in declaration.whereClause?.wherePredList.orEmpty()) {
            when {
                wherePred.typeReference != null && wherePred.forLifetimes == null -> {
                    val typeReference = checkNotNull(wherePred.typeReference)
                    val supTy = inferTypeReferenceType(typeReference)
                    val polybounds = checkNotNull(wherePred.typeParamBounds?.polyboundList)
                    for (polybound in polybounds) {
                        if (polybound.forLifetimes != null) continue
                        val lifetime = polybound.bound.lifetime ?: continue
                        val subRegion = lifetime.resolve()
                        if (subRegion is ReUnknown) continue
                        val predicate = Predicate.TypeOutlives(subRegion, supTy, body)
                        predicates.add(predicate)
                    }
                }
                wherePred.lifetime != null -> {
                    val lifetime = wherePred.lifetime ?: continue@loop
                    val supRegion = lifetime.resolve()
                    if (supRegion is ReUnknown) continue@loop
                    val lifetimeBounds = checkNotNull(wherePred.lifetimeParamBounds?.lifetimeList)
                    for (bound in lifetimeBounds) {
                        val subRegion = bound.resolve()
                        if (subRegion is ReUnknown) continue
                        val predicate = Predicate.RegionOutlives(subRegion, supRegion, body)
                        predicates.add(predicate)
                    }
                }
            }
        }

        return predicates
    }

    fun isMethodCall(expr: RsExpr): Boolean {
        when (expr) {
            is RsCallExpr -> {
                val resolved = resolvedPaths[expr.expr]?.firstOrNull()
                val fn = resolved as? RsFunction ?: return false
                return fn.ancestorStrict<RsItemElement>() is RsTraitOrImpl
            }
            is RsDotExpr -> expr.methodCall != null
            is RsBinaryExpr -> {
                val lhsTy = exprTypes[expr.left] ?: return false
                val rhsTy = exprTypes[expr.right] ?: return false
                if ((lhsTy is TyNumeric || lhsTy is TyBool) && (rhsTy is TyNumeric || rhsTy is TyBool)) return false
                val op = (expr.binaryOp as? OverloadableBinaryOperator) ?: return false

                val trait = RsLangItemIndex.findLangItem(expr.project, op.itemName) ?: return false
                return lookup.canSelect(TraitRef(lhsTy, trait.withSubst(rhsTy)))
            }
            is RsUnaryExpr -> {
                val ty = exprTypes[expr] ?: return false
                val opType = expr.operatorType

                val isBuiltin = when (opType) {
                    DEREF -> when (ty) {
                        is TyReference -> true
                        is TyPointer -> true
                        is TyAdt -> {
                            val boxItem = RsLangItemIndex.findBoxItem(expr.project)
                            ty.item === boxItem
                        }
                        else -> false
                    }
                    MINUS -> ty is TyNumeric
                    NOT -> ty is TyBool
                    else -> false
                }
                if (isBuiltin) return false

                val itemName = when (opType) {
                    DEREF -> "deref"
                    MINUS -> "neg"
                    NOT -> "not"
                    else -> return false
                }
                val trait = RsLangItemIndex.findLangItem(expr.project, itemName) ?: return false
                return lookup.canSelect(TraitRef(ty, trait.withSubst(ty)))
            }
            is RsIndexExpr -> {
                val ty = exprTypes[expr] ?: return false
                if (ty is TyArray) return false
                val trait = RsLangItemIndex.findLangItem(expr.project, "index") ?: return false
                return lookup.canSelect(TraitRef(ty, trait.withSubst(ty)))
            }
        }
        return false
    }

    /**
     * Process the region constraints and report any errors that result.
     * After this, no more unification operations should be done - or the compiler will panic - but it is legal to use
     * [resolveTypeVarsIfPossible] as well as [fullyResolve].
     */
    fun resolveRegions(
        context: RsInferenceContextOwner,
        regionScopeTree: ScopeTree,
        outlives: OutlivesEnvironment
    ) {
        check(regionObligations.isEmpty())
        val relations = RegionRelations(context, regionScopeTree, outlives.freeRegionMap)
        val (varInfos, data) = regionConstraints.intoInfosAndData()
        check(lexicalRegionResolutions == null)
        lexicalRegionResolutions = resolveLexicalRegions(relations, varInfos, data)
        val errors = lexicalRegionResolutions?.errors.orEmpty()
        for (error in errors) {
            when (error) {
                is RegionResolutionError.ConcreteFailure -> {
                    if (error.origin is RelateObjectBound) {
                        reportRegionMismatch(error.origin.element)
                    }
                }
                is RegionResolutionError.GenericBoundFailure -> {

                }
                is RegionResolutionError.SubSupConflict -> {
                    if (error.supOrigin is Reborrow) {
                        reportOutliveError(error.supOrigin.element)
                    }
                }
            }
        }
    }

    override fun toString(): String {
        return "RsInferenceContext(bindings=$bindings, exprTypes=$exprTypes)"
    }
}

class RsFnInferenceContext(
    val ctx: RsInferenceContext,
    private val returnTy: Ty,
    val callerBounds: List<Predicate>,
    body: RsElement?
) {
    private val lookup get() = ctx.lookup
    private val items get() = ctx.items
    private val fulfill get() = ctx.fulfill
    val implicitRegionBound: Region? = body?.let { ReScope(Scope.CallSite(it)) }
    private val RsStructLiteralField.type: Ty get() = resolveToDeclaration?.typeReference?.type ?: TyUnknown

    private fun resolveTypeVarsWithObligations(ty: Ty): Ty {
        if (!ty.hasTyInfer) return ty
        val tyRes = ctx.resolveTypeVarsIfPossible(ty)
        if (!tyRes.hasTyInfer) return tyRes
        selectObligationsWherePossible()
        return ctx.resolveTypeVarsIfPossible(tyRes)
    }

    private fun selectObligationsWherePossible() {
        fulfill.selectWherePossible()
    }

    fun inferFnBody(block: RsBlock): Ty =
        block.inferTypeCoercableTo(returnTy)

    fun inferLambdaBody(expr: RsExpr): Ty =
        if (expr is RsBlockExpr) {
            // skipping diverging procession for lambda body
            ctx.writeExprTy(expr, returnTy)
            inferFnBody(expr.block)
        } else {
            expr.inferTypeCoercableTo(returnTy)
        }

    private fun RsBlock.inferTypeCoercableTo(expected: Ty): Ty =
        inferType(expected, true)

    private fun RsBlock.inferType(expected: Ty? = null, coerce: Boolean = false): Ty {
        var isDiverging = false
        for (stmt in stmtList) {
            isDiverging = processStatement(stmt) || isDiverging
        }
        val type = (if (coerce) expr?.inferTypeCoercableTo(expected!!) else expr?.inferType(expected)) ?: TyUnit
        return if (isDiverging) TyNever else type
    }

    // returns true if expr is always diverging
    private fun processStatement(psi: RsStmt): Boolean = when (psi) {
        is RsLetDecl -> {
            val explicitTy = psi.typeReference?.type
                ?.let { normalizeAssociatedTypesIn(it) }
            val expr = psi.expr
            // We need to know type before coercion to correctly identify if expr is always diverging
            // so we can't call `inferTypeCoercableTo` directly here
            val (inferredTy, coercedInferredTy) = if (expr != null) {
                val inferredTy = expr.inferType(explicitTy)
                val coercedTy = if (explicitTy != null && coerce(expr, inferredTy, explicitTy)) {
                    explicitTy
                } else {
                    inferredTy
                }
                inferredTy to coercedTy
            } else {
                TyUnknown to TyInfer.TyVar()
            }
            psi.pat?.extractBindings(explicitTy ?: resolveTypeVarsWithObligations(coercedInferredTy))
            inferredTy == TyNever
        }
        is RsExprStmt -> psi.expr.inferType() == TyNever
        else -> false
    }

    private fun RsExpr.inferType(expected: Ty? = null): Ty {
        ProgressManager.checkCanceled()
        if (ctx.isTypeInferred(this)) error("Trying to infer expression type twice")

        val ty = when (this) {
            is RsPathExpr -> inferPathExprType(this)
            is RsStructLiteral -> inferStructLiteralType(this, expected)
            is RsTupleExpr -> inferRsTupleExprType(this, expected)
            is RsParenExpr -> this.expr.inferType(expected)
            is RsUnitExpr -> TyUnit
            is RsCastExpr -> inferCastExprType(this)
            is RsCallExpr -> inferCallExprType(this, expected)
            is RsDotExpr -> inferDotExprType(this, expected)
            is RsLitExpr -> inferLitExprType(this, expected)
            is RsBlockExpr -> this.block.inferType(expected)
            is RsIfExpr -> inferIfExprType(this, expected)
            is RsLoopExpr -> inferLoopExprType(this)
            is RsWhileExpr -> inferWhileExprType(this)
            is RsForExpr -> inferForExprType(this)
            is RsMatchExpr -> inferMatchExprType(this, expected)
            is RsUnaryExpr -> inferUnaryExprType(this, expected)
            is RsBinaryExpr -> inferBinaryExprType(this)
            is RsTryExpr -> inferTryExprType(this)
            is RsArrayExpr -> inferArrayType(this, expected)
            is RsRangeExpr -> inferRangeType(this)
            is RsIndexExpr -> inferIndexExprType(this)
            is RsMacroExpr -> inferMacroExprType(this)
            is RsLambdaExpr -> inferLambdaExprType(this, expected)
            is RsRetExpr -> inferRetExprType(this)
            is RsBreakExpr -> inferBreakExprType(this)
            is RsContExpr -> TyNever
            else -> TyUnknown
        }

        ctx.writeExprTy(this, ty)
        return ty
    }

    private fun RsExpr.inferTypeCoercableTo(expected: Ty): Ty {
        val inferred = inferType(expected)
        return if (coerce(this, inferred, expected)) expected else inferred
    }

    @JvmName("inferTypeCoercableTo_")
    fun inferTypeCoercableTo(expr: RsExpr, expected: Ty): Ty =
        expr.inferTypeCoercableTo(expected)

    private fun coerce(expr: RsExpr, inferred: Ty, expected: Ty): Boolean {
        return coerceResolved(expr, resolveTypeVarsWithObligations(inferred), resolveTypeVarsWithObligations(expected))
    }

    private fun coerceResolved(expr: RsExpr, inferred: Ty, expected: Ty): Boolean {
        val ok = tryCoerce(inferred, expected)
        if (!ok) {
            // ignoring possible false-positives (it's only basic experimental type checking)
            val ignoredTys = listOf(
                TyUnknown::class.java,
                TyInfer.TyVar::class.java,
                TyTypeParameter::class.java,
                TyProjection::class.java,
                TyTraitObject::class.java,
                TyAnon::class.java
            )

            if (!expected.containsTyOfClass(ignoredTys) && !inferred.containsTyOfClass(ignoredTys)) {
                // another awful hack: check that inner expressions did not annotated as an error
                // to disallow annotation intersections. This should be done in a different way
                if (ctx.diagnostics.all { !expr.isAncestorOf(it.element) }) {
                    ctx.reportTypeMismatch(expr, expected, inferred)
                }
            }
        }
        return ok
    }

    private fun tryCoerce(inferred: Ty, expected: Ty, element: RsElement): Boolean {
        return when {
            // Coerce array to slice
            inferred is TyReference && inferred.referenced is TyArray &&
                expected is TyReference && expected.referenced is TySlice -> {
                ctx.combineTypes(inferred.referenced.base, expected.referenced.elementType)
            }
            // Coerce reference to pointer
            inferred is TyReference && expected is TyPointer &&
                coerceMutability(inferred.mutability, expected.mutability) -> {
                ctx.combineTypes(inferred.referenced, expected.referenced)
            }
            // Coerce mutable pointer to const pointer
            inferred is TyPointer && inferred.mutability.isMut
                && expected is TyPointer && !expected.mutability.isMut -> {
                ctx.combineTypes(inferred.referenced, expected.referenced)
            }
            // Coerce references
            inferred is TyReference && expected is TyReference &&
                coerceMutability(inferred.mutability, expected.mutability) -> {
                coerceReference(inferred, expected, element)
            }
            // TODO trait object unsizing
            else -> ctx.combineTypes(inferred, expected)
        }
    }

    private fun coerceMutability(from: Mutability, to: Mutability): Boolean =
        from == to || from.isMut && !to.isMut

    /**
     * Reborrows `&mut A` to `&mut B` and `&(mut) A` to `&B`.
     * To match `A` with `B`, autoderef will be performed
     */
    private fun coerceReference(inferred: TyReference, expected: TyReference, element: RsElement): Boolean {
        ctx.makeSubRegion(RelateObjectBound(element), expected.region, inferred.region)
        for (derefTy in lookup.coercionSequence(inferred).drop(1)) {
            val derefTyRef = TyReference(derefTy, inferred.mutability, inferred.region)
            if (ctx.combineTypesIfOk(derefTyRef, expected)) return true
        }
        return false
    }

    private fun inferLitExprType(expr: RsLitExpr, expected: Ty?): Ty {
        val stubType = expr.stubType
        return when (stubType) {
            is RsStubLiteralType.Boolean -> TyBool
            is RsStubLiteralType.Char -> if (stubType.isByte) TyInteger.U8 else TyChar
            is RsStubLiteralType.String -> {
                if (stubType.isByte) {
                    TyReference(TyArray(TyInteger.U8, stubType.length), IMMUTABLE, ReStatic)
                } else {
                    TyReference(TyStr, IMMUTABLE, ReStatic)
                }
            }
            is RsStubLiteralType.Integer -> {
                val ty = stubType.kind
                ty ?: when (expected) {
                    is TyInteger -> expected
                    TyChar -> TyInteger.U8
                    is TyPointer, is TyFunction -> TyInteger.USize
                    else -> TyInfer.IntVar()
                }
            }
            is RsStubLiteralType.Float -> {
                val ty = stubType.kind
                ty ?: (expected?.takeIf { it is TyFloat } ?: TyInfer.FloatVar())
            }
            null -> TyUnknown
        }
    }

    private fun inferPathExprType(expr: RsPathExpr): Ty {
        val variants = resolvePath(expr.path, lookup).mapNotNull { it.downcast<RsNamedElement>() }
        ctx.writePath(expr, variants)
        val fnVariants = variants.mapNotNull { it.downcast<RsFunction>() }
        val qualifier = expr.path.path
        if (variants.size > 1 && fnVariants.size == variants.size && qualifier != null) {
            val resolved = collapseToTrait(fnVariants.map { it.element })
            if (resolved != null) {
                val subst = collapseSubst(resolved, fnVariants)
                return instantiatePath(BoundElement(resolved, subst), expr, tryRefinePath = true)
            }
        }
        val first = variants.singleOrNull() ?: return TyUnknown
        return instantiatePath(first, expr, tryRefinePath = variants.size == 1)
    }

    /** This works for `String::from` where multiple impls of `From` trait found for `String` */
    private fun collapseToTrait(elements: List<RsFunction>): RsFunction? {
        if (elements.size <= 1) return null

        val traits = elements.mapNotNull {
            val owner = it.owner
            when (owner) {
                is RsAbstractableOwner.Impl -> owner.impl.traitRef?.resolveToTrait
                is RsAbstractableOwner.Trait -> owner.trait
                else -> null
            }
        }

        if (traits.size == elements.size && traits.toSet().size == 1) {
            val fnName = elements.first().name
            val trait = traits.first()
            return trait.members?.functionList?.find { it.name == fnName } ?: return null
        }

        return null
    }

    /** See test `test type arguments remap on collapse to trait` */
    private fun collapseSubst(parentFn: RsFunction, variants: List<BoundElement<RsFunction>>): Substitution {
        //TODO remap lifetimes
        val collapsed = mutableMapOf<TyTypeParameter, Ty>()
        val generics = parentFn.generics
        for (fn in variants) {
            for ((key, newValue) in generics.zip(fn.positionalTypeArguments)) {
                collapsed.compute(key) { key, oldValue ->
                    if (oldValue == null || oldValue == newValue) newValue else TyInfer.TyVar(key)
                }
            }
        }
        variants.first().subst[TyTypeParameter.self()]?.let { collapsed[TyTypeParameter.self()] = it }
        return collapsed.toTypeSubst()
    }

    private fun instantiatePath(
        boundElement: BoundElement<RsNamedElement>,
        pathExpr: RsPathExpr? = null,
        tryRefinePath: Boolean = false
    ): Ty {
        val (element, subst) = boundElement
        val type = when (element) {
            is RsPatBinding -> ctx.getBindingType(element)
            is RsTypeDeclarationElement -> element.declaredType
            is RsEnumVariant -> element.parentEnum.declaredType
            is RsFunction -> element.typeOfValue
            is RsConstant -> element.typeReference?.type ?: TyUnknown
            is RsSelfParameter -> element.typeOfValue
            else -> return TyUnknown
        }

        val typeParameters = when (element) {
            is RsFunction -> {
                val owner = element.owner
                var (typeParameters, selfTy) = when (owner) {
                    is RsAbstractableOwner.Impl -> {
                        val typeParameters = instantiateBounds(owner.impl)
                        val selfTy = owner.impl.typeReference?.type?.substitute(typeParameters) ?: TyUnknown
                        subst[TyTypeParameter.self()]?.let { ctx.combineTypes(selfTy, it) }
                        typeParameters to selfTy
                    }
                    is RsAbstractableOwner.Trait -> {
                        val typeParameters = instantiateBounds(owner.trait)
                        // UFCS - add predicate `Self : Trait<Args>`
                        val selfTy = subst[TyTypeParameter.self()] ?: ctx.typeVarForParam(TyTypeParameter.self())
                        val newSubst = owner.trait.paramsToVarsSubst
                        val boundTrait = BoundElement(owner.trait, newSubst)
                            .substitute(typeParameters)
                        val traitRef = TraitRef(selfTy, boundTrait)
                        fulfill.registerPredicateObligation(Obligation(Predicate.Trait(traitRef)))
                        if (pathExpr != null && tryRefinePath) ctx.registerPathRefinement(pathExpr, traitRef)
                        typeParameters to selfTy
                    }
                    else -> emptySubstitution to null
                }

                typeParameters = instantiateBounds(element, selfTy, typeParameters)
                typeParameters
            }
            is RsEnumVariant -> instantiateBounds(element.parentEnum)
            is RsGenericDeclaration -> instantiateBounds(element)
            else -> emptySubstitution
        }

        unifySubst(subst, typeParameters)

        val tupleFields = (element as? RsFieldsOwner)?.tupleFields
        return if (tupleFields != null) {
            // Treat tuple constructor as a function
            TyFunction(tupleFields.tupleFieldDeclList.map { it.typeReference.type }, type)
        } else {
            type
        }.substitute(typeParameters).foldWith(associatedTypeNormalizer)
    }

    private fun instantiateBounds(
        element: RsGenericDeclaration,
        selfTy: Ty? = null,
        typeParameters: Substitution = emptySubstitution
    ): Substitution {
        val map = element.paramsToVarsSubst
            .let { if (selfTy != null) it + mapOf(TyTypeParameter.self() to selfTy).toTypeSubst() else it }
            .let { it + typeParameters }
        ctx.instantiateBounds(element.bounds, map).forEach(fulfill::registerPredicateObligation)
        return map
    }

    private fun <T : TypeFoldable<T>> normalizeAssociatedTypesIn(ty: T): T {
        val (normTy, obligations) = ctx.normalizeAssociatedTypesIn(ty)
        obligations.forEach(fulfill::registerPredicateObligation)
        return normTy
    }

    private inner class AssociatedTypeNormalizer : TypeFolder {
        override fun foldTy(ty: Ty): Ty = normalizeAssociatedTypesIn(ty)
    }

    private val associatedTypeNormalizer = AssociatedTypeNormalizer()

    private fun unifySubst(subst1: Substitution, subst2: Substitution) {
        subst1.typeSubst.forEach { (k, v1) ->
            subst2[k]?.let { v2 ->
                if (k != v1 && k != TyTypeParameter.self() && v1 !is TyTypeParameter && v1 !is TyUnknown) {
                    ctx.combineTypes(v2, v1)
                }
            }
        }
        // TODO take into account the lifetimes
    }

    private fun inferStructLiteralType(expr: RsStructLiteral, expected: Ty?): Ty {
        val boundElement = expr.path.reference.advancedDeepResolve()

        if (boundElement == null) {
            for (field in expr.structLiteralBody.structLiteralFieldList) {
                field.expr?.inferType()
            }
            // Handle struct update syntax { ..expression }
            expr.structLiteralBody.expr?.inferType()
            return TyUnknown
        }

        val (element, subst) = boundElement

        val genericDecl: RsGenericDeclaration? = when (element) {
            is RsStructItem -> element
            is RsEnumVariant -> element.parentEnum
            else -> null
        }

        val typeParameters = genericDecl?.let { instantiateBounds(it) } ?: emptySubstitution
        unifySubst(subst, typeParameters)
        if (expected != null) unifySubst(typeParameters, expected.typeParameterValues)

        val type = when (element) {
            is RsStructItem -> element.declaredType
            is RsEnumVariant -> element.parentEnum.declaredType
            else -> TyUnknown
        }.substitute(typeParameters)

        inferStructTypeArguments(expr, typeParameters)

        // Handle struct update syntax { ..expression }
        expr.structLiteralBody.expr?.inferTypeCoercableTo(type)

        return type
    }

    private fun inferStructTypeArguments(literal: RsStructLiteral, typeParameters: Substitution) {
        literal.structLiteralBody.structLiteralFieldList.filterNotNull().forEach { field ->
            val fieldType = field.type.substitute(typeParameters)
            val expr = field.expr

            if (expr != null) {
                expr.inferTypeCoercableTo(fieldType)
            } else {
                // Handle struct field shorthand by looking up the matching declaration in scope.
                RsCodeFragmentFactory(field.project).createPath(field.referenceName, field)?.let { path ->
                    val local = resolvePath(path, lookup).singleOrNull()?.element
                    val ty = (local as? RsPatBinding)?.let { ctx.getBindingType(it) } ?: TyUnknown
                    tryCoerce(ty, fieldType)
                }
            }
        }
    }

    private fun inferRsTupleExprType(expr: RsTupleExpr, expected: Ty?): Ty {
        return TyTuple(inferExprList(expr.exprList, (expected as? TyTuple)?.types))
    }

    private fun inferExprList(exprs: List<RsExpr>, expected: List<Ty>?): List<Ty> {
        val extended = expected.orEmpty().asSequence().infiniteWithTyUnknown()
        return exprs.asSequence().zip(extended).map { (expr, ty) -> expr.inferTypeCoercableTo(ty) }.toList()
    }

    private fun inferCastExprType(expr: RsCastExpr): Ty {
        expr.expr.inferType()
        return expr.typeReference.type
    }

    private fun inferCallExprType(expr: RsCallExpr, expected: Ty?): Ty {
        val callee = expr.expr
        val ty = resolveTypeVarsWithObligations(callee.inferType()) // or error
        // `struct S; S();`
        if (callee is RsPathExpr) {
            ctx.getResolvedPaths(callee).singleOrNull()?.let {
                if (it is RsFieldsOwner && it.namedFields.isEmpty() && it.positionalFields.isEmpty()) {
                    return ty
                }
            }
        }
        val argExprs = expr.valueArgumentList.exprList
        val calleeType = lookup.asTyFunction(ty)?.register() ?: unknownTyFunction(argExprs.size)
        if (expected != null) ctx.combineTypes(expected, calleeType.retType)
        inferArgumentTypes(calleeType.paramTypes, argExprs)
        return calleeType.retType
    }

    private fun inferMethodCallExprType(receiver: Ty, methodCall: RsMethodCall, expected: Ty?): Ty {
        val argExprs = methodCall.valueArgumentList.exprList
        val callee = run {
            val variants = resolveMethodCallReferenceWithReceiverType(lookup, receiver, methodCall)
            val callee = pickSingleMethod(receiver, variants, methodCall)
            // If we failed to resolve ambiguity just write the all possible methods
            val variantsForDisplay = (callee?.let(::listOf) ?: variants)
            ctx.writeResolvedMethod(methodCall, variantsForDisplay)

            callee ?: variants.firstOrNull()
        }
        if (callee == null) {
            val methodType = unknownTyFunction(argExprs.size)
            inferArgumentTypes(methodType.paramTypes, argExprs)
            return methodType.retType
        }

        val impl = callee.source.impl
        var typeParameters = if (impl != null) {
            val typeParameters = instantiateBounds(impl)
            impl.typeReference?.type?.substitute(typeParameters)?.let { ctx.combineTypes(callee.selfTy, it) }
            if (callee.element.owner is RsAbstractableOwner.Trait) {
                impl.traitRef?.resolveToBoundTrait?.substitute(typeParameters)?.subst ?: emptySubstitution
            } else {
                typeParameters
            }
        } else {
            // Method has been resolved to a trait, so we should add a predicate
            // `Self : Trait<Args>` to select args and also refine method path if possible.
            // Method path refinement needed if there are multiple impls of the same trait to the same type
            val trait = (callee.element.owner as RsAbstractableOwner.Trait).trait
            when (callee.selfTy) {
                // All these branches except `else` are optimization, they can be removed without loss of functionality
                is TyTypeParameter -> callee.selfTy.getTraitBoundsTransitively()
                    .find { it.element == trait }?.subst ?: emptySubstitution
                is TyAnon -> callee.selfTy.getTraitBoundsTransitively()
                    .find { it.element == trait }?.subst ?: emptySubstitution
                is TyTraitObject -> callee.selfTy.trait.flattenHierarchy
                    .find { it.element == trait }?.subst ?: emptySubstitution
                else -> {
                    val typeParameters = instantiateBounds(trait)
                    val subst = trait.generics.associateBy { it }.toTypeSubst()
                    val boundTrait = BoundElement(trait, subst).substitute(typeParameters)
                    val traitRef = TraitRef(callee.selfTy, boundTrait)
                    fulfill.registerPredicateObligation(Obligation(Predicate.Trait(traitRef)))
                    ctx.registerMethodRefinement(methodCall, traitRef)
                    typeParameters
                }
            }
        }
        // TODO: borrow adjustments for self parameter
        /*
        if (callee.selfTy is TyReference) {
            val adjustment = BorrowReference( callee.selfTy)
            ctx.addAdjustment(methodCall.receiver, adjustment)
        }
        */

        typeParameters = instantiateBounds(callee.element, callee.selfTy, typeParameters)

        val fnSubst = run {
            val typeArguments = methodCall.typeArgumentList?.typeReferenceList.orEmpty().map { it.type }
            if (typeArguments.isEmpty()) {
                emptySubstitution
            } else {
                val parameters = callee.element.typeParameterList?.typeParameterList.orEmpty()
                    .map { TyTypeParameter.named(it) }
                parameters.zip(typeArguments).toMap().toTypeSubst()
            }
        }

        unifySubst(fnSubst, typeParameters)

        val methodType = (callee.element.typeOfValue)
            .substitute(typeParameters)
            .foldWith(associatedTypeNormalizer) as TyFunction
        if (expected != null) ctx.combineTypes(expected, methodType.retType)
        // drop first element of paramTypes because it's `self` param
        // and it doesn't have value in `methodCall.valueArgumentList.exprList`
        inferArgumentTypes(methodType.paramTypes.drop(1), argExprs)

        return methodType.retType
    }

    private fun pickSingleMethod(receiver: Ty, variants: List<MethodResolveVariant>, methodCall: RsMethodCall): MethodResolveVariant? {
        val filtered = variants.singleOrLet { list ->
            // 1. filter traits that are not imported
            TypeInferenceMarks.methodPickTraitScope.hit()
            val traitToCallee = hashMapOf<RsTraitItem, MutableList<MethodResolveVariant>>()
            val filtered = mutableListOf<MethodResolveVariant>()
            for (callee in list) {
                val trait = callee.source.impl?.traitRef?.resolveToTrait
                if (trait != null) {
                    traitToCallee.getOrPut(trait) { mutableListOf() }.add(callee)
                } else {
                    filtered.add(callee) // inherent impl
                }
            }
            traitToCallee.keys.filterInScope(methodCall).forEach {
                filtered += traitToCallee.getValue(it)
            }
            filtered
        }.singleOrFilter { callee ->
            // 2. Filter methods by trait bounds (try to select all obligations for each impl)
            TypeInferenceMarks.methodPickCheckBounds.hit()
            val impl = callee.source.impl ?: return@singleOrFilter true
            ctx.canEvaluateBounds(impl, callee.selfTy)
        }.singleOrLet { list ->
            // 3. Pick results matching receiver type
            TypeInferenceMarks.methodPickDerefOrder.hit()

            fun pick(ty: Ty): List<MethodResolveVariant> =
                list.filter { it.element.selfParameter?.typeOfValue(it.selfTy) == ty }

            // https://github.com/rust-lang/rust/blob/a646c912/src/librustc_typeck/check/method/probe.rs#L885
            lookup.coercionSequence(receiver).mapNotNull { ty ->
                pick(ty)
                    // TODO do something with lifetimes
                    .notEmptyOrLet { pick(TyReference(ty, IMMUTABLE)) }
                    .notEmptyOrLet { pick(TyReference(ty, MUTABLE)) }
                    .takeIf { it.isNotEmpty() }
            }.firstOrNull() ?: emptyList()
        }

        return when (filtered.size) {
            0 -> null
            1 -> filtered.single()
            else -> {
                // 4. Try to collapse multiple resolved methods of the same trait, e.g.
                // ```rust
                // trait Foo<T> { fn foo(&self, _: T) {} }
                // impl Foo<Bar> for S { fn foo(&self, _: Bar) {} }
                // impl Foo<Baz> for S { fn foo(&self, _: Baz) {} }
                // ```
                // In this case we `filtered` list contains 2 function defined in 2 impls.
                // We want to collapse them to the single function defined in the trait.
                // Specific impl will be selected later according to the method parameter type.
                val first = filtered.first()
                collapseToTrait(filtered.map { it.element })?.let { fn ->
                    TypeInferenceMarks.methodPickCollapseTraits.hit()
                    MethodResolveVariant(
                        first.name,
                        fn,
                        first.selfTy,
                        first.derefCount,
                        TraitImplSource.Collapsed((fn.owner as RsAbstractableOwner.Trait).trait)
                    )
                }
            }
        }
    }

    private fun unknownTyFunction(arity: Int): TyFunction =
        TyFunction(generateSequence { TyUnknown }.take(arity).toList(), TyUnknown)

    private fun inferArgumentTypes(argDefs: List<Ty>, argExprs: List<RsExpr>) {
        // We do this just like rustc, and comments copied from rustc too

        // We do this in a pretty awful way: first we typecheck any arguments
        // that are not closures, then we typecheck the closures. This is so
        // that we have more information about the types of arguments when we
        // typecheck the functions.
        for (checkLambdas in booleanArrayOf(false, true)) {
            // More awful hacks: before we check argument types, try to do
            // an "opportunistic" vtable resolution of any trait bounds on
            // the call. This helps coercions.
            if (checkLambdas) {
                selectObligationsWherePossible()
            }

            // extending argument definitions to be sure that type inference launched for each arg expr
            val argDefsExt = argDefs.asSequence().infiniteWithTyUnknown()
            for ((type, expr) in argDefsExt.zip(argExprs.asSequence().map(::unwrapParenExprs))) {
                val isLambda = expr is RsLambdaExpr
                if (isLambda != checkLambdas) continue

                expr.inferTypeCoercableTo(type)
            }
        }
    }

    private fun inferFieldExprType(receiver: Ty, fieldLookup: RsFieldLookup): Ty {
        val variants = resolveFieldLookupReferenceWithReceiverType(lookup, receiver, fieldLookup)
        ctx.writeResolvedField(fieldLookup, variants.map { it.element })
        val field = variants.firstOrNull()
        if (field == null) {
            for ((index, type) in lookup.coercionSequence(receiver).withIndex()) {
                if (type is TyTuple) {
                    ctx.addAdjustment(fieldLookup.parentDotExpr.expr, Deref(receiver), index)
                    val fieldIndex = fieldLookup.integerLiteral?.text?.toIntOrNull() ?: return TyUnknown
                    return type.types.getOrElse(fieldIndex) { TyUnknown }
                }
            }
            return TyUnknown
        }
        ctx.addAdjustment(fieldLookup.parentDotExpr.expr, Deref(receiver), field.derefCount)

        val fieldElement = field.element

        val raw = when (fieldElement) {
            is RsFieldDecl -> fieldElement.typeReference?.type
            is RsTupleFieldDecl -> fieldElement.typeReference.type
            else -> null
        } ?: TyUnknown
        return raw.substitute(field.selfTy.typeParameterValues)
    }

    private fun inferDotExprType(expr: RsDotExpr, expected: Ty?): Ty {
        val receiver = resolveTypeVarsWithObligations(expr.expr.inferType())
        val methodCall = expr.methodCall
        val fieldLookup = expr.fieldLookup
        return when {
            methodCall != null -> inferMethodCallExprType(receiver, methodCall, expected)
            fieldLookup != null -> inferFieldExprType(receiver, fieldLookup)
            else -> TyUnknown
        }
    }

    private fun inferLoopExprType(expr: RsLoopExpr): Ty {
        expr.block?.inferType()
        val returningTypes = mutableListOf<Ty>()
        val label = expr.labelDecl?.name

        fun collectReturningTypes(element: PsiElement, matchOnlyByLabel: Boolean) {
            element.forEachChild { child ->
                when (child) {
                    is RsBreakExpr -> {
                        collectReturningTypes(child, matchOnlyByLabel)
                        if (!matchOnlyByLabel && child.label == null || child.label?.referenceName == label) {
                            returningTypes += child.expr?.let(ctx::getExprType) ?: TyUnit
                        }
                    }
                    is RsLabeledExpression -> {
                        if (label != null) {
                            collectReturningTypes(child, true)
                        }
                    }
                    else -> collectReturningTypes(child, matchOnlyByLabel)
                }
            }
        }

        collectReturningTypes(expr, false)
        return if (returningTypes.isEmpty()) TyNever else getMoreCompleteType(returningTypes)
    }

    private fun inferForExprType(expr: RsForExpr): Ty {
        val exprTy = resolveTypeVarsWithObligations(expr.expr?.inferType() ?: TyUnknown)
        expr.pat?.extractBindings(lookup.findIteratorItemType(exprTy)?.register() ?: TyUnknown)
        expr.block?.inferType()
        return TyUnit
    }

    private fun inferWhileExprType(expr: RsWhileExpr): Ty {
        expr.condition?.let { it.pat?.extractBindings(it.expr.inferType()) ?: it.expr.inferType(TyBool) }
        expr.block?.inferType()
        return TyUnit
    }

    private fun inferMatchExprType(expr: RsMatchExpr, expected: Ty?): Ty {
        val matchingExprTy = resolveTypeVarsWithObligations(expr.expr?.inferType() ?: TyUnknown)
        val arms = expr.matchBody?.matchArmList.orEmpty()
        for (arm in arms) {
            for (pat in arm.patList) {
                pat.extractBindings(matchingExprTy)
            }
            arm.expr?.inferType(expected)
            arm.matchArmGuard?.expr?.inferType(TyBool)
        }

        return getMoreCompleteType(arms.mapNotNull { it.expr?.let(ctx::getExprType) })
    }

    private fun inferUnaryExprType(expr: RsUnaryExpr, expected: Ty?): Ty {
        val innerExpr = expr.expr ?: return TyUnknown
        return when (expr.operatorType) {
            REF -> inferRefType(innerExpr, expected, IMMUTABLE)
            REF_MUT -> inferRefType(innerExpr, expected, MUTABLE)
            DEREF -> {
                // expectation must NOT be used for deref
                val base = resolveTypeVarsWithObligations(innerExpr.inferType())
                val deref = lookup.deref(base)
                if (deref == null && base != TyUnknown) {
                    ctx.addDiagnostic(RsDiagnostic.DerefError(expr, base))
                }
                deref ?: TyUnknown
            }
            MINUS -> innerExpr.inferType(expected)
            NOT -> innerExpr.inferType(expected)
            BOX -> {
                innerExpr.inferType()
                TyUnknown
            }
        }
    }

    private fun inferRefType(expr: RsExpr, expected: Ty?, mutable: Mutability): Ty {
        val variable = ctx.regionConstraints.createReVar(AddrOfRegion(expr))
        return TyReference(expr.inferType((expected as? TyReference)?.referenced), mutable, variable)
    }

    private fun inferIfExprType(expr: RsIfExpr, expected: Ty?): Ty {
        expr.condition?.let { it.pat?.extractBindings(it.expr.inferType()) ?: it.expr.inferType(TyBool) }
        val blockTys = mutableListOf<Ty?>()
        blockTys.add(expr.block?.inferType(expected))
        val elseBranch = expr.elseBranch
        if (elseBranch != null) {
            blockTys.add(elseBranch.ifExpr?.inferType(expected))
            blockTys.add(elseBranch.block?.inferType(expected))
        }
        return if (expr.elseBranch == null) TyUnit else getMoreCompleteType(blockTys.filterNotNull())
    }

    private fun inferBinaryExprType(expr: RsBinaryExpr): Ty {
        val lhsType = resolveTypeVarsWithObligations(expr.left.inferType())
        val op = expr.operatorType
        return when (op) {
            is BoolOp -> {
                if (op is OverloadableBinaryOperator) {
                    val rhsType = resolveTypeVarsWithObligations(expr.right?.inferType() ?: TyUnknown)

                    run {
                        // TODO replace it via `selectOverloadedOp` and share the code with `AssignmentOp`
                        // branch when cmp ops will become a real lang items in std
                        val trait = items.findItem("core::cmp::${op.traitName}") as? RsTraitItem
                            ?: return@run null

                        val boundTrait = trait.withSubst(rhsType)
                        val selection = lookup.select(TraitRef(lhsType, boundTrait)).ok()

                        if (!isPrimitiveOrInferPrimitive(lhsType)) {
                            val lhsAdjustment = BorrowReference(TyReference(lhsType, IMMUTABLE))
                            ctx.addAdjustment(expr.left, lhsAdjustment)

                            val rhsAdjustment = BorrowReference(TyReference(rhsType, IMMUTABLE))
                            expr.right?.let { ctx.addAdjustment(it, rhsAdjustment) }
                        }

                        selection
                    }?.nestedObligations?.forEach(fulfill::registerPredicateObligation)
                } else {
                    expr.right?.inferTypeCoercableTo(lhsType)
                }
                TyBool
            }
            is ArithmeticOp -> {
                val rhsType = resolveTypeVarsWithObligations(expr.right?.inferType() ?: TyUnknown)
                lookup.findArithmeticBinaryExprOutputType(lhsType, rhsType, op)?.register() ?: TyUnknown
            }
            is AssignmentOp -> {
                if (op is OverloadableBinaryOperator) {
                    val rhsType = resolveTypeVarsWithObligations(expr.right?.inferType() ?: TyUnknown)
                    val selection = lookup.selectOverloadedOp(lhsType, rhsType, op).ok()

                    if (!isPrimitiveOrInferPrimitive(lhsType)) {
                        val lhsAdjustment = BorrowReference(TyReference(lhsType, MUTABLE))
                        ctx.addAdjustment(expr.left, lhsAdjustment)
                    }

                    selection?.nestedObligations?.forEach(fulfill::registerPredicateObligation)
                } else {
                    expr.right?.inferTypeCoercableTo(lhsType)
                }
                TyUnit
            }
        }
    }

    private fun isPrimitiveOrInferPrimitive(lhsType: Ty) =
        lhsType is TyPrimitive || lhsType is TyInfer.IntVar || lhsType is TyInfer.FloatVar

    private fun inferTryExprType(expr: RsTryExpr): Ty =
        inferTryExprOrMacroType(expr.expr, allowOption = true)

    private fun inferTryExprOrMacroType(arg: RsExpr, allowOption: Boolean): Ty {
        val base = arg.inferType() as? TyAdt ?: return TyUnknown
        //TODO: make it work with generic `std::ops::Try` trait
        if (base.item == items.Result || (allowOption && base.item == items.Option)) {
            TypeInferenceMarks.questionOperator.hit()
            return base.typeArguments.firstOrNull() ?: TyUnknown
        }
        return TyUnknown
    }

    private fun inferRangeType(expr: RsRangeExpr): Ty {
        val el = expr.exprList
        val dot2 = expr.dotdot
        val dot3 = expr.dotdotdot ?: expr.dotdoteq

        val (rangeName, indexType) = when {
            dot2 != null && el.size == 0 -> "RangeFull" to null
            dot2 != null && el.size == 1 -> {
                val e = el[0]
                if (e.startOffsetInParent < dot2.startOffsetInParent) {
                    "RangeFrom" to e.inferType()
                } else {
                    "RangeTo" to e.inferType()
                }
            }
            dot2 != null && el.size == 2 -> {
                "Range" to getMoreCompleteType(el[0].inferType(), el[1].inferType())
            }
            dot3 != null && el.size == 1 -> {
                val e = el[0]
                if (e.startOffsetInParent < dot3.startOffsetInParent) {
                    return TyUnknown
                } else {
                    "RangeToInclusive" to e.inferType()
                }
            }
            dot3 != null && el.size == 2 -> {
                "RangeInclusive" to getMoreCompleteType(el[0].inferType(), el[1].inferType())
            }

            else -> error("Unrecognized range expression")
        }

        return items.findRangeTy(rangeName, indexType)
    }

    private fun inferIndexExprType(expr: RsIndexExpr): Ty {
        fun isArrayToSlice(prevType: Ty?, type: Ty?): Boolean =
            prevType is TyArray && type is TySlice

        val containerExpr = expr.containerExpr ?: return TyUnknown
        val indexExpr = expr.indexExpr ?: return TyUnknown

        val containerType = containerExpr.inferType()
        val indexType = ctx.resolveTypeVarsIfPossible(indexExpr.inferType())

        if (indexType is TyReference) {
            ctx.addAdjustment(indexExpr, BorrowReference(indexType)) // TODO
        }

        var derefCount = -1 // starts with -1 because the fist element of the coercion sequence is the type itself
        var prevType: Ty? = null
        var result: Ty = TyUnknown
        for (type in lookup.coercionSequence(containerType)) {
            if (!isArrayToSlice(prevType, type)) derefCount++

            val outputType = lookup.findIndexOutputType(type, indexType)
            if (outputType != null) {
                result = outputType.register()
                break
            }

            prevType = type
        }

        ctx.addAdjustment(containerExpr, Deref(containerType), derefCount)
        return result
    }

    private fun inferMacroExprType(expr: RsMacroExpr): Ty {
        val tryArg = expr.macroCall.tryMacroArgument
        if (tryArg != null) {
            // See RsTryExpr where we handle the ? expression in a similar way
            return inferTryExprOrMacroType(tryArg.expr, allowOption = false)
        }

        inferChildExprsRecursively(expr.macroCall)
        val vecArg = expr.macroCall.vecMacroArgument
        if (vecArg != null) {
            val elementType = if (vecArg.semicolon != null) {
                // vec![value; repeat]
                vecArg.exprList.firstOrNull()?.let { ctx.getExprType(it) } ?: TyUnknown
            } else {
                // vec![value1, value2, value3]
                val elementTypes = vecArg.exprList.map { ctx.getExprType(it) }
                if (elementTypes.isNotEmpty()) getMoreCompleteType(elementTypes) else TyInfer.TyVar()
            }
            return items.findVecForElementTy(elementType)
        }

        val name = expr.macroCall.macroName
        return when {
            "print" in name || "assert" in name -> TyUnit
            name == "format" -> items.String.asTy()
            name == "format_args" -> items.Arguments.asTy()
            name == "env" -> TyReference(TyStr, IMMUTABLE)
            name == "option_env" -> items.findOptionForElementTy(TyReference(TyStr, IMMUTABLE))
            name == "concat" -> TyReference(TyStr, IMMUTABLE)
            name == "line" || name == "column" -> TyInteger.U32
            name == "file" -> TyReference(TyStr, IMMUTABLE)
            name == "stringify" -> TyReference(TyStr, IMMUTABLE)
            name == "include_str" -> TyReference(TyStr, IMMUTABLE)
            name == "include_bytes" -> TyReference(TyArray(TyInteger.U8, null), IMMUTABLE)
            name == "module_path" -> TyReference(TyStr, IMMUTABLE)
            name == "cfg" -> TyBool
            name == "unimplemented" || name == "unreachable" || name == "panic" -> TyNever
            name == "write" || name == "writeln" -> {
                (expr.macroCall.expansion?.singleOrNull() as? RsExpr)?.inferType() ?: TyUnknown
            }
            expr.macroCall.formatMacroArgument != null || expr.macroCall.logMacroArgument != null -> TyUnit

            else -> TyUnknown
        }
    }

    private fun inferChildExprsRecursively(psi: PsiElement) {
        when (psi) {
            is RsExpr -> psi.inferType()
            else -> psi.forEachChild(this::inferChildExprsRecursively)
        }
    }

    private fun inferLambdaExprType(expr: RsLambdaExpr, expected: Ty?): Ty {
        val params = expr.valueParameterList.valueParameterList
        val expectedFnTy = expected
            ?.let(this::deduceLambdaType)
            ?: unknownTyFunction(params.size)
        val extendedArgs = expectedFnTy.paramTypes.asSequence().infiniteWithTyUnknown()
        val paramTypes = extendedArgs.zip(params.asSequence()).map { (expectedArg, actualArg) ->
            val paramTy = actualArg.typeReference?.type ?: expectedArg
            actualArg.pat?.extractBindings(paramTy)
            paramTy
        }.toList()
        val expectedRetTy = expr.retType?.typeReference?.type
            ?: expectedFnTy.retType.takeIf { it != TyUnknown }
        val isFreshRetTy = expectedRetTy == null
        val retTy = expectedRetTy ?: TyInfer.TyVar()

        expr.expr?.let {
            val ctx = RsFnInferenceContext(ctx, retTy, callerBounds, it)
            ctx.inferLambdaBody(it)
        }

        val isDefaultRetTy = isFreshRetTy && retTy is TyInfer.TyVar && !ctx.isTypeVarAffected(retTy)
        return TyFunction(paramTypes, if (isDefaultRetTy) TyUnit else retTy)
    }

    private fun deduceLambdaType(expected: Ty): TyFunction? {
        return when (expected) {
            is TyInfer.TyVar -> {
                fulfill.pendingObligations
                    .mapNotNull { it.obligation.predicate as? Predicate.Trait }
                    .find { it.trait.selfTy == expected }
                    ?.let { lookup.asTyFunction(it.trait.trait) }
            }
            is TyTraitObject -> lookup.asTyFunction(expected.trait)
            is TyFunction -> expected
            else -> null
        }
    }

    private fun inferArrayType(expr: RsArrayExpr, expected: Ty?): Ty {
        val expectedElemTy = when (expected) {
            is TyArray -> expected.base
            is TySlice -> expected.elementType
            else -> null
        }
        val (elementType, size) = if (expr.semicolon != null) {
            // It is "repeat expr", e.g. `[1; 5]`
            val elementType = expectedElemTy
                ?.let { expr.initializer?.inferTypeCoercableTo(expectedElemTy) }
                ?: expr.initializer?.inferType()
                ?: return TySlice(TyUnknown)
            expr.sizeExpr?.inferType(TyInteger.USize)
            val size = calculateArraySize(expr.sizeExpr) { ctx.getResolvedPaths(it).singleOrNull() }
            elementType to size
        } else {
            val elementTypes = expr.arrayElements?.map { it.inferType(expectedElemTy) }
            if (elementTypes.isNullOrEmpty()) return TySlice(TyUnknown)

            // '!!' is safe here because we've just checked that elementTypes isn't null
            val elementType = getMoreCompleteType(elementTypes!!)
            val inferredTy = if (expectedElemTy != null && tryCoerce(elementType, expectedElemTy)) {
                expectedElemTy
            } else {
                elementType
            }
            inferredTy to elementTypes.size.toLong()
        }

        return TyArray(elementType, size)
    }

    private fun inferRetExprType(expr: RsRetExpr): Ty {
        expr.expr?.inferTypeCoercableTo(returnTy)
        return TyNever
    }

    private fun inferBreakExprType(expr: RsBreakExpr): Ty {
        expr.expr?.inferType()
        return TyNever
    }

    // TODO should be replaced with coerceMany
    private fun getMoreCompleteType(types: List<Ty>): Ty {
        if (types.isEmpty()) return TyUnknown
        return types.reduce { acc, ty -> getMoreCompleteType(acc, ty) }
    }

    // TODO should be replaced with coerceMany
    private fun getMoreCompleteType(ty1: Ty, ty2: Ty): Ty = when (ty1) {
        is TyNever -> ty2
        is TyUnknown -> if (ty2 !is TyNever) ty2 else TyUnknown
        else -> {
            ctx.combineTypes(ty1, ty2)
            ty1
        }
    }

    private fun <T> TyWithObligations<T>.register(): T {
        obligations.forEach(fulfill::registerPredicateObligation)
        return value
    }

    fun extractParameterBindings(fn: RsFunction) {
        for (param in fn.valueParameters) {
            param.pat?.extractBindings(param.typeReference?.type ?: TyUnknown)
        }
    }

    private fun RsPat.extractBindings(ty: Ty) {
        extractBindings(this@RsFnInferenceContext, ty)
    }

    fun writeBindingTy(psi: RsPatBinding, ty: Ty): Unit =
        ctx.writeBindingTy(psi, ty)
}

val RsSelfParameter.typeOfValue: Ty
    get() {
        val owner = parentFunction.owner
        val selfType = when (owner) {
            is RsAbstractableOwner.Impl -> owner.impl.selfType
            is RsAbstractableOwner.Trait -> owner.trait.selfType
            else -> return TyUnknown
        }

        return typeOfValue(selfType)
    }

private fun RsSelfParameter.typeOfValue(selfType: Ty): Ty {
    if (isExplicitType) {
        // self: Self, self: &Self, self: &mut Self, self: Box<Self>
        val ty = this.typeReference?.type ?: TyUnknown
        return ty.substitute(mapOf(TyTypeParameter.self() to selfType).toTypeSubst())
    }

    // self, &self, &mut self
    return if (isRef) {
        val region = lifetime?.resolve() ?: ReEarlyBound.implicit(this)
        TyReference(selfType, mutability, region)
    } else {
        selfType
    }

}

val RsFunction.typeOfValue: TyFunction
    get() {
        val paramTypes = mutableListOf<Ty>()

        val self = selfParameter
        if (self != null) {
            paramTypes += self.typeOfValue
        }

        paramTypes += valueParameters.map { it.typeReference?.type ?: TyUnknown }

        return TyFunction(paramTypes, returnType)
    }

val RsGenericDeclaration.generics: List<TyTypeParameter>
    get() = typeParameters.map { TyTypeParameter.named(it) }

val RsGenericDeclaration.paramsToVarsSubst: Substitution
    get() {
        val typeSubst = typeParameters
            .map { TyTypeParameter.named(it) }
            .associate { it to TyInfer.TyVar(it) }
        return Substitution(typeSubst)
    }

val RsGenericDeclaration.bounds: List<TraitRef>
    get() = CachedValuesManager.getCachedValue(this) {
        CachedValueProvider.Result.create(
            doGetBounds(),
            PsiModificationTracker.MODIFICATION_COUNT
        )
    }

private fun RsGenericDeclaration.doGetBounds(): List<TraitRef> {
    val whereBounds = this.whereClause?.wherePredList.orEmpty().asSequence()
        .flatMap {
            val (element, subst) = (it.typeReference?.typeElement as? RsBaseType)?.path?.reference?.advancedResolve()
                ?: return@flatMap emptySequence<TraitRef>()
            val selfTy = ((element as? RsTypeDeclarationElement)?.declaredType)
                ?.substitute(subst)
                ?: return@flatMap emptySequence<TraitRef>()
            it.typeParamBounds?.polyboundList.toTraitRefs(selfTy)
        }

    return (typeParameters.asSequence().flatMap {
        val selfTy = TyTypeParameter.named(it)
        it.typeParamBounds?.polyboundList.toTraitRefs(selfTy)
    } + whereBounds).toList()
}

private fun List<RsPolybound>?.toTraitRefs(selfTy: Ty): Sequence<TraitRef> = orEmpty().asSequence()
    .mapNotNull { it.bound.traitRef?.resolveToBoundTrait }
    .filter { !it.element.isSizedTrait }
    .map { TraitRef(selfTy, it) }

private fun Sequence<Ty>.infiniteWithTyUnknown(): Sequence<Ty> =
    this + generateSequence { TyUnknown }

data class TyWithObligations<out T>(
    val value: T,
    val obligations: List<Obligation> = emptyList()
)

fun <T> TyWithObligations<T>.withObligations(addObligations: List<Obligation>) =
    TyWithObligations(value, obligations + addObligations)

private fun KnownItems.findVecForElementTy(elementTy: Ty): Ty {
    val ty = Vec?.declaredType ?: TyUnknown

    val typeParameter = ty.getTypeParameter("T") ?: return ty
    return ty.substitute(mapOf(typeParameter to elementTy).toTypeSubst())
}

private fun KnownItems.findOptionForElementTy(elementTy: Ty): Ty {
    val ty = Option?.declaredType ?: TyUnknown

    val typeParameter = ty.getTypeParameter("T") ?: return ty
    return ty.substitute(mapOf(typeParameter to elementTy).toTypeSubst())
}

private fun KnownItems.findRangeTy(rangeName: String, indexType: Ty?): Ty {
    val ty = (findItem("core::ops::$rangeName") as? RsTypeDeclarationElement)?.declaredType ?: TyUnknown

    if (indexType == null) return ty

    val typeParameter = ty.getTypeParameter("Idx") ?: return ty
    return ty.substitute(mapOf(typeParameter to indexType).toTypeSubst())
}

object TypeInferenceMarks {
    val cyclicType = Testmark("cyclicType")
    val questionOperator = Testmark("questionOperator")
    val methodPickTraitScope = Testmark("methodPickTraitScope")
    val methodPickCheckBounds = Testmark("methodPickCheckBounds")
    val methodPickDerefOrder = Testmark("methodPickDerefOrder")
    val methodPickCollapseTraits = Testmark("methodPickCollapseTraits")
    val traitSelectionSpecialization = Testmark("traitSelectionSpecialization")
}
