/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import com.intellij.util.SmartList
import gnu.trove.THashMap
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
    private val traitRef: RsTraitRef? = impl.traitRef
    val isValid: Boolean = impl.isValidProjectMember && !impl.isReservationImpl && !impl.isNegativeImpl
    val isInherent: Boolean get() = traitRef == null

    val implementedTrait: BoundElement<RsTraitItem>? by lazy(PUBLICATION) { traitRef?.resolveToBoundTrait() }
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

    companion object {
        fun forImpl(impl: RsImplItem): RsCachedImplItem {
            return (impl as RsImplItemImplMixin).cachedImplItem.value
        }
    }
}
