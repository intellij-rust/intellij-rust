/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import com.intellij.openapi.project.Project
import com.intellij.util.SmartList
import gnu.trove.THashMap
import org.rust.cargo.project.model.CargoProject
import org.rust.lang.core.macros.MacroExpansionMode
import org.rust.lang.core.macros.macroExpansionManager
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.indexes.RsImplIndex
import org.rust.lang.core.resolve.indexes.RsTypeAliasIndex
import org.rust.lang.core.types.*
import org.rust.lang.core.types.consts.CtConstParameter
import org.rust.lang.core.types.consts.CtInferVar
import org.rust.lang.core.types.consts.FreshCtInferVar
import org.rust.lang.core.types.infer.*
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.ty.Mutability.IMMUTABLE
import org.rust.lang.core.types.ty.Mutability.MUTABLE
import org.rust.lang.core.types.ty.TyFloat.F32
import org.rust.lang.core.types.ty.TyFloat.F64
import org.rust.lang.core.types.ty.TyInteger.*
import org.rust.lang.utils.CargoProjectCache
import org.rust.openapiext.testAssert
import org.rust.stdext.Cache
import org.rust.stdext.buildList
import kotlin.LazyThreadSafetyMode.NONE
import kotlin.LazyThreadSafetyMode.PUBLICATION

private val RsTraitItem.typeParamSingle: TyTypeParameter?
    get() = typeParameters.singleOrNull()?.let { TyTypeParameter.named(it) }

const val DEFAULT_RECURSION_LIMIT = 128

// libcore/num/mod.rs (impl_from!)
val HARDCODED_FROM_IMPLS_MAP: Map<TyPrimitive, List<TyPrimitive>> = run {
    val list = listOf(
        // Unsigned -> Unsigned
        U8.INSTANCE to U16.INSTANCE,
        U8.INSTANCE to U32.INSTANCE,
        U8.INSTANCE to U64.INSTANCE,
        U8.INSTANCE to U128.INSTANCE,
        U8.INSTANCE to USize.INSTANCE,
        U16.INSTANCE to U32.INSTANCE,
        U16.INSTANCE to U64.INSTANCE,
        U16.INSTANCE to U128.INSTANCE,
        U32.INSTANCE to U64.INSTANCE,
        U32.INSTANCE to U128.INSTANCE,
        U64.INSTANCE to U128.INSTANCE,

        // Signed -> Signed
        I8.INSTANCE to I16.INSTANCE,
        I8.INSTANCE to I32.INSTANCE,
        I8.INSTANCE to I64.INSTANCE,
        I8.INSTANCE to I128.INSTANCE,
        I8.INSTANCE to ISize.INSTANCE,
        I16.INSTANCE to I32.INSTANCE,
        I16.INSTANCE to I64.INSTANCE,
        I16.INSTANCE to I128.INSTANCE,
        I32.INSTANCE to I64.INSTANCE,
        I32.INSTANCE to I128.INSTANCE,
        I64.INSTANCE to I128.INSTANCE,

        // Unsigned -> Signed
        U8.INSTANCE to I16.INSTANCE,
        U8.INSTANCE to I32.INSTANCE,
        U8.INSTANCE to I64.INSTANCE,
        U8.INSTANCE to I128.INSTANCE,
        U16.INSTANCE to I32.INSTANCE,
        U16.INSTANCE to I64.INSTANCE,
        U16.INSTANCE to I128.INSTANCE,
        U32.INSTANCE to I64.INSTANCE,
        U32.INSTANCE to I128.INSTANCE,
        U64.INSTANCE to I128.INSTANCE,

        // https://github.com/rust-lang/rust/pull/49305
        U16.INSTANCE to USize.INSTANCE,
        U8.INSTANCE to ISize.INSTANCE,
        I16.INSTANCE to ISize.INSTANCE,

        // Signed -> Float
        I8.INSTANCE to F32.INSTANCE,
        I8.INSTANCE to F64.INSTANCE,
        I16.INSTANCE to F32.INSTANCE,
        I16.INSTANCE to F64.INSTANCE,
        I32.INSTANCE to F64.INSTANCE,

        // Unsigned -> Float
        U8.INSTANCE to F32.INSTANCE,
        U8.INSTANCE to F64.INSTANCE,
        U16.INSTANCE to F32.INSTANCE,
        U16.INSTANCE to F64.INSTANCE,
        U32.INSTANCE to F64.INSTANCE,

        // Float -> Float
        F32.INSTANCE to F64.INSTANCE
    )
    val map = mutableMapOf<TyPrimitive, MutableList<TyPrimitive>>()
    for ((from, to) in list) {
        map.getOrPut(to) { mutableListOf() }.add(from)
    }
    map
}

sealed class TraitImplSource {
    abstract val value: RsTraitOrImpl

    open val implementedTrait: BoundElement<RsTraitItem>? get() = value.implementedTrait

    /** For `impl T for Foo` returns union of impl members and trait `T` members that are not overridden by the impl */
    open val implAndTraitExpandedMembers: Map<String, List<RsAbstractable>> by lazy(PUBLICATION) {
        val membersMap = THashMap<String, MutableList<RsAbstractable>>()
        for (member in value.members?.expandedMembers.orEmpty()) {
            val name = member.name ?: continue
            membersMap.getOrPut(name) { SmartList() }.add(member)
        }
        membersMap
    }

    open val isInherent: Boolean get() = false

    val requiredTraitInScope: RsTraitItem?
        get() {
            if (isInherent) return null

            return when (this) {
                is ExplicitImpl -> implementedTrait?.element
                else -> value as RsTraitItem
            }
        }

    val impl: RsImplItem?
        get() = (this as? ExplicitImpl)?.value

    /** An impl block, directly defined in the code */
    data class ExplicitImpl(private val cachedImpl: RsCachedImplItem) : TraitImplSource() {
        override val value: RsImplItem get() = cachedImpl.impl
        override val isInherent: Boolean get() = cachedImpl.isInherent
        override val implementedTrait: BoundElement<RsTraitItem>? get() = cachedImpl.implementedTrait
        override val implAndTraitExpandedMembers: Map<String, List<RsAbstractable>>
            get() = cachedImpl.implAndTraitExpandedMembers
        val type: Ty? get() = cachedImpl.typeAndGenerics?.first
    }

