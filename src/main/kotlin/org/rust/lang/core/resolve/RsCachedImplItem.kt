/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsMembers
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.RsTraitRef
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.resolveToBoundTrait
import org.rust.lang.core.resolve.ref.ResolveCacheDependency
import org.rust.lang.core.resolve.ref.RsResolveCache
import org.rust.lang.core.types.BoundElement
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
    val impl: RsImplItem,
    val crateRoot: RsMod? = impl.crateRoot,
    val traitRef: RsTraitRef? = impl.traitRef,
    val membres: RsMembers? = impl.members
) {
    val implementedTrait: BoundElement<RsTraitItem>? by lazy(PUBLICATION) { traitRef?.resolveToBoundTrait() }
    val typeAndGenerics: Pair<Ty, List<TyTypeParameter>>? by lazy(PUBLICATION) {
        impl.typeReference?.type?.let { it to impl.generics }
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
