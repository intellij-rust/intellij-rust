/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.RsTraitRef
import org.rust.lang.core.psi.ext.RsAbstractable
import org.rust.lang.core.psi.ext.expandedMembers
import org.rust.lang.core.psi.ext.isReservationImpl
import org.rust.lang.core.psi.ext.resolveToBoundTrait
import org.rust.lang.core.psi.isValidProjectMember
import org.rust.lang.core.resolve.ref.ResolveCacheDependency
import org.rust.lang.core.resolve.ref.RsResolveCache
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.consts.CtConstParameter
import org.rust.lang.core.types.infer.constGenerics
import org.rust.lang.core.types.infer.generics
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyTypeParameter
import org.rust.lang.core.types.type
import org.rust.stdext.mapToSet
import kotlin.LazyThreadSafetyMode.PUBLICATION

/**
 * Used for optimization purposes, to reduce access to cache and PSI tree in some very hot places,
 * [ImplLookup.assembleCandidates] and [processAssociatedItems] in particular
 */
class RsCachedImplItem(
    val impl: RsImplItem
) {
    private val traitRef: RsTraitRef? = impl.traitRef
    val isValid: Boolean = impl.isValidProjectMember && !impl.isReservationImpl
    val isInherent: Boolean get() = traitRef == null

    val implementedTrait: BoundElement<RsTraitItem>? by lazy(PUBLICATION) { traitRef?.resolveToBoundTrait() }
    val typeAndGenerics: Triple<Ty, List<TyTypeParameter>, List<CtConstParameter>>? by lazy(PUBLICATION) {
        impl.typeReference?.type?.let { Triple(it, impl.generics, impl.constGenerics) }
    }

    /** For `impl T for Foo` returns union of impl members and trait `T` members that are not overriden by the impl */
    val implAndTraitExpandedMembers: List<RsAbstractable> by lazy(PUBLICATION) {
        val implMembers = impl.members?.expandedMembers.orEmpty()
        val traitMembers = implementedTrait?.element?.members?.expandedMembers
            ?: return@lazy implMembers
        val implMemberNames = implMembers.mapToSet { it.name }
        implMembers + traitMembers.filter { it.name !in implMemberNames }
    }

    companion object {
        fun forImpl(project: Project, impl: RsImplItem): RsCachedImplItem {
            return RsResolveCache.getInstance(project)
                .resolveWithCaching(impl, ResolveCacheDependency.RUST_STRUCTURE, Resolver)!!
        }

        private object Resolver : (RsImplItem) -> RsCachedImplItem {
            override fun invoke(impl: RsImplItem): RsCachedImplItem {
                return RsCachedImplItem(impl)
            }
        }
    }
}