    /** T: Trait */
    data class TraitBound(override val value: RsTraitItem, override val isInherent: Boolean) : TraitImplSource()

    /**
     * Like [TraitBound], but this is a bound for an associated type projection defined at the trait
     * of the associated type.
     * ```
     * trait Foo where Self::Assoc: Bar {
     *     type Assoc;            //^ the bound
     * }
     * ```
     * or
     * ```
     * trait Foo {
     *     type Assoc: Bar;
     * }             //^ the bound
     * ```
     */
    data class ProjectionBound(override val value: RsTraitItem) : TraitImplSource()

    /** Trait is implemented for item via ```#[derive]``` attribute. */
    data class Derived(override val value: RsTraitItem) : TraitImplSource()

    /** dyn/impl Trait or a closure */
    data class Object(override val value: RsTraitItem) : TraitImplSource() {
        override val isInherent: Boolean get() = true
    }

    /**
     * Used only as a result of method pick. It means that method is resolved to multiple impls of the same trait
     * (with different type parameter values), so we collapsed all impls to that trait. Specific impl
     * will be selected during type inference.
     */
    data class Collapsed(override val value: RsTraitItem) : TraitImplSource()

    /**
     * A trait is directly referenced in UFCS path `TraitName::foo`, an impl should be selected
     * during type inference
     */
    data class Trait(override val value: RsTraitItem) : TraitImplSource()

    /** A trait impl hardcoded in Intellij-Rust. Mostly it's something defined with a macro in stdlib */
    data class Hardcoded(override val value: RsTraitItem) : TraitImplSource()
}

/**
 * When type checking, we use the `ParamEnv` to track details about the set of where-clauses
 * that are in scope at this particular point.
 * Note: ParamEnv of an associated item (method) also contains bounds of its trait/impl
 * Note: callerBounds should have type `List<Predicate>` to also support lifetime bounds
 */
data class ParamEnv(val callerBounds: List<TraitRef>) {
    fun boundsFor(ty: Ty): Sequence<BoundElement<RsTraitItem>> =
        callerBounds.asSequence().filter { it.selfTy.isEquivalentTo(ty) }.map { it.trait }

    fun isEmpty(): Boolean = callerBounds.isEmpty()

    companion object {
        val EMPTY: ParamEnv = ParamEnv(emptyList())
        val LEGACY: ParamEnv = ParamEnv(emptyList())

        fun buildFor(decl: RsGenericDeclaration): ParamEnv {
            val rawBounds = buildList<TraitRef> {
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
                        else -> Unit
                    }
                }
            }

            when (rawBounds.size) {
                0 -> return EMPTY
                1 -> return ParamEnv(rawBounds)
            }

            val lookup = ImplLookup(decl.project, decl.cargoProject, decl.knownItems, ParamEnv(rawBounds))
            val ctx = lookup.ctx
            val bounds2 = rawBounds.map {
                val (bound, obligations) = ctx.normalizeAssociatedTypesIn(it)
                obligations.forEach(ctx.fulfill::registerPredicateObligation)
                bound
            }
            ctx.fulfill.selectWherePossible()

            return ParamEnv(bounds2.map { ctx.fullyResolve(it) })
        }
    }
}

