/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.rust.lang.core.crate.Crate
import org.rust.lang.core.macros.RsExpandedElement
import org.rust.lang.core.psi.RsTypeAlias
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.psi.isValidProjectMember
import org.rust.lang.core.types.consts.CtConstParameter
import org.rust.lang.core.types.infer.constGenerics
import org.rust.lang.core.types.infer.generics
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyTypeParameter
import kotlin.LazyThreadSafetyMode.PUBLICATION

/**
 * Used for optimization purposes, to reduce access to cache and PSI tree in some very hot places,
 * [ImplLookup.processTyFingerprintsWithAliases] in particular
 */
class RsCachedTypeAlias(
    val alias: RsTypeAlias
) {
    val name: String? = alias.name
    val context: RsElement? = RsExpandedElement.getContextImpl(alias) as? RsElement

    val isFreeAndValid: Boolean by lazy(PUBLICATION) {
        name != null
            && alias.getOwner { this@RsCachedTypeAlias.context } is RsAbstractableOwner.Free
            && alias.isValidProjectMember
    }

    val containingCrate: Crate? by lazy(PUBLICATION) {
        context?.containingCrate
    }

    val typeAndGenerics: Triple<Ty, List<TyTypeParameter>, List<CtConstParameter>> by lazy(PUBLICATION) {
        Triple(alias.declaredType, alias.generics, alias.constGenerics)
    }

    companion object {
        fun forAlias(alias: RsTypeAlias): RsCachedTypeAlias {
            return (alias as RsTypeAliasImplMixin).cachedImplItem.value
        }
    }
}
