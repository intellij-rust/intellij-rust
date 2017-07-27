/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import com.intellij.openapi.project.Project
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.indexes.RsImplIndex
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.infer.remapTypeParameters
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type
import org.rust.lang.utils.findWithCache

enum class StdDerivableTrait(val modName: String) {
    Clone("clone"),
    Copy("marker"),
    Debug("fmt"),
    Default("default"),
    Eq("cmp"),
    Hash("hash"),
    Ord("cmp"),
    PartialEq("cmp"),
    PartialOrd("cmp")
}

val STD_DERIVABLE_TRAITS: Map<String, StdDerivableTrait> = StdDerivableTrait.values().associate { it.name to it }

private val RsTraitItem.isDeref: Boolean get() = langAttribute == "deref"
private val RsTraitItem.isIndex: Boolean get() = langAttribute == "index"

class ImplLookup(private val project: Project, private val items: StdKnownItems) {
    fun findImplsAndTraits(ty: Ty): Collection<BoundElement<RsTraitOrImpl>> {
        return findWithCache(project, ty) { rawFindImplsAndTraits(ty) }
    }

    private fun rawFindImplsAndTraits(ty: Ty): Collection<BoundElement<RsTraitOrImpl>> {
        return when (ty) {
            is TyTypeParameter -> ty.getTraitBoundsTransitively()
            is TyTraitObject -> BoundElement(ty.trait).flattenHierarchy

        //  XXX: TyStr is TyPrimitive, but we want to handle it separately
            is TyStr -> RsImplIndex.findImpls(project, this, ty).map { impl -> BoundElement(impl) }
            is TyUnit, is TyUnknown -> emptyList()

            else -> {
                val derived = (ty as? TyStructOrEnumBase)?.item?.derivedTraits.orEmpty()
                    // select only std traits because we are sure
                    // that they are resolved correctly
                    .filter { item ->
                        val derivableTrait = STD_DERIVABLE_TRAITS[item.name] ?: return@filter false
                        item.containingCargoPackage?.origin == PackageOrigin.STDLIB &&
                            item.containingMod?.modName == derivableTrait.modName
                    }.map { BoundElement(it, mapOf(TyTypeParameter(it) to ty)) }

                derived + getHardcodedImpls(ty) + RsImplIndex.findImpls(project, this, ty).map { impl ->
                    BoundElement(impl, impl.remapTypeParameters(this, ty).orEmpty())
                }
            }
        }
    }

    private fun getHardcodedImpls(ty: Ty): Collection<BoundElement<RsTraitItem>> {
        // TODO this code should be completely removed after macros implementation
        when (ty) {
            is TyNumeric -> {
                return items.findBinOpTraits().map { it.substAssocType("Output", ty) }
            }
            is TyStruct -> {
                val mapping: TypeMapping = mutableMapOf()
                if (items.findCoreTy("slice::Iter").canUnifyWith(ty, this, mapping)) {
                    val trait = items.findIteratorTrait() ?: return emptyList()
                    return listOf(trait.substAssocType("Item", TyReference(mapping.valueByName("T"))))
                }
                if (items.findCoreTy("slice::IterMut").canUnifyWith(ty, this, mapping)) {
                    val trait = items.findIteratorTrait() ?: return emptyList()
                    return listOf(trait.substAssocType("Item", TyReference(mapping.valueByName("T"), true)))
                }
            }
        }
        return emptyList()
    }

    fun findMethodsAndAssocFunctions(ty: Ty): List<BoundElement<RsFunction>> {
        return findImplsAndTraits(ty).flatMap { it.functionsWithInherited }
    }

    fun derefTransitively(baseTy: Ty): Set<Ty> {
        val result = mutableSetOf<Ty>()

        var ty = baseTy
        while (true) {
            if (ty in result) break
            result += ty
            ty = if (ty is TyReference) {
                ty.referenced
            } else {
                findDerefTarget(ty)
                    ?: break
            }
        }

        return result
    }

    private fun findDerefTarget(ty: Ty): Ty? {
        for ((impl, subst) in findImplsAndTraits(ty)) {
            val trait = impl.implementedTrait ?: continue
            if (!trait.element.isDeref) continue
            return lookupAssociatedType(impl, "Target")
                .substitute(subst)
        }
        return null
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

    fun findIndexOutputType(containerType: Ty, indexType: Ty): Ty {
        val impls = findImplsAndTraits(containerType)
            .filter { it.element.implementedTrait?.element?.isIndex ?: false }

        val (element, subst) = if (impls.size < 2) {
            impls.firstOrNull()
        } else {
            impls.find { isImplSuitable(it.element, "index", 0, indexType) }
        } ?: return TyUnknown

        val rawOutputType = lookupAssociatedType(element, "Output")
        return rawOutputType.substitute(subst)
    }

    fun findArithmeticBinaryExprOutputType(lhsType: Ty, rhsType: Ty, op: ArithmeticOp): Ty {
        val impls = findImplsAndTraits(lhsType)
            .filter { op.itemName == it.element.implementedTrait?.element?.langAttribute }

        val (element, subst) = if (impls.size < 2) {
            impls.firstOrNull()
        } else {
            impls.find { isImplSuitable(it.element, op.itemName, 0, rhsType) }
        } ?: return TyUnknown

        val assocType = if (element is RsTraitItem) {
            subst[TyTypeParameter(element, "Output")] ?: return TyUnknown
        } else {
            lookupAssociatedType(element, "Output").substitute(subst)
        }

        return assocType.substitute(mapOf(TyTypeParameter(element) to lhsType))
    }

    private fun isImplSuitable(impl: RsTraitOrImpl,
                               fnName: String, paramIndex: Int, paramType: Ty): Boolean {
        return impl.functionList
            .find { it.name == fnName }
            ?.valueParameterList
            ?.valueParameterList
            ?.getOrNull(paramIndex)
            ?.typeReference
            ?.type
            ?.canUnifyWith(paramType, this) ?: false
    }

    companion object {
        fun relativeTo(psi: RsCompositeElement): ImplLookup =
            ImplLookup(psi.project, StdKnownItems.relativeTo(psi))
    }
}

private fun RsTraitItem.substAssocType(assoc: String, ty: Ty?): BoundElement<RsTraitItem> {
    val assocType = TyTypeParameter(this, assoc)
    val subst = if (ty != null) mapOf(assocType to ty) else emptySubstitution
    return BoundElement(this, subst)
}

private fun Substitution.valueByName(name: String): Ty =
    entries.find { it.key.toString() == name }?.value ?: TyUnknown

private fun lookupAssociatedType(impl: RsTraitOrImpl, name: String): Ty =
    impl.typeAliasList.find { it.name == name }?.typeReference?.type
        ?: TyUnknown
