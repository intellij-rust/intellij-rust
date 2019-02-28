/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.indexes.RsImplIndex
import org.rust.lang.core.types.*
import org.rust.lang.core.types.infer.*
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.ty.Mutability.IMMUTABLE
import org.rust.lang.core.types.ty.Mutability.MUTABLE
import org.rust.lang.core.types.ty.TyFloat.F32
import org.rust.lang.core.types.ty.TyFloat.F64
import org.rust.lang.core.types.ty.TyInteger.*
import org.rust.openapiext.ProjectCache
import org.rust.openapiext.testAssert
import org.rust.stdext.buildList
import kotlin.LazyThreadSafetyMode.NONE

private val RsTraitItem.typeParamSingle: TyTypeParameter?
    get() =
        typeParameterList?.typeParameterList?.singleOrNull()?.let { TyTypeParameter.named(it) }

const val DEFAULT_RECURSION_LIMIT = 64

// libcore/num/mod.rs (impl_from!)
val HARDCODED_FROM_IMPLS_MAP: Map<TyPrimitive, List<TyPrimitive>> = run {
    val list = listOf(
        // Unsigned -> Unsigned
        U8 to U16,
        U8 to U32,
        U8 to U64,
        U8 to U128,
        U8 to USize,
        U16 to U32,
        U16 to U64,
        U16 to U128,
        U32 to U64,
        U32 to U128,
        U64 to U128,

        // Signed -> Signed
        I8 to I16,
        I8 to I32,
        I8 to I64,
        I8 to I128,
        I8 to ISize,
        I16 to I32,
        I16 to I64,
        I16 to I128,
        I32 to I64,
        I32 to I128,
        I64 to I128,

        // Unsigned -> Signed
        U8 to I16,
        U8 to I32,
        U8 to I64,
        U8 to I128,
        U16 to I32,
        U16 to I64,
        U16 to I128,
        U32 to I64,
        U32 to I128,
        U64 to I128,

        // https://github.com/rust-lang/rust/pull/49305
        U16 to USize,
        U8 to ISize,
        I16 to ISize,

        // Signed -> Float
        I8 to F32,
        I8 to F64,
        I16 to F32,
        I16 to F64,
        I32 to F64,

        // Unsigned -> Float
        U8 to F32,
        U8 to F64,
        U16 to F32,
        U16 to F64,
        U32 to F64,

        // Float -> Float
        F32 to F64
    )
    val map = mutableMapOf<TyPrimitive, MutableList<TyPrimitive>>()
    for ((from, to) in list) {
        map.getOrPut(to) { mutableListOf() }.add(from)
    }
    map
}

sealed class TraitImplSource {
    abstract val value: RsTraitOrImpl

    val impl: RsImplItem?
        get() = (this as? TraitImplSource.ExplicitImpl)?.value

    /** An impl block, directly defined in the code */
    data class ExplicitImpl(override val value: RsImplItem): TraitImplSource()
    /** T: Trait */
    data class TraitBound(override val value: RsTraitItem): TraitImplSource()
    /** Trait is implemented for item via ```#[derive]``` attribute. */
    data class Derived(override val value: RsTraitItem): TraitImplSource()
    /** dyn/impl Trait or a closure */
    data class Object(override val value: RsTraitItem): TraitImplSource()
    /**
     * Used only as a result of method pick. It means that method is resolved to multiple impls of the same trait
     * (with different type parameter values), so we collapsed all impls to that trait. Specific impl
     * will be selected during type inference.
     */
    data class Collapsed(override val value: RsTraitItem): TraitImplSource()
    /** A trait impl hardcoded in Intellij-Rust. Mostly it's something defined with a macro in stdlib */
    data class Hardcoded(override val value: RsTraitItem): TraitImplSource()
}

/**
 * When type checking, we use the `ParamEnv` to track details about the set of where-clauses
 * that are in scope at this particular point.
 * Note: ParamEnv of an associated item (method) also contains bounds of its trait/impl
 * Note: callerBounds should have type `List<Predicate>` to also support lifetime bounds
 */
data class ParamEnv(val callerBounds: List<TraitRef>) {
    fun boundsFor(ty: Ty): Sequence<BoundElement<RsTraitItem>> =
        callerBounds.asSequence().filter { it.selfTy == ty }.map { it.trait }

    fun isEmpty(): Boolean = callerBounds.isEmpty()

