/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.SmartList
import gnu.trove.THashMap
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.ide.injected.isInsideInjection
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.crate.hasTransitiveDependencyOrSelf
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.SelectionCandidate.*
import org.rust.lang.core.types.*
import org.rust.lang.core.types.consts.CtConstParameter
import org.rust.lang.core.types.consts.CtInferVar
import org.rust.lang.core.types.consts.FreshCtInferVar
import org.rust.lang.core.types.infer.*
import org.rust.lang.core.types.infer.TypeInferenceMarks.WinnowParamCandidateLoses
import org.rust.lang.core.types.infer.TypeInferenceMarks.WinnowParamCandidateWins
import org.rust.lang.core.types.ty.*
import org.rust.openapiext.hitOnTrue
import org.rust.openapiext.testAssert
import org.rust.stdext.*
import kotlin.LazyThreadSafetyMode.NONE
import kotlin.LazyThreadSafetyMode.PUBLICATION

private val RsTraitItem.typeParamSingle: TyTypeParameter?
    get() = typeParameters.singleOrNull()?.let { TyTypeParameter.named(it) }

const val DEFAULT_RECURSION_LIMIT = 128

sealed class TraitImplSource {
    abstract val value: RsTraitOrImpl

    open val implementedTrait: BoundElement<RsTraitItem>? get() = value.implementedTrait

    /** For `impl T for Foo` returns union of impl members and trait `T` members that are not overridden by the impl */
    abstract val implAndTraitExpandedMembers: Map<String, List<RsAbstractable>>

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
    data class TraitBound(override val value: RsTraitItem, override val isInherent: Boolean) : TraitImplSource() {
        override val implAndTraitExpandedMembers: Map<String, List<RsAbstractable>> by lazyTraitMembers(value)
    }

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
    data class ProjectionBound(override val value: RsTraitItem) : TraitImplSource() {
        override val implAndTraitExpandedMembers: Map<String, List<RsAbstractable>> by lazyTraitMembers(value)
    }

    /** Trait is implemented for item via ```#[derive]``` attribute. */
    data class Derived(override val value: RsTraitItem) : TraitImplSource() {
        override val implAndTraitExpandedMembers: Map<String, List<RsAbstractable>> by lazyTraitMembers(value)
    }

    /** dyn/impl Trait or a closure */
    data class Object(override val value: RsTraitItem) : TraitImplSource() {
        override val implAndTraitExpandedMembers: Map<String, List<RsAbstractable>> by lazyTraitMembers(value)
        override val isInherent: Boolean get() = true
    }

    /**
     * Used only as a result of method pick. It means that method is resolved to multiple impls of the same trait
     * (with different type parameter values), so we collapsed all impls to that trait. Specific impl
     * will be selected during type inference.
     */
    data class Collapsed(override val value: RsTraitItem) : TraitImplSource() {
        override val implAndTraitExpandedMembers: Map<String, List<RsAbstractable>> by lazyTraitMembers(value)
    }

    /**
     * A trait is directly referenced in UFCS path `TraitName::foo`, an impl should be selected
     * during type inference
     */
    data class Trait(override val value: RsTraitItem) : TraitImplSource() {
        override val implAndTraitExpandedMembers: Map<String, List<RsAbstractable>> by lazyTraitMembers(value)
    }

    /** A built-in trait impl, like `Clone` impl for tuples */
    data class Builtin(override val value: RsTraitItem) : TraitImplSource() {
        override val implAndTraitExpandedMembers: Map<String, List<RsAbstractable>> by lazyTraitMembers(value)
    }

    companion object {
        @JvmStatic
        private fun lazyTraitMembers(trait: RsTraitItem): Lazy<THashMap<String, MutableList<RsAbstractable>>> =
            lazy(PUBLICATION) { collectTraitMembers(trait) }

        @JvmStatic
        private fun collectTraitMembers(trait: RsTraitItem): THashMap<String, MutableList<RsAbstractable>> {
            val membersMap = THashMap<String, MutableList<RsAbstractable>>()
            for (member in trait.members?.expandedMembers.orEmpty()) {
                val name = member.name ?: continue
                membersMap.getOrPut(name) { SmartList() }.add(member)
            }
            return membersMap
        }
    }
}

/**
 * When type checking, we use the `ParamEnv` to track details about the set of where-clauses
 * that are in scope at this particular point.
 * Note: ParamEnv of an associated item (method) also contains bounds of its trait/impl
 * Note: callerBounds should have type `List<Predicate>` to also support lifetime bounds
 */
interface ParamEnv {
    fun boundsFor(ty: Ty): Sequence<BoundElement<RsTraitItem>>

    companion object {
        val EMPTY: ParamEnv = SimpleParamEnv(emptyList())

        fun buildFor(decl: RsItemElement): ParamEnv {
            val rawBounds = buildList {
                if (decl is RsGenericDeclaration) {
                    addAll(decl.bounds)
                    if (decl is RsTraitItem) {
                        add(TraitRef(TyTypeParameter.self(), decl.withDefaultSubst()))
                    }
                }
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
            }.flatMap { it.flattenHierarchy }
                .distinct()

            when (rawBounds.size) {
                0 -> return EMPTY
                1 -> return SimpleParamEnv(rawBounds)
            }

            val lookup = ImplLookup(decl.project, decl.containingCrate, decl.knownItems, SimpleParamEnv(rawBounds))
            val ctx = lookup.ctx
            val bounds2 = rawBounds.map {
                val (bound, obligations) = ctx.normalizeAssociatedTypesIn(it)
                obligations.forEach(ctx.fulfill::registerPredicateObligation)
                bound
            }
            ctx.fulfill.selectWherePossible()

            return SimpleParamEnv(bounds2.map { ctx.fullyResolve(it) })
        }
    }
}

