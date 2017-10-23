/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.indexes.RsImplIndex
import org.rust.lang.core.resolve.indexes.RsLangItemIndex
import org.rust.lang.core.stubs.index.RsNamedElementIndex
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.TraitRef
import org.rust.lang.core.types.infer.Obligation
import org.rust.lang.core.types.infer.Predicate
import org.rust.lang.core.types.infer.substitute
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.ty.Mutability.IMMUTABLE
import org.rust.lang.core.types.ty.Mutability.MUTABLE
import org.rust.lang.core.types.type
import org.rust.openapiext.ProjectCache
import org.rust.stdext.zipValues
import kotlin.LazyThreadSafetyMode.NONE

enum class StdDerivableTrait(val modName: String, val dependencies: Array<StdDerivableTrait> = emptyArray()) {
    Clone("clone"),
    Copy("marker", arrayOf(Clone)),
    Debug("fmt"),
    Default("default"),
    Hash("hash"),
    PartialEq("cmp"),
    Eq("cmp", arrayOf(PartialEq)),
    PartialOrd("cmp", arrayOf(PartialEq)),
    Ord("cmp", arrayOf(PartialOrd, Eq, PartialEq))
}

val StdDerivableTrait.withDependencies: List<StdDerivableTrait> get() = listOf(this, *dependencies)

val STD_DERIVABLE_TRAITS: Map<String, StdDerivableTrait> = StdDerivableTrait.values().associate { it.name to it }

private val RsTraitItem.isIndex: Boolean get() = langAttribute == "index"

private val RsTraitItem.typeParamSingle: TyTypeParameter?
    get() =
        typeParameterList?.typeParameterList?.singleOrNull()?.let { TyTypeParameter.named(it) }

class ImplLookup(private val project: Project, private val items: StdKnownItems) {
    // Non-concurrent HashMap and lazy(NONE) are safe here because this class isn't shared between threads
    private val primitiveTyHardcodedImplsCache = mutableMapOf<TyPrimitive, Collection<BoundElement<RsTraitItem>>>()
    private val fnTraits by lazy(NONE) {
        listOf("fn", "fn_mut", "fn_once").mapNotNull { RsLangItemIndex.findLangItem(project, it) }
    }
    val fnOutputParam by lazy(NONE) {
        RsLangItemIndex.findLangItem(project, "fn_once")?.let { findFreshAssociatedType(it, "Output") }
    }
    private val copyTrait: RsTraitItem? by lazy(NONE) {
        RsNamedElementIndex.findDerivableTraits(project, "Copy").firstOrNull()
    }
    private val derefTraitAndTarget: Pair<RsTraitItem, TyTypeParameter>? = run {
        val trait = RsLangItemIndex.findLangItem(project, "deref") ?: return@run null
        findFreshAssociatedType(trait, "Target")?.let { trait to it }
    }

    fun findImplsAndTraits(ty: Ty): Collection<BoundElement<RsTraitOrImpl>> {
        if (ty is TyTypeParameter) return ty.getTraitBoundsTransitively()
        return findImplsAndTraitsCache.getOrPut(project, ty) { rawFindImplsAndTraits(ty) }
    }

    private fun rawFindImplsAndTraits(ty: Ty): Collection<BoundElement<RsTraitOrImpl>> {
        return when (ty) {
            is TyTraitObject -> BoundElement(ty.trait).flattenHierarchy
            is TyFunction -> {
                val params = TyTuple(ty.paramTypes)
                val fnOutputSubst = fnOutputParam?.let { mapOf(it to ty.retType) } ?: emptySubstitution
                findSimpleImpls(ty) + fnTraits.map {
                    val subst = mutableMapOf<TyTypeParameter, Ty>()
                    subst.putAll(fnOutputSubst)
                    it.typeParamSingle?.let { subst.put(it, params) }
                    BoundElement(it, subst)
                }
            }
            is TyUnknown -> emptyList()
            else -> {
                findDerivedTraits(ty) + getHardcodedImpls(ty) + findSimpleImpls(ty)
            }
        }
    }

    private fun findDerivedTraits(ty: Ty): Collection<BoundElement<RsTraitItem>> {
        return (ty as? TyStructOrEnumBase)?.item?.derivedTraits.orEmpty()
            // select only std traits because we are sure
            // that they are resolved correctly
            .filter { it.isStdDerivable }
            .map { BoundElement(it, mapOf(TyTypeParameter.self(it) to ty)) }
    }

