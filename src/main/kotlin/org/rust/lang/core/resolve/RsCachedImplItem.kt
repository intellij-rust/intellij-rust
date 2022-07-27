/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import com.intellij.util.SmartList
import com.intellij.util.recursionSafeLazy
import gnu.trove.THashMap
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.consts.CtConstParameter
import org.rust.lang.core.types.infer.constGenerics
import org.rust.lang.core.types.infer.generics
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyTypeParameter
import org.rust.lang.core.types.type
import kotlin.LazyThreadSafetyMode.PUBLICATION

/**
 * Used for optimization purposes, to reduce access to cache and PSI tree in some very hot places,
 * [ImplLookup.assembleCandidates] and [processAssociatedItems] in particular
 */
class RsCachedImplItem(
    val impl: RsImplItem
) {
    val traitRef: RsTraitRef?
    val typeParameters: Array<ScopeEntry>
    val constParameters: Array<ScopeEntry>
    val containingCrate: Crate?
    val isValid: Boolean
    val isNegativeImpl: Boolean

    init {
        var typeParameterList: RsTypeParameterList? = null

        val stub = impl.greenStub
        if (stub != null) {
            // This branch exists only for performance purposes
            var traitRef: RsTraitRef? = null
            for (child in stub.childrenStubs) {
                when (child.stubType) {
                    RsElementTypes.TRAIT_REF -> traitRef = child.psi as RsTraitRef
                    RsElementTypes.TYPE_PARAMETER_LIST -> typeParameterList = child.psi as RsTypeParameterList
                }
            }
            this.traitRef = traitRef
            this.isNegativeImpl = stub.isNegativeImpl
        } else {
            typeParameterList = impl.typeParameterList
            this.traitRef = impl.traitRef
            this.isNegativeImpl = impl.isNegativeImpl
        }
        this.typeParameters = typeParameterList?.typeParameterList.orEmpty().mapToScopeEntries()
        this.constParameters = typeParameterList?.constParameterList.orEmpty().mapToScopeEntries()

        val (isValid, crate) = impl.isValidProjectMemberAndContainingCrate
        this.containingCrate = crate
        this.isValid = isValid && !impl.isReservationImpl
    }

    val isInherent: Boolean get() = traitRef == null

    val implementedTrait: BoundElement<RsTraitItem>? by recursionSafeLazy { traitRef?.resolveToBoundTrait() }
    val typeAndGenerics: Triple<Ty, List<TyTypeParameter>, List<CtConstParameter>>? by lazy(PUBLICATION) {
        impl.typeReference?.type?.let { Triple(it, impl.generics, impl.constGenerics) }
    }

    /** For `impl T for Foo` returns union of impl members and trait `T` members that are not overriden by the impl */
    val implAndTraitExpandedMembers: Map<String, List<RsAbstractable>> by lazy(PUBLICATION) {
        val membersMap = THashMap<String, MutableList<RsAbstractable>>()
        for (member in impl.members?.expandedMembers.orEmpty()) {
            val name = member.name ?: continue
            membersMap.getOrPut(name) { SmartList() }.add(member)
        }
        val traitMembers = implementedTrait?.element?.members?.expandedMembers
            ?: return@lazy membersMap
        val implMemberNames = HashSet<String>(membersMap.keys)
        for (member in traitMembers) {
            val name = member.name ?: continue
            if (name in implMemberNames) continue
            membersMap.getOrPut(name) { SmartList() }.add(member)
        }
        membersMap
    }

    // Reduces heap memory usage by reducing number on `TraitImplSource.ExplicitImpl` instances
    val explicitImpl: TraitImplSource.ExplicitImpl = TraitImplSource.ExplicitImpl(this)

    companion object {
        private val EMPTY_SCOPE_ENTRY_ARRAY: Array<ScopeEntry> = emptyArray()

        fun forImpl(impl: RsImplItem): RsCachedImplItem {
            return (impl as RsImplItemImplMixin).cachedImplItem.value
        }

        private fun List<RsNamedElement>.mapToScopeEntries(): Array<ScopeEntry> {
            val scopeEntries = mapNotNull {
                val name = it.name ?: return@mapNotNull null
                SimpleScopeEntry(name, it)
            }
            return if (scopeEntries.isEmpty()) {
                EMPTY_SCOPE_ENTRY_ARRAY
            } else {
                scopeEntries.toTypedArray()
            }
        }
    }
}