class ImplLookup(
    private val project: Project,
    cargoProject: CargoProject?,
    val items: KnownItems,
    private val paramEnv: ParamEnv = ParamEnv.EMPTY
) {
    // Non-concurrent HashMap and lazy(NONE) are safe here because this class isn't shared between threads
    private val primitiveTyHardcodedImplsCache = mutableMapOf<TyPrimitive, Collection<BoundElement<RsTraitItem>>>()
    private val traitSelectionCache: Cache<TraitRef, SelectionResult<SelectionCandidate>> =
        if (paramEnv.isEmpty() && cargoProject != null) {
            cargoProjectGlobalTraitSelectionCache.getCache(cargoProject)
        } else {
            // function-local cache is used when [paramEnv] is not empty, i.e. if there are trait bounds
            // that affect trait selection
            Cache.new()
        }
    private val findImplsAndTraitsCache: Cache<Ty, List<TraitImplSource>> =
        if (cargoProject != null) {
            cargoProjectGlobalFindImplsAndTraitsCache.getCache(cargoProject)
        } else {
            Cache.new()
        }
    private val arithOps by lazy(NONE) {
        ArithmeticOp.values().mapNotNull { it.findTrait(items) }
    }
    private val assignArithOps by lazy(NONE) {
        ArithmeticAssignmentOp.values().mapNotNull { it.findTrait(items) }
    }
    private val fnTraits = listOfNotNull(items.Fn, items.FnMut, items.FnOnce)
    private val fnOnceOutput: RsTypeAlias? by lazy(NONE) {
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
        RsInferenceContext(project, this, items)
    }

    fun getEnvBoundTransitivelyFor(ty: Ty): Sequence<BoundElement<RsTraitItem>> {
        if (paramEnv == ParamEnv.LEGACY && ty is TyTypeParameter) {
            @Suppress("DEPRECATION")
            return ty.getTraitBoundsTransitively().asSequence()
        }
        return paramEnv.boundsFor(ty).flatMap { it.flattenHierarchy.asSequence() }
    }

    /** Resulting sequence is ordered: inherent impls are placed to the head */
    fun findImplsAndTraits(ty: Ty): Sequence<TraitImplSource> {
        val cached = findImplsAndTraitsCache.getOrPut(freshen(ty)) { rawFindImplsAndTraits(ty) }

        val isInherentBounds = ty is TyTypeParameter
        val envBounds = getEnvBoundTransitivelyFor(ty)
            .map { TraitImplSource.TraitBound(it.element, isInherent = isInherentBounds) }
        return if (isInherentBounds) envBounds + cached.asSequence() else cached.asSequence() + envBounds
    }

    private fun rawFindImplsAndTraits(ty: Ty): List<TraitImplSource> {
        val implsAndTraits = mutableListOf<TraitImplSource>()
        when (ty) {
            is TyTraitObject -> {
                ty.getTraitBoundsTransitively().mapTo(implsAndTraits) { TraitImplSource.Object(it.element) }
                findExplicitImpls(ty) { implsAndTraits += TraitImplSource.ExplicitImpl(it); false }
            }
            is TyFunction -> {
                findExplicitImpls(ty) { implsAndTraits += TraitImplSource.ExplicitImpl(it); false }
                implsAndTraits += fnTraits.map { TraitImplSource.Object(it) }
            }
            is TyAnon -> {
                ty.getTraitBoundsTransitively()
                    .distinctBy { it.element }
                    .mapTo(implsAndTraits) { TraitImplSource.Object(it.element) }
                RsImplIndex.findFreeImpls(project) { implsAndTraits += TraitImplSource.ExplicitImpl(it); false }
            }
            is TyProjection -> {
                val subst = ty.trait.subst + mapOf(TyTypeParameter.self() to ty.type).toTypeSubst()
                implsAndTraits += ty.trait.element.bounds.asSequence()
                    .filter { ctx.probe { ctx.combineTypes(it.selfTy.substitute(subst), ty) }.isOk }
                    .flatMap { it.trait.flattenHierarchy.asSequence() }
                    .map { TraitImplSource.ProjectionBound(it.element) }
                    .distinct()

            }
            is TyUnknown -> Unit
            else -> {
                implsAndTraits += findDerivedTraits(ty).map { TraitImplSource.Derived(it) }
                findExplicitImpls(ty) { implsAndTraits += TraitImplSource.ExplicitImpl(it); false }
                implsAndTraits += getHardcodedImpls(ty).map { TraitImplSource.Hardcoded(it.element) }.distinct()
            }
        }
        // Place inherent impls to the head of the list
        implsAndTraits.sortBy { !it.isInherent }
        testAssert { implsAndTraits.distinct().size == implsAndTraits.size }
        return implsAndTraits
    }

    private fun findDerivedTraits(ty: Ty): Collection<RsTraitItem> {
        return (ty as? TyAdt)?.item?.derivedTraits.orEmpty()
            // select only std traits because we are sure
            // that they are resolved correctly
            .filter { it.isKnownDerivable }
    }

    // TODO rename to BuiltinImpls
    /**
     * Keep in sync with [getHardcodedImplPredicates]
     *
     * @see <a href="https://doc.rust-lang.org/std/clone/trait.Clone.html#additional-implementors">Clone additional implementors</a>
     * @see <a href="https://doc.rust-lang.org/std/marker/trait.Copy.html#additional-implementors">Copy additional implementors</a>
     */
    private fun getHardcodedImpls(ty: Ty): Collection<BoundElement<RsTraitItem>> {
        if (ty is TyTuple || ty is TyArray) {
            return listOfNotNull(items.Clone, items.Copy).map { BoundElement(it) }
        }
        if (ty is TyFunction) {
            val fnOnceOutput = fnOnceOutput
            val args = if (ty.paramTypes.isEmpty()) TyUnit.INSTANCE else TyTuple(ty.paramTypes)
            val assoc = if (fnOnceOutput != null) mapOf(fnOnceOutput to ty.retType) else emptyMap()
            return fnTraits.map { it.withSubst(args).copy(assoc = assoc) } +
                listOfNotNull(items.Clone, items.Copy).map { BoundElement(it) }
        }

        if (project.macroExpansionManager.macroExpansionMode is MacroExpansionMode.New) {
            if (ty is TyUnit) {
                return listOfNotNull(items.Clone, items.Copy).map { BoundElement(it) }
            }
            return emptyList()
        }

        // TODO this code should be completely removed after removal of "old" macro expansion engine
        return when (ty) {
            is TyPrimitive -> {
                primitiveTyHardcodedImplsCache.getOrPut(ty) {
                    getHardcodedImplsForPrimitives(ty)
                }
            }
            is TyAdt -> when (ty.item) {
                items.findItem<RsNamedElement>("core::slice::Iter") -> {
                    val trait = items.Iterator ?: return emptyList()
                    listOf(
                        trait.substAssocType(
                            "Item",
                            TyReference(ty.typeParameterValues.typeByName("T"), IMMUTABLE)
                        )
                    )
                }
                items.findItem<RsNamedElement>("core::slice::IterMut") -> {
                    val trait = items.Iterator ?: return emptyList()
                    listOf(
                        trait.substAssocType(
                            "Item",
                            TyReference(ty.typeParameterValues.typeByName("T"), MUTABLE)
                        )
                    )
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
        if (ty !is TyStr) {
            // Default (libcore/default.rs)
            addImpl(items.Default)

            // PartialEq (libcore/cmp.rs)
            if (ty != TyNever && ty !is TyUnit) {
                addImpl(items.PartialEq, ty)
            }

            // Eq (libcore/cmp.rs)
            if (ty !is TyFloat && ty !is TyInfer.FloatVar && ty != TyNever) {
                addImpl(items.Eq)
            }

            // PartialOrd (libcore/cmp.rs)
            if (ty !is TyUnit && ty !is TyBool && ty != TyNever) {
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

    /**
     * Keep in sync with [getHardcodedImpls]
     *
     * @see <a href="https://doc.rust-lang.org/std/clone/trait.Clone.html#additional-implementors">Clone additional implementors</a>
     * @see <a href="https://doc.rust-lang.org/std/marker/trait.Copy.html#additional-implementors">Copy additional implementors</a>
     */
    private fun getHardcodedImplPredicates(ty: Ty, trait: BoundElement<RsTraitItem>): List<Predicate> {
        return when (ty) {
            is TyTuple -> ty.types.map { Predicate.Trait(TraitRef(it, trait)) }
            is TyArray -> listOf(Predicate.Trait(TraitRef(ty.base, trait)))
            else -> emptyList()
        }
    }

    private fun findExplicitImpls(selfTy: Ty, processor: RsProcessor<RsCachedImplItem>): Boolean {
        return processTyFingerprintsWithAliases(selfTy) { tyFingerprint ->
            findExplicitImplsWithoutAliases(selfTy, tyFingerprint, processor)
        }
    }

    private fun findExplicitImplsWithoutAliases(selfTy: Ty, tyf: TyFingerprint, processor: RsProcessor<RsCachedImplItem>): Boolean {
        return RsImplIndex.findPotentialImpls(project, tyf) { cachedImpl ->
            val (type, generics, constGenerics) = cachedImpl.typeAndGenerics ?: return@findPotentialImpls false
            val isAppropriateImpl = canCombineTypes(selfTy, type, generics, constGenerics) &&
                // Check that trait is resolved if it's not an inherent impl; checking it after types because
                // we assume that unresolved trait is a rare case
                (cachedImpl.isInherent || cachedImpl.implementedTrait != null)
            isAppropriateImpl && processor(cachedImpl)
        }
    }

    private fun processTyFingerprintsWithAliases(selfTy: Ty, processor: RsProcessor<TyFingerprint>): Boolean {
        val fingerprint = TyFingerprint.create(selfTy)
        if (fingerprint != null) {
            val set = mutableSetOf(fingerprint)
            if (processor(fingerprint)) return true
            val result = RsTypeAliasIndex.findPotentialAliases(project, fingerprint) {
                val name = it.name ?: return@findPotentialAliases false
                val aliasFingerprint = TyFingerprint(name)
                val isAppropriateAlias = set.add(aliasFingerprint) && run {
                    val (declaredType, generics, constGenerics) = it.typeAndGenerics
                    canCombineTypes(selfTy, declaredType, generics, constGenerics)
                }
                isAppropriateAlias && processor(aliasFingerprint)
            }
            if (result) return true
        }
        return processor(TyFingerprint.TYPE_PARAMETER_FINGERPRINT)
    }

    private fun canCombineTypes(
        ty1: Ty,
        ty2: Ty,
        genericsForTy2: List<TyTypeParameter>,
        constGenericsForTy2: List<CtConstParameter>
    ): Boolean {
        val subst = Substitution(
            typeSubst = genericsForTy2.associateWith { ctx.typeVarForParam(it) },
            constSubst = constGenericsForTy2.associateWith { ctx.constVarForParam(it) }
        )
        // TODO: take into account the lifetimes (?)
        return ctx.probe {
            val (normTy2, _) = ctx.normalizeAssociatedTypesIn(ty2.substitute(subst))
            ctx.combineTypes(normTy2, ty1).isOk
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
        selectStrictWithoutConfirm(ref, recursionDepth).andThen { confirmCandidate(ref, it, recursionDepth) }

    private fun selectStrictWithoutConfirm(ref: TraitRef, recursionDepth: Int): SelectionResult<SelectionCandidate> {
        val result = selectWithoutConfirm(ref, recursionDepth)
        val candidate = result.ok() ?: return result.map { error("unreachable") }
        // TODO optimize it. Obligations may be already evaluated, so we don't need to re-evaluated it
        if (!canEvaluateObligations(ref, candidate, recursionDepth)) return SelectionResult.Err
        return result
    }

    /**
     * If the TraitRef is a something like
     *     `T : Foo<U>`
     * here we select an impl of the trait `Foo<U>` for the type `T`, i.e.
     *     `impl Foo<U> for T {}`
     */
    fun select(ref: TraitRef, recursionDepth: Int = 0): SelectionResult<Selection> =
        selectWithoutConfirm(ref, recursionDepth).andThen { confirmCandidate(ref, it, recursionDepth) }

    private fun selectWithoutConfirm(ref: TraitRef, recursionDepth: Int): SelectionResult<SelectionCandidate> {
        if (recursionDepth > DEFAULT_RECURSION_LIMIT) return SelectionResult.Err
        testAssert { !ctx.hasResolvableTypeVars(ref) }
        return traitSelectionCache.getOrPut(freshen(ref)) { selectCandidate(ref, recursionDepth) }
    }

    private fun selectCandidate(ref: TraitRef, recursionDepth: Int): SelectionResult<SelectionCandidate> {
        if (ref.selfTy is TyReference && ref.selfTy.referenced is TyInfer.TyVar) {
            // This condition is related to TyFingerprint internals: TyFingerprint should not be created for
            // TyInfer.TyVar, and TyReference is a single special case: it unwraps during TyFingerprint creation
            return SelectionResult.Ambiguous
        }

        val candidates = assembleCandidates(ref)

        return when (candidates.size) {
            0 -> SelectionResult.Err
            1 -> SelectionResult.Ok(candidates.single())
            else -> {
                val filtered = candidates.filter {
                    canEvaluateObligations(ref, it, recursionDepth)
                }

                when (filtered.size) {
                    0 -> SelectionResult.Err
                    1 -> SelectionResult.Ok(filtered.single())
                    else -> {
                        // basic specialization
                        filtered.singleOrNull {
                            it !is SelectionCandidate.Impl || it.formalSelfTy !is TyTypeParameter
                        }?.let {
                            TypeInferenceMarks.TraitSelectionSpecialization.hit()
                            SelectionResult.Ok(it)
                        } ?: SelectionResult.Ambiguous
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
        val tyMap = hashMapOf<TyInfer, FreshTyInfer>()
        val constMap = hashMapOf<CtInferVar, FreshCtInferVar>()

        var counter = 0
        return ty
            .foldTyInferWith {
                tyMap.getOrPut(it) {
                    when (it) {
                        is TyInfer.TyVar -> FreshTyInfer.TyVar(counter++)
                        is TyInfer.IntVar -> FreshTyInfer.IntVar(counter++)
                        is TyInfer.FloatVar -> FreshTyInfer.FloatVar(counter++)
                    }
                }
            }
            .foldCtInferWith {
                constMap.getOrPut(it) { FreshCtInferVar(counter++) }
            }
    }

    private fun canEvaluateObligations(ref: TraitRef, candidate: SelectionCandidate, recursionDepth: Int): Boolean {
        return ctx.probe {
            val obligation = confirmCandidate(ref, candidate, recursionDepth).ok()?.nestedObligations ?: return false
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
            element == items.Destruct -> listOf(SelectionCandidate.TypeParameter(BoundElement(element)))
            element == items.Unsize -> unsizeTraitCandidates(ref)
            ref.selfTy is TyAnon -> buildList {
                ref.selfTy.getTraitBoundsTransitively().find { it.element == element }
                    ?.let { add(SelectionCandidate.TraitObject) }
                RsImplIndex.findFreeImpls(project) {
                    it.trySelectCandidate(ref)?.let(::add)
                    false
                }
            }
            element.isAuto -> autoTraitCandidates(ref.selfTy, element)
            else -> buildList {
                getEnvBoundTransitivelyFor(ref.selfTy).asSequence()
                    .filter { ctx.probe { ctx.combineBoundElements(it, ref.trait) } }
                    .map { SelectionCandidate.TypeParameter(it) }
                    .forEach(::add)

                if (ref.selfTy is TyProjection) {
                    val subst = ref.selfTy.trait.subst + mapOf(TyTypeParameter.self() to ref.selfTy.type).toTypeSubst()
                    ref.selfTy.trait.element.bounds.asSequence()
                        .filter { ctx.probe { ctx.combineTypes(it.selfTy.substitute(subst), ref.selfTy) }.isOk }
                        .flatMap { it.trait.flattenHierarchy.asSequence() }
                        .distinct()
                        .filter { ctx.probe { ctx.combineBoundElements(it.substitute(subst), ref.trait) } }
                        .forEach { add(SelectionCandidate.Projection(TraitRef(ref.selfTy, it))) }
                    return@buildList
                }
                assembleImplCandidates(ref) { add(it); false }
                addAll(assembleDerivedCandidates(ref))
                if (ref.selfTy is TyTraitObject) {
                    ref.selfTy.getTraitBoundsTransitively().find { it.element == ref.trait.element }
                        ?.let { add(SelectionCandidate.TraitObject) }
                }
                getHardcodedImpls(ref.selfTy).filter { be ->
                    be.element == element && ctx.probe {
                        ctx.combineTypePairs(be.subst.zipTypeValues(ref.trait.subst)).isOk &&
                            ctx.combineConstPairs(be.subst.zipConstValues(ref.trait.subst)).isOk
                    }
                }.forEach { _ -> add(SelectionCandidate.HardcodedImpl) }
            }
        }
    }

    private fun assembleImplCandidates(ref: TraitRef, processor: RsProcessor<SelectionCandidate>): Boolean {
        return processTyFingerprintsWithAliases(ref.selfTy) { tyFingerprint ->
            assembleImplCandidatesWithoutAliases(ref, tyFingerprint, processor)
        }
    }

    private fun assembleImplCandidatesWithoutAliases(ref: TraitRef, tyf: TyFingerprint, processor: RsProcessor<SelectionCandidate>): Boolean {
        return RsImplIndex.findPotentialImpls(project, tyf) {
            val candidate = it.trySelectCandidate(ref)
            candidate != null && processor(candidate)
        }
    }

    private fun RsCachedImplItem.trySelectCandidate(ref: TraitRef): SelectionCandidate? {
        val formalTraitRef = implementedTrait ?: return null
        if (formalTraitRef.element != ref.trait.element) return null
        val (formalSelfTy, generics, constGenerics) = typeAndGenerics ?: return null
        val probe = ctx.probe {
            val (_, implTraitRef, _) =
                prepareSubstAndTraitRefRaw(ctx, generics, constGenerics, formalSelfTy, formalTraitRef, ref.selfTy, 0)
            ctx.combineTraitRefs(implTraitRef, ref)
        }
        if (!probe) return null
        return SelectionCandidate.Impl(impl, formalSelfTy, formalTraitRef)
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
        val candidate = SelectionCandidate.TypeParameter(BoundElement(sizedTrait))
        if (!isSizedTypeImpl(ty)) return emptyList()
        return listOf(candidate)
    }

    // Mirrors rustc's `assemble_candidates_for_unsizing`
    // https://github.com/rust-lang/rust/blob/97d48bec2d/compiler/rustc_trait_selection/src/traits/select/candidate_assembly.rs#L741
    private fun unsizeTraitCandidates(ref: TraitRef): List<SelectionCandidate> {
        val source = ref.selfTy
        val target = ref.trait.singleParamValue
        return when {
            // Trait+Kx+'a -> Trait+Ky+'b (upcasts)
            source is TyTraitObject && target is TyTraitObject -> {
                listOf(SelectionCandidate.BuiltinUnsizeCandidate) // TODO
            }

            // `T` -> `Trait`
            target is TyTraitObject -> listOf(SelectionCandidate.BuiltinUnsizeCandidate)

            // `[T; n]` -> `[T]`
            source is TyArray && target is TySlice -> listOf(SelectionCandidate.BuiltinUnsizeCandidate)

            // `Struct<T>` -> `Struct<U>`
            source is TyAdt && target is TyAdt && source.item == target.item && source.item is RsStructItem
                && source.item.kind == RsStructKind.STRUCT -> listOf(SelectionCandidate.BuiltinUnsizeCandidate)

            // `(.., T)` -> `(.., U)`
            source is TyTuple && target is TyTuple && source.types.size == target.types.size ->
                listOf(SelectionCandidate.BuiltinUnsizeCandidate)

            else -> emptyList()
        }
    }

    /** See `org.rust.lang.core.type.RsImplicitTraitsTest` */
    private fun isSizedTypeImpl(ty: Ty): Boolean {
        val ancestors = mutableSetOf(ty)

        fun Ty.isSizedInner(): Boolean {
            return when (this) {
                is TyNumeric,
                is TyBool,
                is TyChar,
                is TyUnit,
                is TyNever,
                is TyReference,
                is TyPointer,
                is TyArray,
                is TyFunction -> true

                is TyStr, is TySlice, is TyTraitObject -> false

                is TyTypeParameter -> getEnvBoundTransitivelyFor(this).any { it.element == items.Sized }

                is TyAdt -> {
                    val item = item as? RsStructItem ?: return true
                    val typeRef = item.fields.lastOrNull()?.typeReference
                    val type = typeRef?.type?.substitute(typeParameterValues) ?: return true
                    if (!ancestors.add(type)) return true
                    type.isSizedInner()
                }

                is TyTuple -> types.last().isSizedInner()

                else -> true
            }
        }

        return ty.isSizedInner()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun autoTraitCandidates(ty: Ty, trait: RsTraitItem): List<SelectionCandidate> {
        // FOr now, just think that any type is Sync + Send
        // TODO implement auto trait logic
        return listOf(SelectionCandidate.TypeParameter(BoundElement(trait)))
    }

    private fun confirmCandidate(
        ref: TraitRef,
        candidate: SelectionCandidate,
        recursionDepth: Int
    ): SelectionResult<Selection> {
        val newRecDepth = recursionDepth + 1
        return when (candidate) {
            is SelectionCandidate.Impl -> {
                testAssert { !candidate.formalSelfTy.containsTyOfClass(TyInfer::class.java) }
                testAssert { !candidate.formalTrait.containsTyOfClass(TyInfer::class.java) }
                val (subst, preparedRef, typeObligations) = candidate.prepareSubstAndTraitRef(ctx, ref.selfTy, newRecDepth)
                ctx.combineTraitRefs(ref, preparedRef)
                // pre-resolve type vars to simplify caching of already inferred obligation on fulfillment
                val candidateSubst = subst
                    .mapTypeValues { (_, v) -> ctx.resolveTypeVarsIfPossible(v) }
                    .mapConstValues { (_, v) -> ctx.resolveTypeVarsIfPossible(v) } +
                    mapOf(TyTypeParameter.self() to ref.selfTy).toTypeSubst()
                val obligations = typeObligations +
                    ctx.instantiateBounds(candidate.impl.predicates, candidateSubst, newRecDepth)
                SelectionResult.Ok(Selection(candidate.impl, obligations, candidateSubst))
            }
            is SelectionCandidate.DerivedTrait -> {
                val selfTy = ref.selfTy as TyAdt // Guaranteed by `assembleCandidates`
                // For `#[derive(Clone)] struct S<T>(T);` add `T: Clone` obligation
                val obligations = selfTy.typeArguments.map {
                    Obligation(newRecDepth, Predicate.Trait(TraitRef(it, BoundElement(candidate.item))))
                }
                SelectionResult.Ok(Selection(candidate.item, obligations))
            }
            is SelectionCandidate.TypeParameter -> {
                testAssert { !candidate.bound.containsTyOfClass(TyInfer::class.java) }
                ctx.combineBoundElements(candidate.bound, ref.trait)
                SelectionResult.Ok(Selection(candidate.bound.element, emptyList()))
            }
            is SelectionCandidate.Projection -> {
                ref.selfTy as TyProjection
                val subst = ref.selfTy.trait.subst + mapOf(TyTypeParameter.self() to ref.selfTy.type).toTypeSubst()
                ctx.combineTraitRefs(ref, candidate.bound.substitute(subst))
                SelectionResult.Ok(Selection(ref.trait.element, emptyList()))
            }
            SelectionCandidate.TraitObject -> {
                val traits = when (ref.selfTy) {
                    is TyTraitObject -> ref.selfTy.getTraitBoundsTransitively()
                    is TyAnon -> ref.selfTy.getTraitBoundsTransitively()
                    else -> error("unreachable")
                }
                // should be nonnull because already checked in `assembleCandidates`
                val be = traits.find { it.element == ref.trait.element } ?: error("Corrupted trait selection")
                ctx.combineBoundElements(be, ref.trait)
                SelectionResult.Ok(Selection(be.element, emptyList()))
            }
            SelectionCandidate.BuiltinUnsizeCandidate -> confirmBuiltinUnsizeCandidate(ref, recursionDepth)
            is SelectionCandidate.HardcodedImpl -> {
                val impl = getHardcodedImpls(ref.selfTy).first { be ->
                    be.element == ref.trait.element && ctx.probe {
                        ctx.combineTypePairs(be.subst.zipTypeValues(ref.trait.subst)).isOk &&
                            ctx.combineConstPairs(be.subst.zipConstValues(ref.trait.subst)).isOk
                    }
                }
                ctx.combineBoundElements(impl, ref.trait)
                val obligations = getHardcodedImplPredicates(ref.selfTy, impl).map { Obligation(newRecDepth, it) }
                SelectionResult.Ok(Selection(impl.element, obligations, mapOf(TyTypeParameter.self() to ref.selfTy).toTypeSubst()))
            }
        }
    }

    // Mirrors rustc's `confirm_builtin_unsize_candidate`
    // https://github.com/rust-lang/rust/blob/97d48bec2d/compiler/rustc_trait_selection/src/traits/select/confirmation.rs#L865
    private fun confirmBuiltinUnsizeCandidate(
        ref: TraitRef,
        recursionDepth: Int
    ): SelectionResult<Selection> {
        val unsizeTrait = ref.trait.element
        val unsizeTypeParam = unsizeTrait.typeParamSingle ?: return SelectionResult.Err
        val source = ctx.shallowResolve(ref.selfTy)
        val target = ctx.shallowResolve(ref.trait.subst.typeSubst[unsizeTypeParam] ?: return SelectionResult.Err)
        val okSelection = Selection(
            impl = unsizeTrait,
            nestedObligations = emptyList(),
            subst = mapOf(unsizeTypeParam to target).toTypeSubst()
        )
        when {
            // `T` -> `Trait`
            target is TyTraitObject -> {
                // TODO implement unsizing. Currently always allowed
                TypeInferenceMarks.UnsizeToTraitObject.hit()
                return SelectionResult.Ok(okSelection)
            }

            // `[T; n]` -> `[T]`
            source is TyArray && target is TySlice -> {
                if (ctx.combineTypes(target.elementType, source.base).isOk) {
                    TypeInferenceMarks.UnsizeArrayToSlice.hit()
                    return SelectionResult.Ok(okSelection)
                }
            }

            // `Struct<T>` -> `Struct<U>`
            source is TyAdt && target is TyAdt -> {
                check(source.item == target.item) { "Guaranteed by assembleCandidates" }
                check(source.item is RsStructItem) { "Guaranteed by assembleCandidates" }
                val fields = source.item.fields
                val lastField = fields.lastOrNull() ?: return SelectionResult.Err
                val lastFieldType = lastField.typeReference?.type ?: return SelectionResult.Err
                val unsizingParams = hashSetOf<TyTypeParameter>()
                // TODO consider const params
                lastFieldType.visitTypeParameters { ty ->
                    unsizingParams += ty
                    false
                }
                for (field in fields) {
                    if (field == lastField) break
                    val fieldType = field.typeReference?.type ?: continue
                    fieldType.visitTypeParameters { ty ->
                        unsizingParams -= ty
                        false
                    }
                }
                if (unsizingParams.isEmpty()) return SelectionResult.Err

                val sourceSubst = source.typeParameterValues
                val targetSubst = target.typeParameterValues

                // Check that the source struct with the target's unsizing parameters is equal to the target.
                val subst = sourceSubst.mapTypeValues { (k, v) ->
                    if (k in unsizingParams) targetSubst[k] ?: TyUnknown else v
                }
                val newStruct = source.item.declaredType.substitute(subst)
                if (!ctx.combineTypes(target, newStruct).isOk) {
                    return SelectionResult.Err
                }

                // Extract `TailField<T>` and `TailField<U>` from `Struct<T>` and `Struct<U>`.
                val sourceTail = lastFieldType.substitute(sourceSubst)
                val targetTail = lastFieldType.substitute(targetSubst)

                // Construct the nested `TailField<T>: Unsize<TailField<U>>` predicate.
                val nested = Obligation(
                    recursionDepth + 1,
                    Predicate.Trait(
                        TraitRef(
                            sourceTail,
                            unsizeTrait.withSubst(targetTail)
                        )
                    )
                )
                TypeInferenceMarks.UnsizeStruct.hit()
                return SelectionResult.Ok(okSelection.copy(nestedObligations = listOf(nested)))
            }

            // `(.., T)` -> `(.., U)`
            source is TyTuple && target is TyTuple -> {
                // Check that the source tuple with the target's last element is equal to the target
                val newTuple = TyTuple(source.types.dropLast(1) + listOf(target.types.last()))
                if (!ctx.combineTypes(target, newTuple).isOk) {
                    return SelectionResult.Err
                }
                // Construct the nested `T: Unsize<U>` predicate.
                val nested = Obligation(
                    recursionDepth + 1,
                    Predicate.Trait(
                        TraitRef(
                            source.types.last(),
                            unsizeTrait.withSubst(target.types.last())
                        )
                    )
                )
                TypeInferenceMarks.UnsizeTuple.hit()
                return SelectionResult.Ok(okSelection.copy(nestedObligations = listOf(nested)))
            }
        }
        return SelectionResult.Err
    }

    fun coercionSequence(baseTy: Ty): Sequence<Ty> {
        val result = mutableSetOf<Ty>()
        return generateSequence(ctx.resolveTypeVarsIfPossible(baseTy)) {
            if (result.add(it)) {
                deref(it)?.let(ctx::resolveTypeVarsIfPossible)
                    ?: (it as? TyArray)?.let { array -> TySlice(array.base) }
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
        return ctx.normalizeAssociatedTypesIn(TyProjection.valueOf(lhsType, trait.withSubst(rhsType), assocType))
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
    ): SelectionResult<TyWithObligations<Ty>?> =
        select(ref, recursionDepth).map { selection ->
            lookupAssociatedType(ref.selfTy, selection, assocType)
                ?.let { ctx.normalizeAssociatedTypesIn(it, recursionDepth) }
                ?.withObligations(selection.nestedObligations)
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
        return selectStrict(ref, recursionDepth).map { selection ->
            lookupAssociatedType(ref.selfTy, selection, assocType)
                ?.let { ctx.normalizeAssociatedTypesIn(it, recursionDepth) }
                ?.withObligations(selection.nestedObligations)
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
            ?: SelectionResult.Err

    fun selectAllProjectionsStrict(ref: TraitRef): Map<RsTypeAlias, Ty>? = ctx.probe {
        val selection = select(ref).ok() ?: return@probe null
        val assocValues = ref.trait.element.associatedTypesTransitively.associateWith { assocType ->
            lookupAssociatedType(ref.selfTy, selection, assocType)
                ?.let { ctx.normalizeAssociatedTypesIn(it) }
                ?.withObligations(selection.nestedObligations)
                ?: TyWithObligations(TyUnknown)
        }
        val fulfill = FulfillmentContext(ctx, this)
        assocValues.values.flatMap { it.obligations }.forEach(fulfill::registerPredicateObligation)

        if (fulfill.selectUntilError()) {
            assocValues.mapValues { (_, v) -> ctx.resolveTypeVarsIfPossible(v.value) }
        } else {
            null
        }
    }

    private fun lookupAssociatedType(selfTy: Ty, res: Selection, assocType: RsTypeAlias): Ty? {
        return when {
            res.impl is RsImplItem -> lookupAssocTypeInSelection(res, assocType)
            selfTy is TyTypeParameter -> lookupAssocTypeInBounds(getEnvBoundTransitivelyFor(selfTy), res.impl, assocType)
            selfTy is TyTraitObject -> lookupAssocTypeInBounds(selfTy.getTraitBoundsTransitively().asSequence(), res.impl, assocType)
            else -> {
                lookupAssocTypeInSelection(res, assocType)
                    ?: lookupAssocTypeInBounds(getHardcodedImpls(selfTy).asSequence(), res.impl, assocType)
                    ?: (selfTy as? TyAnon)?.let { lookupAssocTypeInBounds(it.getTraitBoundsTransitively().asSequence(), res.impl, assocType) }
            }
        }
    }

    private fun lookupAssocTypeInSelection(selection: Selection, assoc: RsTypeAlias): Ty? =
        selection.impl.associatedTypesTransitively.find { it.name == assoc.name }?.typeReference?.type?.substitute(selection.subst)

    private fun lookupAssocTypeInBounds(
        subst: Sequence<BoundElement<RsTraitItem>>,
        trait: RsTraitOrImpl,
        assocType: RsTypeAlias
    ): Ty? {
        return subst
            .find { it.element == trait }
            ?.assoc
            ?.get(assocType)
    }

    private fun selectOverloadedOp(lhsType: Ty, rhsType: Ty, op: OverloadableBinaryOperator): SelectionResult<Selection> {
        val trait = op.findTrait(items) ?: return SelectionResult.Err
        return select(TraitRef(lhsType, trait.withSubst(rhsType)))
    }

    fun findOverloadedOpImpl(lhsType: Ty, rhsType: Ty, op: OverloadableBinaryOperator): RsTraitOrImpl? =
        selectOverloadedOp(lhsType, rhsType, op).ok()?.impl

    fun asTyFunction(ty: Ty): TyWithObligations<TyFunction>? {
        return (ty as? TyFunction)?.withObligations() ?: run {
            val output = fnOnceOutput ?: return@run null

            val inputArgVar = TyInfer.TyVar()
            val ok = fnTraits.asSequence()
                .mapNotNull { ctx.commitIfNotNull { selectProjection(it to output, ty, inputArgVar).ok() } }
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

    fun isSized(ty: Ty): Boolean {
        // Treat all types as sized if `Sized` trait is not found. This suppresses error noise in the
        // case of the toolchain misconfiguration (when there is not a `Sized` trait)
        val sizedTrait = items.Sized ?: return true
        return ty.isTraitImplemented(sizedTrait)
    }
    fun isDeref(ty: Ty): Boolean = ty.isTraitImplemented(items.Deref)
    fun isCopy(ty: Ty): Boolean = ty.isTraitImplemented(items.Copy)
    fun isClone(ty: Ty): Boolean = ty.isTraitImplemented(items.Clone)
    fun isDebug(ty: Ty): Boolean = ty.isTraitImplemented(items.Debug)
    fun isDefault(ty: Ty): Boolean = ty.isTraitImplemented(items.Default)
    fun isEq(ty: Ty): Boolean = ty.isTraitImplemented(items.Eq)
    fun isPartialEq(ty: Ty, rhsType: Ty = ty): Boolean = ty.isTraitImplemented(items.PartialEq, rhsType)
    fun isIntoIterator(ty: Ty): Boolean = ty.isTraitImplemented(items.IntoIterator)
    fun isAnyFn(ty: Ty): Boolean = isFn(ty) || isFnOnce(ty) || isFnMut(ty)
    @Suppress("MemberVisibilityCanBePrivate")
    fun isFn(ty: Ty): Boolean = ty.isTraitImplemented(items.Fn)
    @Suppress("MemberVisibilityCanBePrivate")
    fun isFnOnce(ty: Ty): Boolean = ty.isTraitImplemented(items.FnOnce)
    @Suppress("MemberVisibilityCanBePrivate")
    fun isFnMut(ty: Ty): Boolean = ty.isTraitImplemented(items.FnMut)


    private fun Ty.isTraitImplemented(trait: RsTraitItem?, vararg subst: Ty): Boolean {
        if (trait == null) return false
        return canSelect(TraitRef(this, trait.withSubst(*subst)))
    }

    private val BoundElement<RsTraitItem>.asFunctionType: TyFunction?
        get() {
            val outputParam = fnOnceOutput ?: return null
            val param = element.typeParamSingle ?: return null
            val argumentTypes = ((subst[param] ?: TyUnknown) as? TyTuple)?.types.orEmpty()
            val outputType = (assoc[outputParam] ?: TyUnit.INSTANCE)
            return TyFunction(argumentTypes, outputType)
        }

    companion object {
        fun relativeTo(psi: RsElement): ImplLookup {
            val parentItem = psi.contextOrSelf<RsItemElement>()
            val paramEnv = if (parentItem is RsGenericDeclaration) {
                val useLegacy = psi.contextOrSelf<RsWherePred>() != null ||
                    psi.contextOrSelf<RsBound>() != null ||
                    run {
                        val impl = psi.contextOrSelf<RsImplItem>() ?: return@run false
                        impl.traitRef?.isAncestorOf(psi) == true || impl.typeReference?.isAncestorOf(psi) == true
                    }
                if (useLegacy) {
                    // We should mock ParamEnv here. Otherwise we run into infinite recursion
                    // This is mostly a hack. It should be solved in the future somehow
                    ParamEnv.LEGACY
                } else {
                    ParamEnv.buildFor(parentItem)
                }
            } else {
                ParamEnv.EMPTY
            }
            return ImplLookup(psi.project, psi.cargoProject, psi.knownItems, paramEnv)
        }

        private val cargoProjectGlobalFindImplsAndTraitsCache =
            CargoProjectCache<Ty, List<TraitImplSource>>("cargoProjectGlobalFindImplsAndTraitsCache")

        private val cargoProjectGlobalTraitSelectionCache =
            CargoProjectCache<TraitRef, SelectionResult<SelectionCandidate>>("cargoProjectGlobalTraitSelectionCache")
    }
}

sealed class SelectionResult<out T> {
    object Err : SelectionResult<Nothing>()
    object Ambiguous : SelectionResult<Nothing>()
    data class Ok<out T>(
        val result: T
    ) : SelectionResult<T>()

    fun ok(): T? = (this as? Ok<T>)?.result

    fun isOk(): Boolean = this is Ok<T>

    inline fun <R> map(action: (T) -> R): SelectionResult<R> = when (this) {
        is Err -> Err
        is Ambiguous -> Ambiguous
        is Ok -> Ok(action(result))
    }

    inline fun <R> andThen(action: (T) -> SelectionResult<R>): SelectionResult<R> = when (this) {
        is Err -> Err
        is Ambiguous -> Ambiguous
        is Ok -> action(result)
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
        fun prepareSubstAndTraitRef(ctx: RsInferenceContext, selfTy: Ty, recursionDepth: Int): Triple<Substitution, TraitRef, List<Obligation>> =
            prepareSubstAndTraitRefRaw(ctx, impl.generics, impl.constGenerics, formalSelfTy, formalTrait, selfTy, recursionDepth)
    }

    data class DerivedTrait(val item: RsTraitItem) : SelectionCandidate()
    data class TypeParameter(val bound: BoundElement<RsTraitItem>) : SelectionCandidate()
    object TraitObject : SelectionCandidate()
    object BuiltinUnsizeCandidate : SelectionCandidate()

    /** @see ImplLookup.getHardcodedImpls */
    object HardcodedImpl : SelectionCandidate()

    class Projection(val bound: TraitRef) : SelectionCandidate()
}

private fun prepareSubstAndTraitRefRaw(
    ctx: RsInferenceContext,
    typeGenerics: List<TyTypeParameter>,
    constGenerics: List<CtConstParameter>,
    formalSelfTy: Ty,
    formalTrait: BoundElement<RsTraitItem>,
    selfTy: Ty,
    recursionDepth: Int
): Triple<Substitution, TraitRef, List<Obligation>> {
    val subst = Substitution(
        typeSubst = typeGenerics.associateWith { ctx.typeVarForParam(it) },
        constSubst = constGenerics.associateWith { ctx.constVarForParam(it) }
    )
    val boundSubst = formalTrait.substitute(subst)
        .subst
        .substituteInValues(mapOf(TyTypeParameter.self() to selfTy).toTypeSubst())
    val (normSelfTy, obligations) = ctx.normalizeAssociatedTypesIn(formalSelfTy.substitute(subst), recursionDepth)
    return Triple(subst, TraitRef(normSelfTy, BoundElement(formalTrait.element, boundSubst)), obligations)
}

private fun <T : Ty> T.withObligations(obligations: List<Obligation> = emptyList()): TyWithObligations<T> =
    TyWithObligations(this, obligations)