    private fun getHardcodedImpls(ty: Ty): Collection<BoundElement<RsTraitItem>> {
        // TODO this code should be completely removed after macros implementation
        when (ty) {
            is TyPrimitive -> {
                return primitiveTyHardcodedImplsCache.getOrPut(ty) {
                    val impls = mutableListOf<BoundElement<RsTraitItem>>()
                    if (ty is TyNumeric) {
                        // libcore/ops/arith.rs libcore/ops/bit.rs
                        impls += items.findBinOpTraits().map { it.substAssocType("Output", ty) }
                    }
                    if (ty != TyStr) {
                        // libcore/cmp.rs
                        if (ty != TyUnit) {
                            RsLangItemIndex.findLangItem(project, "eq")?.let {
                                impls.add(BoundElement(it, it.typeParamSingle?.let { mapOf(it to ty) } ?: emptySubstitution))
                            }
                        }
                        if (ty != TyUnit && ty != TyBool) {
                            RsLangItemIndex.findLangItem(project, "ord")?.let {
                                impls.add(BoundElement(it, it.typeParamSingle?.let { mapOf(it to ty) } ?: emptySubstitution))
                            }
                        }
                        if (ty !is TyFloat) {
                            items.findEqTrait()?.let { impls.add(BoundElement(it)) }
                            if (ty != TyUnit && ty != TyBool) {
                                items.findOrdTrait()?.let { impls.add(BoundElement(it)) }
                            }
                        }

                    }
                    // libcore/clone.rs
                    items.findCloneTrait()?.let { impls.add(BoundElement(it)) }
                    impls
                }
            }
            is TyStruct -> {
                items.findCoreTy("slice::Iter").unifyWith(ty, this).substitution()?.let { subst ->
                    val trait = items.findIteratorTrait() ?: return emptyList()
                    return listOf(trait.substAssocType("Item", TyReference(subst.valueByName("T"), IMMUTABLE)))
                }
                items.findCoreTy("slice::IterMut").unifyWith(ty, this).substitution()?.let { subst ->
                    val trait = items.findIteratorTrait() ?: return emptyList()
                    return listOf(trait.substAssocType("Item", TyReference(subst.valueByName("T"), MUTABLE)))
                }
            }
        }
        return emptyList()
    }

    private fun findSimpleImpls(ty: Ty): Collection<BoundElement<RsTraitOrImpl>> {
        val impls = RsImplIndex.findPotentialImpls(project, ty).mapNotNull { impl ->
            remapTypeParameters(impl, ty)?.let { BoundElement(impl, it) }
        }

        val traitToImpl = impls.associateBy { it.element.traitRef?.path?.reference?.resolve() }

        return impls.map { (impl, oldSubst) ->
            var subst = oldSubst
            impl.implementedTrait?.let { trait ->
                for ((element, _) in trait.flattenHierarchy.filter { it != trait }) {
                    traitToImpl[element]?.let { subst = subst.substituteInValues(it.subst) }
                }
            }
            BoundElement(impl, subst)
        }
    }

    private fun remapTypeParameters(impl: RsImplItem, receiver: Ty): Substitution? {
        val subst = impl.typeReference?.type?.unifyWith(receiver, this)?.substitution() ?: return null
        val associated = (impl.implementedTrait?.subst ?: emptyMap())
            .substituteInValues(subst)
        return subst + associated
    }

    /**
     * If the TraitRef is a something like
     *     `T : Foo<U>`
     * here we select an impl of the trait `Foo<U>` for the type `T`, i.e.
     *     `impl Foo<U> for T {}`
     */
    fun select(ref: TraitRef, recursionDepth: Int = 0): SelectionResult {
        val (trait, subst) = ref.trait
        val impls = findImplsAndTraits(ref.selfTy).filter { it.element.implementedTrait?.element == trait }
        if (impls.size == 1) {
            return okResultFor(impls.first(), subst, recursionDepth)
        }
        val suitable = impls.filter {
            zipValues(it.subst, subst).all { (v1, v2) ->
                v2 is TyInfer.TyVar || v1.unifyWith(v2, this) != UnifyResult.fail
            }
        }
        return when {
            suitable.isEmpty() -> SelectionResult.Err
            suitable.size == 1 -> okResultFor(suitable.first(), subst, recursionDepth)
            else -> SelectionResult.Ambiguous
        }
    }

    private fun okResultFor(
        impl: BoundElement<RsTraitOrImpl>,
        subst: Substitution,
        recursionDepth: Int
    ): SelectionResult.Ok {
        val (found, foundSubst) = impl
        return SelectionResult.Ok(found, subst.mapNotNull { (k, ty1) ->
            foundSubst[k]?.let { ty2 ->
                Obligation(
                    recursionDepth + 1,
                    Predicate.Equate(ty1, ty2)
                )
            }
        })
    }

    fun coercionSequence(baseTy: Ty): Sequence<Ty> {
        val result = mutableSetOf<Ty>()
        return generateSequence(baseTy) {
            if (result.add(it)) {
                deref(it) ?: (it as? TyArray)?.let { TySlice(it.base) }
            } else {
                null
            }
        }.constrainOnce().take(64) // rustc's default limit
    }

    fun deref(ty: Ty): Ty? = when (ty) {
        is TyReference -> ty.referenced
        is TyPointer -> ty.referenced
        else -> findDerefTarget(ty)
    }