class SimpleParamEnv(private val callerBounds: List<TraitRef>) : ParamEnv {
    override fun boundsFor(ty: Ty): Sequence<BoundElement<RsTraitItem>> =
        callerBounds.asSequence().filter { it.selfTy.isEquivalentTo(ty) }.map { it.trait }
}

class LazyParamEnv(private val parentItem: RsGenericDeclaration) : ParamEnv {
    override fun boundsFor(ty: Ty): Sequence<BoundElement<RsTraitItem>> {
        return if (ty is TyTypeParameter) {
            val additionalBounds = when (val parameter = ty.parameter) {
                is TyTypeParameter.Named -> if (parameter.parameter.owner != parentItem) {
                    parentItem.whereClause?.wherePredList.orEmpty().asSequence()
                        .filter { (it.typeReference?.skipParens() as? RsPathType)?.path?.reference?.resolve() == parameter.parameter }
                        .flatMap { it.typeParamBounds?.polyboundList.orEmpty() }
                } else {
                    emptySequence()
                }
                TyTypeParameter.Self -> if (parentItem !is RsTraitOrImpl) {
                    parentItem.whereClause?.wherePredList.orEmpty().asSequence()
                        .filter { (it.typeReference?.skipParens() as? RsPathType)?.path?.kind == PathKind.CSELF }
                        .flatMap { it.typeParamBounds?.polyboundList.orEmpty() }
                } else {
                    emptySequence()
                }
            }.mapNotNull {
                if (it.hasQ) return@mapNotNull null // Ignore `T: ?Sized`
                it.bound.traitRef?.resolveToBoundTrait()
            }
            @Suppress("DEPRECATION")
            ty.getTraitBoundsTransitively().asSequence() + additionalBounds
        } else {
            emptySequence()
        }
    }
}