    companion object {
        val EMPTY: ParamEnv = ParamEnv(emptyList())
        val LEGACY: ParamEnv = ParamEnv(emptyList())

        fun buildFor(decl: RsGenericDeclaration): ParamEnv = ParamEnv(buildList {
            addAll(decl.bounds)
            if (decl is RsAbstractable) {
                when (val owner = decl.owner) {
                    is RsAbstractableOwner.Trait -> {
                        add(TraitRef(TyTypeParameter.self(), owner.trait.withDefaultSubst()))
                        addAll(owner.trait.bounds)
                    }
                    is RsAbstractableOwner.Impl -> {
                        addAll(owner.impl.bounds)
                    }
                }
            }
        })
    }
}

class ImplLookup(
    private val project: Project,
    private val items: KnownItems,
    private val paramEnv: ParamEnv = ParamEnv.EMPTY
) {
    // Non-concurrent HashMap and lazy(NONE) are safe here because this class isn't shared between threads
    private val primitiveTyHardcodedImplsCache = mutableMapOf<TyPrimitive, Collection<BoundElement<RsTraitItem>>>()
    private val localTraitSelectionCache: MutableMap<TraitRef, SelectionResult<SelectionCandidate>>? =
        if (paramEnv.isEmpty()) null else mutableMapOf()
    private val arithOps by lazy(NONE) {
        ArithmeticOp.values().mapNotNull { it.findTrait(items) }
    }
    private val assignArithOps by lazy(NONE) {
        ArithmeticAssignmentOp.values().mapNotNull { it.findTrait(items) }
    }
    private val fnTraits = listOfNotNull(items.Fn, items.FnMut, items.FnOnce)
    val fnOnceOutput: RsTypeAlias? by lazy(NONE) {
        val trait = items.FnOnce ?: return@lazy null
        trait.findAssociatedType("Output")
    }

    private val derefTraitAndTarget: Pair<RsTraitItem, RsTypeAlias>? = run {
        val trait = items.Deref ?: return@run null
        trait.findAssociatedType("Target")?.let { trait to it }
    }
    private val indexTraitAndOutput: Pair<RsTraitItem, RsTypeAlias>? by lazy(NONE) {
        val trait = items.Index ?: return@lazy null
        trait.findAssociatedType("Output")?.let { trait to it }
    }
    private val intoIteratorTraitAndOutput: Pair<RsTraitItem, RsTypeAlias>? by lazy(NONE) {
        val trait = items.IntoIterator ?: return@lazy null
        trait.findAssociatedType("Item")?.let { trait to it }
    }

    val ctx: RsInferenceContext by lazy(NONE) {
        RsInferenceContext(this, items)
    }

    fun getEnvBoundTransitivelyFor(ty: Ty): Sequence<BoundElement<RsTraitItem>> {
        if (paramEnv == ParamEnv.LEGACY && ty is TyTypeParameter) {
            @Suppress("DEPRECATION")
            return ty.getTraitBoundsTransitively().asSequence()
        }
        return paramEnv.boundsFor(ty).flatMap { it.flattenHierarchy.asSequence() }
    }

    fun findImplsAndTraits(ty: Ty): Set<TraitImplSource> {
        val cached = findImplsAndTraitsCache.getOrPut(project, freshen(ty)) { rawFindImplsAndTraits(ty) }
        return getEnvBoundTransitivelyFor(ty)
            .asIterable().mapTo(cached.toMutableSet()) { TraitImplSource.TraitBound(it.element) }
    }

    private fun rawFindImplsAndTraits(ty: Ty): Set<TraitImplSource> {
        val implsAndTraits = hashSetOf<TraitImplSource>()
        when (ty) {
            is TyTraitObject ->
                ty.trait.flattenHierarchy.mapTo(implsAndTraits) { TraitImplSource.Object(it.element) }
            is TyFunction -> {
                implsAndTraits += findSimpleImpls(ty).map { TraitImplSource.ExplicitImpl(it) }
                implsAndTraits += fnTraits.map { TraitImplSource.Object(it) }
            }
            is TyAnon -> {
                ty.getTraitBoundsTransitively().mapTo(implsAndTraits) { TraitImplSource.Object(it.element) }
                RsImplIndex.findFreeImpls(project)
                    .mapTo(implsAndTraits) { TraitImplSource.ExplicitImpl(it) }
            }
            is TyUnknown -> Unit
            else -> {
                implsAndTraits += findDerivedTraits(ty).map { TraitImplSource.Derived(it) }
                implsAndTraits += findSimpleImpls(ty).map { TraitImplSource.ExplicitImpl(it) }
                getHardcodedImpls(ty).mapTo(implsAndTraits) { TraitImplSource.Hardcoded(it.element) }
            }
        }
        return implsAndTraits
    }

    private fun findDerivedTraits(ty: Ty): Collection<RsTraitItem> {
        return (ty as? TyAdt)?.item?.derivedTraits.orEmpty()
            // select only std traits because we are sure
            // that they are resolved correctly
            .filter { it.isKnownDerivable }
    }

    private fun getHardcodedImpls(ty: Ty): Collection<BoundElement<RsTraitItem>> {
        // TODO this code should be completely removed after macros implementation
        return when (ty) {
            is TyPrimitive -> {
                primitiveTyHardcodedImplsCache.getOrPut(ty) {
                    getHardcodedImplsForPrimitives(ty)
                }
            }
            is TyAdt -> when {
                ty.item == items.findItem("core::slice::Iter") -> {
                    val trait = items.Iterator ?: return emptyList()
                    listOf(trait.substAssocType("Item",
                        TyReference(ty.typeParameterValues.typeByName("T"), IMMUTABLE)))
                }
                ty.item == items.findItem("core::slice::IterMut") -> {
                    val trait = items.Iterator ?: return emptyList()
                    listOf(trait.substAssocType("Item",
                        TyReference(ty.typeParameterValues.typeByName("T"), MUTABLE)))
                }
                else -> emptyList()
            }
            // Can't cache type variables
            is TyInfer.IntVar, is TyInfer.FloatVar -> getHardcodedImplsForPrimitives(ty)
            else -> emptyList()
        }
    }

    private fun getHardcodedImplsForPrimitives(ty: Ty): Collection<BoundElement<RsTraitItem>> {
        val impls = mutableListOf<BoundElement<RsTraitItem>>()

        fun addImpl(trait: RsTraitItem?, vararg subst: Ty) {
            trait?.let { impls += it.withSubst(*subst) }
        }

        if (ty is TyNumeric || ty is TyInfer.IntVar || ty is TyInfer.FloatVar) {
            // libcore/ops/arith.rs libcore/ops/bit.rs
            impls += arithOps.map { it.withSubst(ty).substAssocType("Output", ty) }
            impls += assignArithOps.map { it.withSubst(ty) }
            // Debug (libcore/fmt/num.rs libcore/fmt/float.rs)
            addImpl(items.Debug)
        }
        if (ty is TyInteger || ty is TyInfer.IntVar) {
            // libcore/num/mod.rs
            items.FromStr?.let {
                impls += it.substAssocType("Err", items.findItem<RsStructItem>("core::num::ParseIntError").asTy())
            }

            // libcore/hash/mod.rs
            addImpl(items.Hash)
        }
        HARDCODED_FROM_IMPLS_MAP[ty]?.forEach { from ->
            addImpl(items.From, from)
        }
        if (ty != TyStr) {
            // Default (libcore/default.rs)
            addImpl(items.Default)

            // PatrialEq (libcore/cmp.rs)
            if (ty != TyNever && ty != TyUnit) {
                addImpl(items.PartialEq, ty)
            }

            // Eq (libcore/cmp.rs)
            if (ty !is TyFloat && ty !is TyInfer.FloatVar && ty != TyNever) {
                addImpl(items.Eq)
            }

            // PartialOrd (libcore/cmp.rs)
            if (ty != TyUnit && ty != TyBool && ty != TyNever) {
                addImpl(items.PartialOrd, ty)
                // Ord (libcore/cmp.rs)
                if (ty !is TyFloat && ty !is TyInfer.FloatVar) {
                    addImpl(items.Ord)
                }
            }

            // Clone (libcore/clone.rs)
            addImpl(items.Clone)
            // Copy (libcore/markers.rs)
            addImpl(items.Copy)
        }

        return impls
    }

    private fun findSimpleImpls(selfTy: Ty): Sequence<RsImplItem> {
        return RsImplIndex.findPotentialImpls(project, selfTy).mapNotNull { impl ->
            val subst = impl.generics.associate { it to ctx.typeVarForParam(it) }.toTypeSubst()
            // TODO: take into account the lifetimes (?)
            val formalSelfTy = impl.typeReference?.type?.substitute(subst) ?: return@mapNotNull null
            impl.takeIf { ctx.canCombineTypes(formalSelfTy, selfTy) }
        }
    }

    /**
     * Checks that trait can be successfully selected on any deref level.
     * E.g. for type `&&T` it try to select trait for `&&T`, `&T` and `T` types
     * See [select]
     */
    fun canSelectWithDeref(ref: TraitRef, recursionDepth: Int = 0): Boolean =
        coercionSequence(ref.selfTy)
            .any { canSelect(TraitRef(it, ref.trait), recursionDepth) }

    /** Checks that trait can be successfully selected. See [select] */
    fun canSelect(ref: TraitRef, recursionDepth: Int = 0): Boolean =
        selectStrictWithoutConfirm(ref, recursionDepth).isOk()

    /** Same as [select], but strictly evaluates all obligations (checks trait bounds) of impls */
    fun selectStrict(ref: TraitRef, recursionDepth: Int = 0): SelectionResult<Selection> =
        selectStrictWithoutConfirm(ref, recursionDepth).map { confirmCandidate(ref, it, recursionDepth) }

    private fun selectStrictWithoutConfirm(ref: TraitRef, recursionDepth: Int): SelectionResult<SelectionCandidate> {
        val result = selectWithoutConfirm(ref, recursionDepth)
        val candidate = result.ok() ?: return result.map { error("unreachable") }
        // TODO optimize it. Obligations may be already evaluated, so we don't need to re-evaluated it
        if (!canEvaluateObligations(ref, candidate, recursionDepth)) return SelectionResult.Err()
        return result
    }

    /**
     * If the TraitRef is a something like
     *     `T : Foo<U>`
     * here we select an impl of the trait `Foo<U>` for the type `T`, i.e.
     *     `impl Foo<U> for T {}`
     */
    fun select(ref: TraitRef, recursionDepth: Int = 0): SelectionResult<Selection> =
        selectWithoutConfirm(ref, recursionDepth).map { confirmCandidate(ref, it, recursionDepth) }

    private fun selectWithoutConfirm(ref: TraitRef, recursionDepth: Int): SelectionResult<SelectionCandidate> {
        if (recursionDepth > DEFAULT_RECURSION_LIMIT) return SelectionResult.Err()
        testAssert { !ctx.hasResolvableTypeVars(ref) }
        val freshenRef = freshen(ref)
        @Suppress("IfThenToElvis")
        return if (localTraitSelectionCache != null) {
            // function-local cache is used when [paramEnv] is not empty, i.e. if there are trait bounds
            // that affect trait selection
            localTraitSelectionCache.getOrPut(freshenRef) { selectCandidate(ref, recursionDepth) }
        } else {
            globalTraitSelectionCache.getOrPut(project, freshenRef) { selectCandidate(ref, recursionDepth) }
        }
    }

    private fun selectCandidate(ref: TraitRef, recursionDepth: Int): SelectionResult<SelectionCandidate> {
        if (ref.selfTy is TyReference && ref.selfTy.referenced is TyInfer.TyVar) {
            // This condition is related to TyFingerprint internals: TyFingerprint should not be created for
            // TyInfer.TyVar, and TyReference is a single special case: it unwraps during TyFingerprint creation
            return SelectionResult.Ambiguous()
        }

        val candidates = assembleCandidates(ref)

        return when (candidates.size) {
            0 -> SelectionResult.Err()
            1 -> SelectionResult.Ok(candidates.single())
            else -> {
                val filtered = candidates.filter {
                    canEvaluateObligations(ref, it, recursionDepth)
                }

                when (filtered.size) {
                    0 -> SelectionResult.Err()
                    1 -> SelectionResult.Ok(filtered.single())
                    else -> {
                        // basic specialization
                        filtered.singleOrNull {
                            it !is SelectionCandidate.Impl || it.formalSelfTy !is TyTypeParameter
                        }?.let {
                            TypeInferenceMarks.traitSelectionSpecialization.hit()
                            SelectionResult.Ok(it)
                        } ?: SelectionResult.Ambiguous()
                    }
                }
            }
        }
    }

    /**
     * When we doing an independent cache lookups we want to treat
     * these types as the same (A & B is type variables)
     * ` S<A, B, A> == S<B, A, B>`
     */
    private fun <T : TypeFoldable<T>> freshen(ty: T): T {
        var counter = 0
        val map = HashMap<TyInfer, FreshTyInfer>()

        return ty.foldTyInferWith {
            map.getOrPut(it) {
                when (it) {
                    is TyInfer.TyVar -> FreshTyInfer.TyVar(counter++)
                    is TyInfer.IntVar -> FreshTyInfer.IntVar(counter++)
                    is TyInfer.FloatVar -> FreshTyInfer.FloatVar(counter++)
                }
            }
        }
    }

    private fun canEvaluateObligations(ref: TraitRef, candidate: SelectionCandidate, recursionDepth: Int): Boolean {
        return ctx.probe {
            val obligation = confirmCandidate(ref, candidate, recursionDepth).nestedObligations
            val ff = FulfillmentContext(ctx, this)
            obligation.forEach(ff::registerPredicateObligation)
            ff.selectUntilError()
        }
    }

    private fun assembleCandidates(ref: TraitRef): List<SelectionCandidate> {
        val element = ref.trait.element
        return when {
            // The `Sized` trait is hardcoded in the compiler. It cannot be implemented in source code.
            // Trying to do so would result in a E0322.
            element == items.Sized -> sizedTraitCandidates(ref.selfTy, element)
            ref.selfTy is TyAnon -> buildList {
                ref.selfTy.getTraitBoundsTransitively().find { it.element == element }
                    ?.let { add(SelectionCandidate.TraitObject) }
                RsImplIndex.findFreeImpls(project)
                    .assembleImplCandidates(ref)
                    .forEach(::add)
            }
            element.isAuto -> autoTraitCandidates(ref.selfTy, element)
            else -> buildList {
                getEnvBoundTransitivelyFor(ref.selfTy).asSequence()
                    .filter { ctx.probe { ctx.combineBoundElements(it, ref.trait) } }
                    .map { SelectionCandidate.TypeParameter(it) }
                    .forEach(::add)
                if (ref.selfTy is TyTypeParameter) return@buildList
                addAll(assembleImplCandidates(ref))
                addAll(assembleDerivedCandidates(ref))
                if (ref.selfTy is TyFunction && element in fnTraits) add(SelectionCandidate.Closure)
                if (ref.selfTy is TyTraitObject) {
                    ref.selfTy.trait.flattenHierarchy.find { it.element == ref.trait.element }
                        ?.let { add(SelectionCandidate.TraitObject) }
                }
                getHardcodedImpls(ref.selfTy).filter { be ->
                    be.element == element && ctx.probe { ctx.combinePairs(be.subst.zipTypeValues(ref.trait.subst)) }
                }.forEach { add(SelectionCandidate.HardcodedImpl) }
            }
        }
    }

    private fun assembleImplCandidates(ref: TraitRef): List<SelectionCandidate> {
        return RsImplIndex.findPotentialImpls(project, ref.selfTy)
            .assembleImplCandidates(ref)
            .toList()
    }

    private fun Sequence<RsImplItem>.assembleImplCandidates(ref: TraitRef): Sequence<SelectionCandidate> =
        mapNotNull { impl ->
            val formalTraitRef = impl.implementedTrait ?: return@mapNotNull null
            if (formalTraitRef.element != ref.trait.element) return@mapNotNull null
            val formalSelfTy = impl.typeReference?.type ?: return@mapNotNull null
            val (_, implTraitRef) =
                prepareSubstAndTraitRefRaw(ctx, impl.generics, formalSelfTy, formalTraitRef, ref.selfTy)
            if (!ctx.probe { ctx.combineTraitRefs(implTraitRef, ref) }) return@mapNotNull null
            SelectionCandidate.Impl(impl, formalSelfTy, formalTraitRef)
        }

    private fun assembleDerivedCandidates(ref: TraitRef): List<SelectionCandidate> {
        return (ref.selfTy as? TyAdt)?.item?.derivedTraits.orEmpty()
            // select only std traits because we are sure
            // that they are resolved correctly
            .filter { it.isKnownDerivable }
            .filter { it == ref.trait.element }
            .map { SelectionCandidate.DerivedTrait(it) }
    }

    private fun sizedTraitCandidates(ty: Ty, sizedTrait: RsTraitItem): List<SelectionCandidate> {
        if (!ty.isSized()) return emptyList()
        val candidate = if (ty is TyTypeParameter) {
            SelectionCandidate.TypeParameter(sizedTrait.withSubst())
        } else {
            SelectionCandidate.DerivedTrait(sizedTrait)
        }
        return listOf(candidate)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun autoTraitCandidates(ty: Ty, trait: RsTraitItem): List<SelectionCandidate> {
        // FOr now, just think that any type is Sync + Send
        // TODO implement auto trait logic
        return listOf(SelectionCandidate.DerivedTrait(trait))
    }

    private fun confirmCandidate(
        ref: TraitRef,
        candidate: SelectionCandidate,
        recursionDepth: Int
    ): Selection {
        val newRecDepth = recursionDepth + 1
        return when (candidate) {
            is SelectionCandidate.Impl -> {
                testAssert { !candidate.formalSelfTy.containsTyOfClass(TyInfer::class.java) }
                testAssert { !candidate.formalTrait.containsTyOfClass(TyInfer::class.java) }
                val (subst, preparedRef) = candidate.prepareSubstAndTraitRef(ctx, ref.selfTy)
                ctx.combineTraitRefs(ref, preparedRef)
                // pre-resolve type vars to simplify caching of already inferred obligation on fulfillment
                val candidateSubst = subst.mapTypeValues { (_, v) -> ctx.resolveTypeVarsIfPossible(v) } +
                    mapOf(TyTypeParameter.self() to ref.selfTy).toTypeSubst()
                val obligations = ctx.instantiateBounds(candidate.impl.bounds, candidateSubst, newRecDepth).toList()
                Selection(candidate.impl, obligations, candidateSubst)
            }
            is SelectionCandidate.DerivedTrait -> Selection(candidate.item, emptyList())
            is SelectionCandidate.Closure -> {
                // TODO hacks hacks hacks
                val (trait, _, assoc) = ref.trait
                ctx.combineTypes(assoc[fnOnceOutput] ?: TyUnit, (ref.selfTy as TyFunction).retType)
                Selection(trait, emptyList())
            }
            is SelectionCandidate.TypeParameter -> {
                testAssert { !candidate.bound.containsTyOfClass(TyInfer::class.java) }
                ctx.combineBoundElements(candidate.bound, ref.trait)
                Selection(candidate.bound.element, emptyList())
            }
            SelectionCandidate.TraitObject -> {
                val traits = when (ref.selfTy) {
                    is TyTraitObject -> ref.selfTy.trait.flattenHierarchy
                    is TyAnon -> ref.selfTy.getTraitBoundsTransitively()
                    else -> error("unreachable")
                }
                // should be nonnull because already checked in `assembleCandidates`
                val be = traits.find { it.element == ref.trait.element } ?: error("Corrupted trait selection")
                ctx.combineBoundElements(be, ref.trait)
                Selection(be.element, emptyList())
            }
            is SelectionCandidate.HardcodedImpl -> {
                val impl = getHardcodedImpls(ref.selfTy).first { be ->
                    be.element == ref.trait.element && ctx.probe { ctx.combinePairs(be.subst.zipTypeValues(ref.trait.subst)) }
                }
                ctx.combineBoundElements(impl, ref.trait)
                Selection(impl.element, emptyList(), mapOf(TyTypeParameter.self() to ref.selfTy).toTypeSubst())
            }
        }
    }

    fun coercionSequence(baseTy: Ty): Sequence<Ty> {
        val result = mutableSetOf<Ty>()
        return generateSequence(ctx.resolveTypeVarsIfPossible(baseTy)) {
            if (result.add(it)) {
                deref(it)?.let(ctx::resolveTypeVarsIfPossible) ?: (it as? TyArray)?.let { TySlice(it.base) }
            } else {
                null
            }
        }.constrainOnce().take(DEFAULT_RECURSION_LIMIT)
    }

    fun deref(ty: Ty): Ty? = when (ty) {
        is TyReference -> ty.referenced
        is TyPointer -> ty.referenced
        else -> findDerefTarget(ty)?.value // TODO don't ignore obligations
    }

    private fun findDerefTarget(ty: Ty): TyWithObligations<Ty>? {
        return selectProjection(derefTraitAndTarget ?: return null, ty).ok()
    }

    fun findIteratorItemType(ty: Ty): TyWithObligations<Ty>? {
        return selectProjection(intoIteratorTraitAndOutput ?: return null, ty).ok()
    }

    fun findIndexOutputType(containerType: Ty, indexType: Ty): TyWithObligations<Ty>? {
        return selectProjection(indexTraitAndOutput ?: return null, containerType, indexType).ok()
    }

    fun findArithmeticBinaryExprOutputType(lhsType: Ty, rhsType: Ty, op: ArithmeticOp): TyWithObligations<Ty>? {
        val trait = op.findTrait(items) ?: return null
        val assocType = trait.findAssociatedType("Output") ?: return null
        return selectProjection(TraitRef(lhsType, trait.withSubst(rhsType)), assocType).ok()
    }

    private fun selectProjection(
        traitAndOutput: Pair<RsTraitItem, RsTypeAlias>,
        selfTy: Ty,
        vararg subst: Ty
    ): SelectionResult<TyWithObligations<Ty>?> {
        val (trait, assocType) = traitAndOutput
        return selectProjection(TraitRef(selfTy, trait.withSubst(*subst)), assocType)
    }

    fun selectProjection(
        ref: TraitRef,
        assocType: RsTypeAlias,
        recursionDepth: Int = 0
    ): SelectionResult<TyWithObligations<Ty>?> {
        return select(ref, recursionDepth).map {
            lookupAssociatedType(ref.selfTy, it, assocType)
                ?.let { ctx.normalizeAssociatedTypesIn(it, recursionDepth) }
                ?.withObligations(it.nestedObligations)
        }
    }

    fun selectProjection(
        projectionTy: TyProjection,
        recursionDepth: Int = 0
    ): SelectionResult<TyWithObligations<Ty>?> = selectProjection(
        projectionTy.traitRef,
        projectionTy.target,
        recursionDepth
    )

    fun selectProjectionStrict(
        ref: TraitRef,
        assocType: RsTypeAlias,
        recursionDepth: Int = 0
    ): SelectionResult<TyWithObligations<Ty>?> {
        return selectStrict(ref, recursionDepth).map {
            lookupAssociatedType(ref.selfTy, it, assocType)
                ?.let { ctx.normalizeAssociatedTypesIn(it, recursionDepth) }
                ?.withObligations(it.nestedObligations)
        }
    }

    fun selectProjectionStrictWithDeref(
        ref: TraitRef,
        assocType: RsTypeAlias,
        recursionDepth: Int = 0
    ): SelectionResult<TyWithObligations<Ty>?> =
        coercionSequence(ref.selfTy)
            .map { selectProjectionStrict(TraitRef(it, ref.trait), assocType, recursionDepth) }
            .firstOrNull { it.isOk() }
            ?: SelectionResult.Err()

    private fun lookupAssociatedType(selfTy: Ty, res: Selection, assocType: RsTypeAlias): Ty? {
        return when (selfTy) {
            is TyTypeParameter -> lookupAssocTypeInBounds(getEnvBoundTransitivelyFor(selfTy), res.impl, assocType)
            is TyTraitObject -> selfTy.trait.assoc[assocType]
            else -> {
                lookupAssocTypeInSelection(res, assocType)
                    ?: lookupAssocTypeInBounds(getHardcodedImpls(selfTy).asSequence(), res.impl, assocType)
                    ?: (selfTy as? TyAnon)?.let { lookupAssocTypeInBounds(it.getTraitBoundsTransitively().asSequence(), res.impl, assocType) }
            }
        }
    }

    private fun lookupAssocTypeInSelection(selection: Selection, assoc: RsTypeAlias): Ty? =
        selection.impl.associatedTypesTransitively.find { it.name == assoc.name }?.typeReference?.type?.substitute(selection.subst)

    fun lookupAssocTypeInBounds(
        subst: Sequence<BoundElement<RsTraitItem>>,
        trait: RsTraitOrImpl,
        assocType: RsTypeAlias
    ): Ty? {
        return subst
            .find { it.element == trait }
            ?.assoc
            ?.get(assocType)
    }

    fun selectOverloadedOp(lhsType: Ty, rhsType: Ty, op: OverloadableBinaryOperator): SelectionResult<Selection> {
        val trait = op.findTrait(items) ?: return SelectionResult.Err()
        return select(TraitRef(lhsType, trait.withSubst(rhsType)))
    }

    fun findOverloadedOpImpl(lhsType: Ty, rhsType: Ty, op: OverloadableBinaryOperator): RsTraitOrImpl? =
        selectOverloadedOp(lhsType, rhsType, op).ok()?.impl

    fun asTyFunction(ty: Ty): TyWithObligations<TyFunction>? {
        return (ty as? TyFunction)?.withObligations() ?: run {
            val output = fnOnceOutput ?: return@run null

            val inputArgVar = TyInfer.TyVar()
            val ok = fnTraits.asSequence()
                .mapNotNull { selectProjection(it to output, ty, inputArgVar).ok() }
                .firstOrNull() ?: return@run null
            TyWithObligations(
                TyFunction((ctx.shallowResolve(inputArgVar) as? TyTuple)?.types.orEmpty(), ok.value),
                ok.obligations
            )
        }
    }

    fun asTyFunction(ref: BoundElement<RsTraitItem>): TyFunction? {
        return ref.asFunctionType
    }

    fun isCopy(ty: Ty): Boolean = ty.isTraitImplemented(items.Copy)
    fun isClone(ty: Ty): Boolean = ty.isTraitImplemented(items.Clone)
    fun isSized(ty: Ty): Boolean = ty.isTraitImplemented(items.Sized)
    fun isDebug(ty: Ty): Boolean = ty.isTraitImplemented(items.Debug)
    fun isPartialEq(ty: Ty, rhsType: Ty = ty): Boolean = ty.isTraitImplemented(items.PartialEq, rhsType)
    fun isIntoIterator(ty: Ty): Boolean = ty.isTraitImplemented(items.IntoIterator)

    private fun Ty.isTraitImplemented(trait: RsTraitItem?, vararg subst: Ty): Boolean {
        if (trait == null) return false
        return canSelect(TraitRef(this, trait.withSubst(*subst)))
    }

    private val BoundElement<RsTraitItem>.asFunctionType: TyFunction?
        get() {
            val outputParam = fnOnceOutput ?: return null
            val param = element.typeParamSingle ?: return null
            val argumentTypes = ((subst[param] ?: TyUnknown) as? TyTuple)?.types.orEmpty()
            val outputType = (assoc[outputParam] ?: TyUnit)
            return TyFunction(argumentTypes, outputType)
        }

    companion object {
        fun relativeTo(psi: RsElement): ImplLookup {
            val parentItem = psi.contextOrSelf<RsItemElement>()
            val paramEnv = if (parentItem is RsGenericDeclaration) {
                if (psi.contextOrSelf<RsWherePred>() == null) {
                    ParamEnv.buildFor(parentItem)
                } else {
                    // We should mock ParamEnv here. Otherwise we run into infinite recursion
                    // This is mostly a hack. It should be solved in the future somehow
                    ParamEnv.LEGACY
                }
            } else {
                ParamEnv.EMPTY
            }
            return ImplLookup(psi.project, psi.knownItems, paramEnv)
        }

        private val findImplsAndTraitsCache =
            ProjectCache<Ty, Set<TraitImplSource>>("findImplsAndTraitsCache")

        private val globalTraitSelectionCache =
            ProjectCache<TraitRef, SelectionResult<SelectionCandidate>>("globalTraitSelectionCache")
    }
}

sealed class SelectionResult<out T> {
    class Err<out T> : SelectionResult<T>()
    class Ambiguous<out T> : SelectionResult<T>()
    data class Ok<out T>(
        val result: T
    ) : SelectionResult<T>()

    fun ok(): T? = (this as? Ok<T>)?.result

    fun isOk(): Boolean = this is Ok<T>

    inline fun <R> map(action: (T) -> R): SelectionResult<R> = when (this) {
        is SelectionResult.Err -> SelectionResult.Err()
        is SelectionResult.Ambiguous -> SelectionResult.Ambiguous()
        is SelectionResult.Ok -> SelectionResult.Ok(action(result))
    }
}

data class Selection(
    val impl: RsTraitOrImpl,
    val nestedObligations: List<Obligation>,
    val subst: Substitution = emptySubstitution
)

private sealed class SelectionCandidate {
    /**
     * ```
     * impl<A, B> Foo<A> for Bar<B> {}
     * |   |      |          |
     * |   |      |          formalSelfTy
     * |   |      formalTrait
     * |   subst
     * impl
     * ```
     */
    data class Impl(
        val impl: RsImplItem,
        // We can always extract these values from impl, but it's better to cache them
        val formalSelfTy: Ty,
        val formalTrait: BoundElement<RsTraitItem>
    ) : SelectionCandidate() {
        fun prepareSubstAndTraitRef(ctx: RsInferenceContext, selfTy: Ty): Pair<Substitution, TraitRef> =
            prepareSubstAndTraitRefRaw(ctx, impl.generics, formalSelfTy, formalTrait, selfTy)
    }

    data class DerivedTrait(val item: RsTraitItem) : SelectionCandidate()
    data class TypeParameter(val bound: BoundElement<RsTraitItem>) : SelectionCandidate()
    object TraitObject : SelectionCandidate()
    /** @see ImplLookup.getHardcodedImpls */
    object HardcodedImpl : SelectionCandidate()
    object Closure : SelectionCandidate()
}

private fun prepareSubstAndTraitRefRaw(
    ctx: RsInferenceContext,
    generics: List<TyTypeParameter>,
    formalSelfTy: Ty,
    formalTrait: BoundElement<RsTraitItem>,
    selfTy: Ty
): Pair<Substitution, TraitRef> {
    val subst = generics.associate { it to ctx.typeVarForParam(it) }.toTypeSubst()
    val boundSubst = formalTrait.substitute(subst).subst.mapTypeValues { (k, v) ->
        if (k == v && k.parameter is TyTypeParameter.Named) {
            // Default type parameter values `trait Tr<T=Foo> {}`
            k.parameter.parameter.typeReference?.type?.substitute(subst) ?: v
        } else {
            v
        }
    }.substituteInValues(mapOf(TyTypeParameter.self() to selfTy).toTypeSubst())
    return subst to TraitRef(formalSelfTy.substitute(subst), BoundElement(formalTrait.element, boundSubst))
}

private fun lookupAssociatedType(impl: RsTraitOrImpl, name: String): Ty {
    return impl.associatedTypesTransitively
        .find { it.name == name }
        ?.let { it.typeReference?.type ?: TyProjection.valueOf(it) }
        ?: TyUnknown
}

private fun <T : Ty> T.withObligations(obligations: List<Obligation> = emptyList()): TyWithObligations<T> =
    TyWithObligations(this, obligations)