    fun findImplOfTrait(ty: Ty, trait: RsTraitItem): BoundElement<RsTraitOrImpl>? =
        findImplsAndTraits(ty).find { it.element.implementedTrait?.element == trait }

    private fun findDerefTarget(ty: Ty): Ty? {
        val (derefTrait, derefTarget) = derefTraitAndTarget ?: return null
        val (_, subst) = findImplOfTrait(ty, derefTrait) ?: return null
        return subst[derefTarget]
    }

    fun findIteratorItemType(ty: Ty): Ty {
        val impl = findImplsAndTraits(ty)
            .find { impl ->
                val traitName = impl.element.implementedTrait?.element?.name
                traitName == "Iterator" || traitName == "IntoIterator"
            } ?: return TyUnknown

        val rawType = lookupAssociatedType(impl.element, "Item")
        return rawType.substitute(impl.subst)
    }

    fun findIndexOutputType(containerType: Ty, indexType: Ty): Ty? {
        val (element, subst) = findLangTraitImpl(containerType, indexType, "index") ?: return null
        val rawOutputType = lookupAssociatedType(element, "Output")
        return rawOutputType.substitute(subst)
    }

    fun findArithmeticBinaryExprOutputType(lhsType: Ty, rhsType: Ty, op: ArithmeticOp): Ty {
        val (element, subst) = findLangTraitImpl(lhsType, rhsType, op.itemName, op.fnName) ?: return TyUnknown
        return lookupAssociatedType(element, "Output")
            .substitute(subst)
            .substitute(mapOf(TyTypeParameter.self(element) to lhsType))
    }

    fun findLangTraitImpl(implType: Ty, argumentType: Ty,
                          itemName: String, fnName: String = itemName): BoundElement<RsTraitOrImpl>? {
        val impls = findImplsAndTraits(implType)
            .filter { itemName == it.element.implementedTrait?.element?.langAttribute }

        return if (impls.size < 2) {
            impls.firstOrNull()
        } else {
            impls.find { isImplSuitable(it.element, fnName, 0, argumentType) }
        }
    }

    private fun isImplSuitable(impl: RsTraitOrImpl,
                               fnName: String, paramIndex: Int, paramType: Ty): Boolean {
        return impl.members?.functionList
            ?.find { it.name == fnName }
            ?.valueParameterList
            ?.valueParameterList
            ?.getOrNull(paramIndex)
            ?.typeReference
            ?.type
            ?.unifyWith(paramType, this)
            ?.substitution() != null
    }

    fun asTyFunction(ty: Ty): TyFunction? {
        return ty as? TyFunction ?:
            (findImplsAndTraits(ty).mapNotNull { it.downcast<RsTraitItem>()?.asFunctionType }.firstOrNull())
    }

    fun asTyFunction(ref: BoundElement<RsTraitItem>): TyFunction? {
        return ref.asFunctionType
    }

    fun isCopy(ty: Ty): Boolean = findImplsAndTraits(ty).any { it.element == copyTrait }

    private val BoundElement<RsTraitItem>.asFunctionType: TyFunction?
        get() {
            val outputParam = fnOutputParam ?: return null
            val param = element.typeParamSingle ?: return null
            val argumentTypes = ((subst[param] ?: TyUnknown) as? TyTuple)?.types.orEmpty()
            val outputType = (subst[outputParam] ?: TyUnit)
            return TyFunction(argumentTypes, outputType)
        }

    companion object {
        fun relativeTo(psi: RsCompositeElement): ImplLookup =
            ImplLookup(psi.project, StdKnownItems.relativeTo(psi))

        private val findImplsAndTraitsCache =
            ProjectCache<Ty, Collection<BoundElement<RsTraitOrImpl>>>("findImplsAndTraitsCache")
    }
}

sealed class SelectionResult {
    object Ambiguous : SelectionResult()
    data class Ok(val impl: RsTraitOrImpl, val nestedObligations: List<Obligation>): SelectionResult()
    object Err: SelectionResult()
}

private fun RsTraitItem.substAssocType(assoc: String, ty: Ty?): BoundElement<RsTraitItem> {
    val assocType = findFreshAssociatedType(this, assoc)
    val subst = if (assocType != null && ty != null) mapOf(assocType to ty) else emptySubstitution
    return BoundElement(this, subst)
}

private fun Substitution.valueByName(name: String): Ty =
    entries.find { it.key.toString() == name }?.value ?: TyUnknown

private fun lookupAssociatedType(impl: RsTraitOrImpl, name: String): Ty {
    return impl.associatedTypesTransitively
        .find { it.name == name }
        ?.let { it.typeReference?.type ?: TyTypeParameter.associated(it) }
        ?: TyUnknown
}

private fun findFreshAssociatedType(baseTrait: RsTraitItem, name: String): TyTypeParameter? =
    baseTrait.associatedTypesTransitively.find { it.name == name }?.let { TyTypeParameter.associated(it) }