class ImplLookup(
    private val project: Project,
    private val containingCrate: Crate,
    val items: KnownItems,
    private val paramEnv: ParamEnv,
    context: RsElement? = null
) {
    // Non-concurrent HashMap and lazy(NONE) are safe here because this class isn't shared between threads
    private val traitSelectionCache: MutableMap<TraitRef, SelectionResult<SelectionCandidate>> = hashMapOf()
    private val findImplsAndTraitsCache: MutableMap<Ty, List<TraitImplSource>> = hashMapOf()
    private val indexCache = RsImplIndexAndTypeAliasCache.getInstance(project)
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

    private val implsFromNestedMacros: Map<TyFingerprint, List<RsCachedImplItem>> by lazy(NONE) {
        @Suppress("NAME_SHADOWING")
        val context = context ?: return@lazy emptyMap()
        val ancestorItem = context.contexts.withPrevious().findLast { (it, prev) ->
            it is RsFunction && (prev == null || prev is RsBlock)
                || it is RsFile && it.isInsideInjection
        }?.first as? RsElement
            ?: return@lazy emptyMap()
        ancestorItem.implsFromNestedMacros
    }

    val ctx: RsInferenceContext by lazy(NONE) {
        RsInferenceContext(project, this, items)
    }

    fun getEnvBoundTransitivelyFor(ty: Ty): Sequence<BoundElement<RsTraitItem>> {
        return paramEnv.boundsFor(ty)
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
                findExplicitImpls(ty) { implsAndTraits += it.explicitImpl; false }
            }
            is TyFunction -> {
                findExplicitImpls(ty) { implsAndTraits += it.explicitImpl; false }
                implsAndTraits += fnTraits.map { TraitImplSource.Object(it) }
                listOfNotNull(items.Clone, items.Copy).mapTo(implsAndTraits) { TraitImplSource.Builtin(it) }
            }
            is TyAnon -> {
                ty.getTraitBoundsTransitively()
                    .distinctBy { it.element }
                    .mapTo(implsAndTraits) { TraitImplSource.Object(it.element) }
                findBlanketImpls().forEach {
                    if (!it.isNegativeImpl) {
                        implsAndTraits += it.explicitImpl
                    }
                }
            }
            is TyProjection -> {
                val subst = ty.trait.subst + mapOf(TyTypeParameter.self() to ty.type).toTypeSubst()
                implsAndTraits += ty.trait.element.bounds.asSequence()
                    .filter { ctx.probe { ctx.combineTypes(it.selfTy.substitute(subst), ty) }.isOk }
                    .flatMap { it.trait.getFlattenHierarchy().asSequence() }
                    .map { TraitImplSource.ProjectionBound(it.element) }
                    .distinct()

            }
            is TyUnknown -> Unit
            else -> {
                implsAndTraits += findDerivedTraits(ty).map { TraitImplSource.Derived(it) }
                findExplicitImpls(ty) { implsAndTraits += it.explicitImpl; false }
                if (ty is TyTuple || ty is TyUnit) {
                    listOfNotNull(items.Clone, items.Copy).mapTo(implsAndTraits) { TraitImplSource.Builtin(it) }
                }
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

    private fun findExplicitImpls(selfTy: Ty, processor: RsProcessor<RsCachedImplItem>): Boolean {
        return processTyFingerprintsWithAliases(selfTy) { tyFingerprint ->
            findExplicitImplsWithoutAliases(selfTy, tyFingerprint, processor)
        }
    }

    private fun findExplicitImplsWithoutAliases(selfTy: Ty, tyf: TyFingerprint, processor: RsProcessor<RsCachedImplItem>): Boolean {
        val impls = findPotentialImpls(tyf)
        return impls.any { cachedImpl ->
            if (cachedImpl.isNegativeImpl) return@any false
            val (type, generics, constGenerics) = cachedImpl.typeAndGenerics ?: return@any false
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
            val aliases = findPotentialAliases(fingerprint)
            val result = aliases.any {
                val name = it.name ?: return@any false
                val aliasFingerprint = TyFingerprint(name)
                val isAppropriateAlias = run {
                    val (declaredType, generics, constGenerics) = it.typeAndGenerics
                    canCombineTypes(selfTy, declaredType, generics, constGenerics)
                }
                isAppropriateAlias && set.add(aliasFingerprint) && processor(aliasFingerprint)
            }
            if (result) return true
        }
        return processor(TyFingerprint.TYPE_PARAMETER_OR_MACRO_FINGERPRINT)
    }

    private fun findPotentialImpls(tyf: TyFingerprint): Sequence<RsCachedImplItem> =
        indexCache.findPotentialImpls(tyf)
            .asSequence()
            .filter { useImplsFromCrate(it.containingCrates) }
            .plus(implsFromNestedMacros[tyf].orEmpty())

    private fun findPotentialAliases(tyf: TyFingerprint) =
        indexCache.findPotentialAliases(tyf)
            .asSequence()
            .filter { useImplsFromCrate(it.containingCrates) }

    private fun useImplsFromCrate(crates: List<Crate>): Boolean =
        crates.any { containingCrate.hasTransitiveDependencyOrSelf(it) }

    private fun canCombineTypes(
        ty1: Ty,
        ty2: Ty,
        genericsForTy2: List<TyTypeParameter>,
        constGenericsForTy2: List<CtConstParameter>
    ): Boolean {
        // Optimization: early handle common cases in order to avoid heavier probe/substitute/ctx.combineTypes
        if (genericsForTy2.size < 5) {
            if (ty2 in genericsForTy2) return true
            if (ty2 is TyReference && ty2.referenced in genericsForTy2) {
                return ty1 is TyReference && ty1.mutability == ty2.mutability
            }
        }

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

    /** return impls for a generic type `impl<T> Trait for T {}` */
    private fun findBlanketImpls(): Sequence<RsCachedImplItem> {
        return findPotentialImpls(TyFingerprint.TYPE_PARAMETER_OR_MACRO_FINGERPRINT)
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
        val result = selectWithoutConfirm(ref, BoundConstness.NotConst, recursionDepth)
        val candidate = result.ok() ?: return result.map { error("unreachable") }
        // TODO optimize it. Obligations may be already evaluated, so we don't need to re-evaluated it
        if (!canEvaluateObligations(ref, candidate, recursionDepth)) return SelectionResult.Err
        return result
    }

    fun select(ref: TraitRef, recursionDepth: Int = 0): SelectionResult<Selection> =
        select(ref, BoundConstness.NotConst, recursionDepth)

    /**
     * If the TraitRef is a something like
     *     `T : Foo<U>`
     * here we select an impl of the trait `Foo<U>` for the type `T`, i.e.
     *     `impl Foo<U> for T {}`
     */
    fun select(ref: TraitRef, constness: BoundConstness, recursionDepth: Int = 0): SelectionResult<Selection> =
        selectWithoutConfirm(ref, constness, recursionDepth).andThen { confirmCandidate(ref, it, recursionDepth) }

    private fun selectWithoutConfirm(
        ref: TraitRef,
        constness: BoundConstness,
        recursionDepth: Int
    ): SelectionResult<SelectionCandidate> {
        if (recursionDepth > DEFAULT_RECURSION_LIMIT) {
            TypeInferenceMarks.TraitSelectionOverflow.hit()
            return SelectionResult.Err
        }
        testAssert { !ctx.hasResolvableTypeVars(ref) }

        // BACKCOMPAT rustc 1.61.0: there are `~const Drop` bounds in stdlib that must be always satisfied in
        // a non-const context. In newer rustc version these bounds are removed
        if (constness == BoundConstness.ConstIfConst && ref.trait.element == items.Drop) {
            return SelectionResult.Ok(ParamCandidate(BoundElement(ref.trait.element)))
        }

        return traitSelectionCache.getOrPut(freshen(ref)) { selectCandidate(ref, recursionDepth) }
    }

    private fun selectCandidate(ref: TraitRef, recursionDepth: Int): SelectionResult<SelectionCandidate> {
        if (ref.selfTy is TyInfer.TyVar) {
            return SelectionResult.Ambiguous
        }
        if (ref.selfTy is TyReference && ref.selfTy.referenced is TyInfer.TyVar) {
            // This condition is related to TyFingerprint internals: TyFingerprint should not be created for
            // TyInfer.TyVar, and TyReference is a single special case: it unwraps during TyFingerprint creation
            return SelectionResult.Ambiguous
        }

        val candidateSet = assembleCandidates(ref)

        if (candidateSet.ambiguous) {
            return SelectionResult.Ambiguous
        }
        val candidates = candidateSet.list.filter { it !is ImplCandidate.ExplicitImpl || !it.isNegativeImpl }

        return when (candidates.size) {
            0 -> SelectionResult.Err
            1 -> SelectionResult.Ok(candidates.single())
            else -> {
                val filtered = candidates.filterTo(mutableListOf()) {
                    canEvaluateObligations(ref, it, recursionDepth)
                }

                when (filtered.size) {
                    0 -> SelectionResult.Err
                    1 -> SelectionResult.Ok(filtered.single())
                    else -> {
                        var i = 0
                        while (i < filtered.size) {
                            val isDup = (0 until filtered.size).filter { it != i }.any {
                                candidateShouldBeDroppedInFavorOf(ref.selfTy, filtered[i], filtered[it])
                            }
                            if (isDup) {
                                filtered.swapRemoveAt(i)
                            } else {
                                i++
                                if (i > 1) {
                                    return SelectionResult.Ambiguous
                                }
                            }
                        }
                        filtered.singleOrNull()
                            ?.let { SelectionResult.Ok(it) }
                            ?: SelectionResult.Ambiguous
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

    // https://github.com/rust-lang/rust/blob/3a90bedb332d/compiler/rustc_trait_selection/src/traits/select/mod.rs#L1522
    private fun candidateShouldBeDroppedInFavorOf(
        selfTy: Ty,
        victim: SelectionCandidate,
        other: SelectionCandidate
    ): Boolean {
        if (victim.isEquivalentTo(other)) {
            return true
        }
        return when {
            other is BuiltinCandidate && !other.hasNested
                || other == ConstDestructCandidate -> true
            victim is BuiltinCandidate && !victim.hasNested
                || victim == ConstDestructCandidate -> false

            other is ParamCandidate && (
                victim is ImplCandidate
//                    || victim is ClosureCandidate
//                    || victim is GeneratorCandidate
                    || victim is FnPointerCandidate
//                    || victim is BuiltinObjectCandidate
                    || victim is BuiltinUnsizeCandidate
//                    || victim is TraitUpcastingUnsizeCandidate
                    || victim is BuiltinCandidate
//                    || victim is TraitAliasCandidate
                    || victim is ObjectCandidate
                    || victim is ProjectionCandidate
                ) -> WinnowParamCandidateWins.hitOnTrue(!(selfTy.isGlobal && other.bound.isGlobal))

            (
                other is ImplCandidate
//                    || other is ClosureCandidate
//                    || other is GeneratorCandidate
                    || other is FnPointerCandidate
//                    || other is BuiltinObjectCandidate
                    || other is BuiltinUnsizeCandidate
//                    || other is TraitUpcastingUnsizeCandidate
                    || other is BuiltinCandidate
//                    || other is TraitAliasCandidate
                    || other is ObjectCandidate
                    || other is ProjectionCandidate
                ) && victim is ParamCandidate -> WinnowParamCandidateLoses.hitOnTrue(selfTy.isGlobal && victim.bound.isGlobal)

            (other is ObjectCandidate || other is ProjectionCandidate) && (
                victim is ImplCandidate
//                    || victim is ClosureCandidate
//                    || victim is GeneratorCandidate
                    || victim is FnPointerCandidate
//                    || victim is BuiltinObjectCandidate
                    || victim is BuiltinUnsizeCandidate
//                    || victim is TraitUpcastingUnsizeCandidate
                    || victim is BuiltinCandidate
//                    || victim is TraitAliasCandidate
                ) -> TypeInferenceMarks.WinnowObjectOrProjectionCandidateWins.hitOnTrue(true)

            (
                other is ImplCandidate
//                    || other is ClosureCandidate
//                    || other is GeneratorCandidate
                    || other is FnPointerCandidate
//                    || other is BuiltinObjectCandidate
                    || other is BuiltinUnsizeCandidate
//                    || other is TraitUpcastingUnsizeCandidate
                    || other is BuiltinCandidate
//                    || other is TraitAliasCandidate
                ) && (victim is ObjectCandidate || victim is ProjectionCandidate) -> false

            // basic specialization
            victim is ImplCandidate.ExplicitImpl && victim.formalSelfTy is TyTypeParameter
                && other is ImplCandidate.ExplicitImpl && other.formalSelfTy !is TyTypeParameter -> {
                TypeInferenceMarks.WinnowSpecialization.hit()
                true
            }

            else -> false
        }
    }

    // https://github.com/rust-lang/rust/blob/3a90bedb332d/compiler/rustc_trait_selection/src/traits/select/candidate_assembly.rs#L242
    private fun assembleCandidates(ref: TraitRef): SelectionCandidateSet {
        val candidates = SelectionCandidateSet()
        val trait = ref.trait.element
        when (trait) {
            items.Copy -> {
                assembleCandidatesFromImpls(ref, candidates)
                assembleBuiltinBoundCandidates(copyCloneConditions(ref.selfTy), candidates)
            }
            items.Sized -> assembleBuiltinBoundCandidates(sizedConditions(ref), candidates)
            items.Unsize -> assembleCandidatesForUnsizing(ref, candidates)
            items.Destruct -> candidates.list.add(ConstDestructCandidate)
            else -> {
                if (trait == items.Clone) {
                    assembleBuiltinBoundCandidates(copyCloneConditions(ref.selfTy), candidates)
                }

                assembleFnPointerCandidates(ref, candidates)
                assembleCandidatesFromImpls(ref, candidates)
                assembleCandidatesFromObjectTy(ref, candidates)
            }
        }

        assembleCandidatesFromProjectedTys(ref, candidates)
        assembleCandidatesFromCallerBounds(ref, candidates)

        // Auto implementations have lower priority, so we only consider triggering a default if
        // there is no other impl that can apply. Note that `candidates.list` also contains negative
        // impl like `impl !Sync for Foo {}`
        if (candidates.list.isEmpty() && trait.isAuto) {
            assembleCandidatesFromAutoImpls(ref, candidates)
        }

        return candidates
    }

    private fun assembleBuiltinBoundCandidates(conditions: BuiltinImplConditions, candidates: SelectionCandidateSet) {
        when (conditions) {
            is BuiltinImplConditions.Where -> {
                candidates.list += BuiltinCandidate(hasNested = conditions.nested.isNotEmpty())
            }
            BuiltinImplConditions.None -> Unit
            BuiltinImplConditions.Ambiguous -> candidates.ambiguous = true
        }.exhaustive
    }

    // https://github.com/rust-lang/rust/blob/3a90bedb332d/compiler/rustc_trait_selection/src/traits/select/mod.rs#L1820
    private fun copyCloneConditions(selfTy: Ty): BuiltinImplConditions {
        return when (selfTy) {
            is TyInfer.IntVar, is TyInfer.FloatVar, is TyFunction, TyUnknown, is TyUnit -> {
                BuiltinImplConditions.Where(emptyList())
            }
            is TyInteger, is TyFloat, is TyBool, is TyChar, is TyPointer, is TyNever, is TyReference, is TyArray -> {
                // Implementations provided in libcore
                BuiltinImplConditions.None
            }
            is TyTuple -> BuiltinImplConditions.Where(selfTy.types)
            else -> BuiltinImplConditions.None
        }
    }

    /** See `org.rust.lang.core.type.RsImplicitTraitsTest` */
    private fun sizedConditions(ref: TraitRef): BuiltinImplConditions {
        return when (val selfTy = ref.selfTy) {
            is TyInfer.IntVar,
            is TyInfer.FloatVar,
            is TyNumeric,
            is TyBool,
            is TyFunction,
            is TyPointer,
            is TyReference,
            is TyChar,
            is TyArray,
            TyNever,
            is TyUnit,
            is TyUnknown -> BuiltinImplConditions.Where(emptyList())

            is TyStr, is TySlice, is TyTraitObject /*is TyForeign*/ -> BuiltinImplConditions.None

            is TyTuple -> BuiltinImplConditions.Where(listOf(selfTy.types.last()))

            is TyAdt -> BuiltinImplConditions.Where(listOfNotNull(selfTy.structTail()))

            is TyProjection, is TyTypeParameter, is TyAnon -> BuiltinImplConditions.None

            is TyInfer.TyVar -> BuiltinImplConditions.Ambiguous

            else -> BuiltinImplConditions.None
        }
    }

    private fun assembleFnPointerCandidates(ref: TraitRef, candidates: SelectionCandidateSet) {
        val element = ref.trait.element
        getTyFunctionImpls(ref.selfTy).filter { be ->
            be.element == element && ctx.probe {
                ctx.combineTypePairs(be.subst.zipTypeValues(ref.trait.subst)).isOk &&
                    ctx.combineConstPairs(be.subst.zipConstValues(ref.trait.subst)).isOk
            }
        }.mapTo(candidates.list) { FnPointerCandidate }
    }

    // TODO simplify
    private fun getTyFunctionImpls(ty: Ty): Collection<BoundElement<RsTraitItem>> {
        if (ty is TyFunction) {
            val fnOnceOutput = fnOnceOutput
            val args = if (ty.paramTypes.isEmpty()) TyUnit.INSTANCE else TyTuple(ty.paramTypes)
            val assoc = if (fnOnceOutput != null) mapOf(fnOnceOutput to ty.retType) else emptyMap()
            return fnTraits.map { it.withSubst(args).copy(assoc = assoc) } +
                listOfNotNull(items.Clone, items.Copy).map { BoundElement(it) }
        }

        return emptyList()
    }

    private fun assembleCandidatesFromObjectTy(ref: TraitRef, candidates: SelectionCandidateSet) {
        if (ref.selfTy is TyTraitObject) {
            ref.selfTy.getTraitBoundsTransitively().find { it.element == ref.trait.element }
                ?.let { candidates.list.add(ObjectCandidate) }
        }
    }

    private fun assembleCandidatesFromProjectedTys(ref: TraitRef, candidates: SelectionCandidateSet) {
        val selfTy = ref.selfTy
        if (selfTy is TyProjection) {
            val subst = selfTy.trait.subst + mapOf(TyTypeParameter.self() to selfTy.type).toTypeSubst()
            selfTy.trait.element.bounds.asSequence()
                .filter { ctx.probe { ctx.combineTypes(it.selfTy.substitute(subst), selfTy) }.isOk }
                .flatMap { it.trait.getFlattenHierarchy().asSequence() }
                .distinct()
                .filter { ctx.probe { ctx.combineBoundElements(it.substitute(subst), ref.trait) } }
                .forEach { candidates.list.add(ProjectionCandidate(it)) }
        }
        if (selfTy is TyAnon) {
            selfTy.getTraitBoundsTransitively().find { it.element == ref.trait.element }
                ?.let { candidates.list.add(ObjectCandidate) }
        }
    }

    private fun assembleCandidatesFromCallerBounds(ref: TraitRef, candidates: SelectionCandidateSet) {
        getEnvBoundTransitivelyFor(ref.selfTy)
            .filter { ctx.probe { ctx.combineBoundElements(it, ref.trait) } }
            .mapTo(candidates.list) { ParamCandidate(it) }
    }

    private fun assembleCandidatesFromImpls(ref: TraitRef, candidates: SelectionCandidateSet) {
        assembleDerivedCandidates(ref, candidates)
        assembleCandidatesFromImpls(ref) {
            candidates.list += it
            false
        }
    }

    private fun assembleCandidatesFromImpls(ref: TraitRef, processor: RsProcessor<SelectionCandidate>): Boolean {
        return processTyFingerprintsWithAliases(ref.selfTy) { tyFingerprint ->
            assembleImplCandidatesWithoutAliases(ref, tyFingerprint, processor)
        }
    }

    private fun assembleImplCandidatesWithoutAliases(ref: TraitRef, tyf: TyFingerprint, processor: RsProcessor<SelectionCandidate>): Boolean {
        val impls = findPotentialImpls(tyf)
        return impls.any {
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
                prepareSubstAndTraitRefRaw(ctx, generics, constGenerics, formalSelfTy, formalTraitRef, 0)
            ctx.combineTraitRefs(implTraitRef, ref)
        }
        if (!probe) return null
        return ImplCandidate.ExplicitImpl(impl, formalSelfTy, formalTraitRef, isNegativeImpl)
    }

    private fun assembleDerivedCandidates(ref: TraitRef, candidates: SelectionCandidateSet) {
        (ref.selfTy as? TyAdt)?.item?.derivedTraits.orEmpty()
            // select only std traits because we are sure
            // that they are resolved correctly
            .filter { it.isKnownDerivable }
            .filter { it == ref.trait.element }
            .mapTo(candidates.list) { ImplCandidate.DerivedTrait(it) }
    }

    // Mirrors rustc's `assemble_candidates_for_unsizing`
    // https://github.com/rust-lang/rust/blob/97d48bec2d/compiler/rustc_trait_selection/src/traits/select/candidate_assembly.rs#L741
    private fun assembleCandidatesForUnsizing(ref: TraitRef, candidates: SelectionCandidateSet) {
        val source = ref.selfTy
        val target = ref.trait.singleParamValue
        when {
            // Trait+Kx+'a -> Trait+Ky+'b (upcasts)
            source is TyTraitObject && target is TyTraitObject -> {
                candidates.list += BuiltinUnsizeCandidate // TODO
            }

            // `T` -> `Trait`
            target is TyTraitObject -> candidates.list += BuiltinUnsizeCandidate

            source is TyInfer.TyVar || target is TyInfer.TyVar -> {
                candidates.ambiguous = true
            }

            // `[T; n]` -> `[T]`
            source is TyArray && target is TySlice -> candidates.list += BuiltinUnsizeCandidate

            // `Struct<T>` -> `Struct<U>`
            source is TyAdt && target is TyAdt && source.item == target.item && source.item is RsStructItem
                && source.item.kind == RsStructKind.STRUCT -> candidates.list += BuiltinUnsizeCandidate

            // `(.., T)` -> `(.., U)`
            source is TyTuple && target is TyTuple && source.types.size == target.types.size ->
                candidates.list += BuiltinUnsizeCandidate
        }
    }

    private fun assembleCandidatesFromAutoImpls(ref: TraitRef, candidates: SelectionCandidateSet) {
        // For now, just think that any type is Sync + Send
        // TODO implement auto trait logic
        candidates.list += ParamCandidate(BoundElement(ref.trait.element))
    }

    // https://github.com/rust-lang/rust/blob/3a90bedb332d/compiler/rustc_trait_selection/src/traits/select/confirmation.rs#L40
    private fun confirmCandidate(
        ref: TraitRef,
        candidate: SelectionCandidate,
        recursionDepth: Int
    ): SelectionResult<Selection> {
        return when (candidate) {
            is BuiltinCandidate -> {
                SelectionResult.Ok(confirmBuiltinCandidate(ref, recursionDepth, candidate.hasNested))
            }
            is ParamCandidate -> {
                SelectionResult.Ok(confirmParamCandidate(ref, candidate))
            }
            is ImplCandidate -> {
                SelectionResult.Ok(confirmImplCandidate(ref, candidate, recursionDepth))
            }
            is ProjectionCandidate -> {
                SelectionResult.Ok(confirmProjectionCandidate(ref, candidate))
            }
            ObjectCandidate -> {
                SelectionResult.Ok(confirmObjectCandidate(ref))
            }
            BuiltinUnsizeCandidate -> {
                confirmBuiltinUnsizeCandidate(ref, recursionDepth)
            }
            is FnPointerCandidate -> {
                SelectionResult.Ok(confirmFnPointerCandidate(ref))
            }
            ConstDestructCandidate -> {
                SelectionResult.Ok(Selection(ref.trait.element, emptyList()))
            }
        }
    }

    private fun confirmBuiltinCandidate(
        ref: TraitRef,
        recursionDepth: Int,
        hasNested: Boolean,
    ): Selection {
        val trait = ref.trait.element
        val obligations = if (hasNested) {
            val conditions = when (trait) {
                items.Copy, items.Clone -> copyCloneConditions(ref.selfTy)
                items.Sized -> sizedConditions(ref)
                else -> {
                    error("unexpected builtin trait $trait")
                }
            } as? BuiltinImplConditions.Where ?: error("obligation $ref had matched a builtin impl but now doesn't")
            collectPredicatesForTypes(recursionDepth + 1, trait, conditions.nested)
        } else {
            emptyList()
        }
        return Selection(trait, obligations)
    }

    private fun collectPredicatesForTypes(
        recursionDepth: Int,
        trait: RsTraitItem,
        types: List<Ty>
    ): List<Obligation> {
        return types.flatMap {
            val (normTy, obligations) = ctx.normalizeAssociatedTypesIn(it, recursionDepth)
            obligations + listOf(Obligation(recursionDepth, Predicate.Trait(TraitRef(normTy, trait.withSubst()))))
        }
    }

    private fun confirmParamCandidate(ref: TraitRef, candidate: ParamCandidate): Selection {
        testAssert { !candidate.bound.containsTyOfClass(TyInfer::class.java) }
        ctx.combineBoundElements(candidate.bound, ref.trait)
        return Selection(candidate.bound.element, emptyList())
    }

    private fun confirmImplCandidate(
        ref: TraitRef,
        candidate: ImplCandidate,
        recursionDepth: Int
    ): Selection {
        when (candidate) {
            is ImplCandidate.DerivedTrait ->
                return confirmDerivedCandidate(ref, candidate, recursionDepth)
            is ImplCandidate.ExplicitImpl -> Unit
        }
        testAssert { !candidate.formalSelfTy.containsTyOfClass(TyInfer::class.java) }
        testAssert { !candidate.formalTrait.containsTyOfClass(TyInfer::class.java) }
        val (subst, preparedRef, typeObligations) = candidate.prepareSubstAndTraitRef(ctx, recursionDepth + 1)
        ctx.combineTraitRefs(ref, preparedRef)
        // pre-resolve type vars to simplify caching of already inferred obligation on fulfillment
        val candidateSubst = ctx.resolveTypeVarsIfPossible(subst) +
            mapOf(TyTypeParameter.self() to ref.selfTy).toTypeSubst()
        val obligations = typeObligations +
            ctx.instantiateBounds(candidate.impl.predicates, candidateSubst, recursionDepth + 1)
        return Selection(candidate.impl, obligations, candidateSubst)
    }

    private fun confirmDerivedCandidate(
        ref: TraitRef,
        candidate: ImplCandidate.DerivedTrait,
        recursionDepth: Int
    ): Selection {
        val selfTy = ref.selfTy as TyAdt // Guaranteed by `assembleCandidates`
        // For `#[derive(Clone)] struct S<T>(T);` add `T: Clone` obligation
        val obligations = selfTy.typeArguments.map {
            Obligation(recursionDepth + 1, Predicate.Trait(TraitRef(it, BoundElement(candidate.item))))
        }
        return Selection(candidate.item, obligations)
    }

    private fun confirmProjectionCandidate(ref: TraitRef, candidate: ProjectionCandidate): Selection {
        ref.selfTy as TyProjection
        val subst = ref.selfTy.trait.subst + mapOf(TyTypeParameter.self() to ref.selfTy.type).toTypeSubst()
        ctx.combineTraitRefs(ref, TraitRef(ref.selfTy, candidate.bound.substitute(subst)))
        return Selection(ref.trait.element, emptyList())
    }

    private fun confirmObjectCandidate(ref: TraitRef): Selection {
        val traits = when (ref.selfTy) {
            is TyTraitObject -> ref.selfTy.getTraitBoundsTransitively()
            is TyAnon -> ref.selfTy.getTraitBoundsTransitively()
            else -> error("unreachable")
        }
        // should be nonnull because already checked in `assembleCandidatesFromObjectTy`
        val be = traits.find { it.element == ref.trait.element } ?: error("Corrupted trait selection")
        ctx.combineBoundElements(be, ref.trait)
        return Selection(be.element, emptyList())
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
                val lastFieldType = lastField.typeReference?.rawType ?: return SelectionResult.Err
                val unsizingParams = hashSetOf<TyTypeParameter>()
                // TODO consider const params
                lastFieldType.visitTypeParameters { ty ->
                    unsizingParams += ty
                    false
                }
                for (field in fields) {
                    if (field == lastField) break
                    val fieldType = field.typeReference?.rawType ?: continue
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

    private fun confirmFnPointerCandidate(ref: TraitRef): Selection {
        val impl = getTyFunctionImpls(ref.selfTy).first { be ->
            be.element == ref.trait.element && ctx.probe {
                ctx.combineTypePairs(be.subst.zipTypeValues(ref.trait.subst)).isOk &&
                    ctx.combineConstPairs(be.subst.zipConstValues(ref.trait.subst)).isOk
            }
        }
        ctx.combineBoundElements(impl, ref.trait)
        return Selection(impl.element, emptyList(), mapOf(TyTypeParameter.self() to ref.selfTy).toTypeSubst())
    }

    fun coercionSequence(baseTy: Ty): Autoderef = Autoderef(this, ctx, baseTy)

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
                ?.let { ctx.normalizeAssociatedTypesIn(it, recursionDepth + 1) }
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
                ?.let { ctx.normalizeAssociatedTypesIn(it, recursionDepth + 1) }
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
                    ?: lookupAssocTypeInBounds(getTyFunctionImpls(selfTy).asSequence(), res.impl, assocType)
                    ?: (selfTy as? TyAnon)?.let { lookupAssocTypeInBounds(it.getTraitBoundsTransitively().asSequence(), res.impl, assocType) }
            }
        }
    }

    private fun lookupAssocTypeInSelection(selection: Selection, assoc: RsTypeAlias): Ty? =
        selection.impl.associatedTypesTransitively.find { it.name == assoc.name }?.typeReference?.rawType?.substitute(selection.subst)

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
                val (ancestor, cameFrom) = psi.contexts.withPrevious().find { (it, _) ->
                    it is RsWherePred || it is RsBound || it is RsImplItem
                } ?: (null to null)
                val isInTraitBoundOrImplSignature = ancestor != null && (ancestor !is RsImplItem
                    || cameFrom is RsTraitRef || cameFrom is RsTypeReference)
                if (isInTraitBoundOrImplSignature) {
                    // We have to calculate type parameter bounds lazily in this case.
                    // Otherwise, we run into infinite recursion
                    LazyParamEnv(parentItem)
                } else {
                    ParamEnv.buildFor(parentItem)
                }
            } else if (parentItem != null) {
                ParamEnv.buildFor(parentItem)
            } else {
                ParamEnv.EMPTY
            }
            return ImplLookup(psi.project, psi.containingCrate, psi.knownItems, paramEnv, psi)
        }
    }
}

private class SelectionCandidateSet(
    val list: MutableList<SelectionCandidate> = mutableListOf(),
    var ambiguous: Boolean = false,
)

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
    data class BuiltinCandidate(
        /** `false` if there are no *further* obligations */
        val hasNested: Boolean
    ) : SelectionCandidate()

    data class ParamCandidate(val bound: BoundElement<RsTraitItem>) : SelectionCandidate() {
        override fun isEquivalentTo(other: SelectionCandidate): Boolean =
            other is ParamCandidate && bound.isEquivalentTo(other.bound)
    }

    sealed class ImplCandidate : SelectionCandidate() {
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
        data class ExplicitImpl(
            val impl: RsImplItem,
            // We can always extract these values from impl, but it's better to cache them
            val formalSelfTy: Ty,
            val formalTrait: BoundElement<RsTraitItem>,
            /** `true` if it is `!` impl: `impl !Sync for Foo {}` */
            val isNegativeImpl: Boolean
        ) : ImplCandidate() {
            override fun isEquivalentTo(other: SelectionCandidate): Boolean =
                other is ExplicitImpl && impl == other.impl

            fun prepareSubstAndTraitRef(
                ctx: RsInferenceContext,
                recursionDepth: Int
            ): Triple<Substitution, TraitRef, List<Obligation>> = prepareSubstAndTraitRefRaw(
                ctx,
                impl.generics,
                impl.constGenerics,
                formalSelfTy,
                formalTrait,
                recursionDepth
            )
        }

        data class DerivedTrait(val item: RsTraitItem) : ImplCandidate()
    }

    // AutoImplCandidate

    data class ProjectionCandidate(val bound: BoundElement<RsTraitItem>) : SelectionCandidate() {
        override fun isEquivalentTo(other: SelectionCandidate): Boolean =
            other is ProjectionCandidate && bound.isEquivalentTo(other.bound)
    }

    /** @see ImplLookup.getTyFunctionImpls */
    object FnPointerCandidate : SelectionCandidate()

    object ObjectCandidate : SelectionCandidate()

    // BuiltinObjectCandidate

    object BuiltinUnsizeCandidate : SelectionCandidate()

    object ConstDestructCandidate : SelectionCandidate()

    /** @see Ty.isEquivalentTo */
    open fun isEquivalentTo(other: SelectionCandidate): Boolean = this == other
}

/** When does the builtin impl for `T: Trait` apply? */
private sealed class BuiltinImplConditions {
    /** The impl is conditional on `T1, T2, ...: Trait` */
    data class Where(val nested: List<Ty>) : BuiltinImplConditions()

    /** There is no built-in impl. There may be some other candidate (a where-clause or user-defined impl) */
    object None : BuiltinImplConditions()

    /** It is unknown whether there is an impl */
    object Ambiguous : BuiltinImplConditions()
}

private fun prepareSubstAndTraitRefRaw(
    ctx: RsInferenceContext,
    typeGenerics: List<TyTypeParameter>,
    constGenerics: List<CtConstParameter>,
    formalSelfTy: Ty,
    formalTrait: BoundElement<RsTraitItem>,
    recursionDepth: Int
): Triple<Substitution, TraitRef, List<Obligation>> {
    val subst = Substitution(
        typeSubst = typeGenerics.associateWith { ctx.typeVarForParam(it) },
        constSubst = constGenerics.associateWith { ctx.constVarForParam(it) }
    )
    val substSelfTy = formalSelfTy.substitute(subst)
    val boundSubst = formalTrait.substitute(subst)
        .substitute(mapOf(TyTypeParameter.self() to substSelfTy).toTypeSubst())
        .subst
    val rawImplTraitRef = TraitRef(substSelfTy, BoundElement(formalTrait.element, boundSubst))
    val (implTraitRef, obligations) = ctx.normalizeAssociatedTypesIn(rawImplTraitRef, recursionDepth)
    return Triple(subst, implTraitRef, obligations)
}

private fun <T : Ty> T.withObligations(obligations: List<Obligation> = emptyList()): TyWithObligations<T> =
    TyWithObligations(this, obligations)

/**
 * The Rust plugin does not index impls expanded from macro calls located inside function bodies, so if we want to take
 * them into account during type inference, we have to collect them manually
 */
private val RsElement.implsFromNestedMacros: Map<TyFingerprint, List<RsCachedImplItem>>
    get() = CachedValuesManager.getCachedValue(this, IMPLS_FROM_NESTED_MACROS_KEY) {
        val result = doGetImplsFromNestedMacros(this)
        if (this is RsFunction) {
            createCachedResult(result)
        } else {
            CachedValueProvider.Result(result, PsiModificationTracker.MODIFICATION_COUNT)
        }
    }

private val IMPLS_FROM_NESTED_MACROS_KEY: Key<CachedValue<Map<TyFingerprint, List<RsCachedImplItem>>>> =
    Key.create("IMPLS_FROM_NESTED_MACROS_KEY")

private fun doGetImplsFromNestedMacros(element: RsElement): Map<TyFingerprint, List<RsCachedImplItem>> {
    val body = when (element) {
        is RsFunction -> element.block ?: return emptyMap()
        is RsFile -> element
        else -> error("Unexpected element type: $element")
    }

    val macroCalls = mutableListOf<RsPossibleMacroCall>()
    val impls = mutableListOf<RsImplItem>()

    collectNestedMacroCallsAndImpls(body, macroCalls, if (element.isInsideInjection) impls else null)

    while (macroCalls.isNotEmpty()) {
        val macroCall = macroCalls.removeLast()

        // Optimization: skip hardcoded special macros
        if (macroCall is RsMacroCall && macroCall.macroArgument == null) {
            val macroDef = macroCall.resolveToMacro()
            if (macroDef != null && macroDef.containingCrate.origin == PackageOrigin.STDLIB) {
                continue
            }
        }

        val expansion = macroCall.expansion ?: continue
        for (expandedElement in expansion.elements) {
            collectNestedMacroCallsAndImpls(expandedElement, macroCalls, impls)
        }
    }

    val implMap = hashMapOf<TyFingerprint, MutableList<RsCachedImplItem>>()

    for (impl in impls) {
        val typeRef = impl.typeReference ?: continue
        for (tyf in TyFingerprint.create(typeRef, impl.typeParameters.mapNotNull { it.name })) {
            val list = implMap.getOrPut(tyf) { mutableListOf() }
            list += RsCachedImplItem.forImpl(impl)
        }
    }

    return implMap
}

private fun collectNestedMacroCallsAndImpls(
    root: RsElement,
    macroCalls: MutableList<RsPossibleMacroCall>,
    impls: MutableList<RsImplItem>?,
) {
    val possibleMacroCallsOrImpls = root.descendantsOfTypeOrSelf<RsAttrProcMacroOwner>()
    for (possibleMacroCall in possibleMacroCallsOrImpls) {
        when (val attr = possibleMacroCall.procMacroAttributeWithDerives) {
            is ProcMacroAttribute.Attr -> macroCalls += attr.attr
            is ProcMacroAttribute.Derive -> macroCalls += attr.derives
            ProcMacroAttribute.None -> when (possibleMacroCall) {
                is RsMacroCall -> macroCalls += possibleMacroCall
                is RsImplItem -> impls?.add(possibleMacroCall)
            }
        }
    }
}

