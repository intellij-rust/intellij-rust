/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import com.intellij.codeInsight.completion.CompletionUtil
import org.rust.lang.core.psi.RsTypeAlias
import org.rust.lang.core.psi.ext.RsStructOrEnumItemElement
import org.rust.lang.core.psi.ext.constParameters
import org.rust.lang.core.psi.ext.lifetimeParameters
import org.rust.lang.core.psi.ext.typeParameters
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.Substitution
import org.rust.lang.core.types.consts.Const
import org.rust.lang.core.types.consts.CtConstParameter
import org.rust.lang.core.types.consts.CtUnknown
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.TypeVisitor
import org.rust.lang.core.types.mergeFlags
import org.rust.lang.core.types.regions.ReEarlyBound
import org.rust.lang.core.types.regions.ReUnknown
import org.rust.lang.core.types.regions.Region

/**
 * Represents struct/enum/union.
 * "ADT" may be read as "Algebraic Data Type".
 * The name is inspired by rustc.
 */
@Suppress("DataClassPrivateConstructor")
data class TyAdt private constructor(
    val item: RsStructOrEnumItemElement,
    val typeArguments: List<Ty>,
    val regionArguments: List<Region>,
    val constArguments: List<Const>,
    val aliasedBy: BoundElement<RsTypeAlias>?
) : Ty(mergeFlags(typeArguments) or mergeFlags(regionArguments) or mergeFlags(constArguments)) {

    // This method is rarely called (in comparison with folding), so we can implement it in a such inefficient way.
    override val typeParameterValues: Substitution
        get() {
            val typeSubst = item.typeParameters.withIndex().associate { (i, param) ->
                TyTypeParameter.named(param) to typeArguments.getOrElse(i) { TyUnknown }
            }
            val regionSubst = item.lifetimeParameters.withIndex().associate { (i, param) ->
                ReEarlyBound(param) to regionArguments.getOrElse(i) { ReUnknown }
            }
            val constSubst = item.constParameters.withIndex().associate { (i, param) ->
                CtConstParameter(param) to constArguments.getOrElse(i) { CtUnknown }
            }
            return Substitution(typeSubst, regionSubst, constSubst)
        }

    override fun superFoldWith(folder: TypeFolder): TyAdt =
        TyAdt(
            item,
            typeArguments.map { it.foldWith(folder) },
            regionArguments.map { it.foldWith(folder) },
            constArguments.map { it.foldWith(folder) },
            aliasedBy
        )

    override fun superVisitWith(visitor: TypeVisitor): Boolean =
        typeArguments.any { it.visitWith(visitor) } ||
            regionArguments.any { it.visitWith(visitor) } ||
            constArguments.any { it.visitWith(visitor) }

    fun withAlias(aliasedBy: BoundElement<RsTypeAlias>?): TyAdt =
        copy(aliasedBy = aliasedBy)

    companion object {
        fun valueOf(struct: RsStructOrEnumItemElement): TyAdt =
            TyAdt(
                CompletionUtil.getOriginalOrSelf(struct),
                defaultTypeArguments(struct),
                defaultRegionArguments(struct),
                defaultConstArguments(struct),
                null
            )
    }
}

private fun defaultTypeArguments(item: RsStructOrEnumItemElement): List<Ty> =
    item.typeParameters.map { param -> TyTypeParameter.named(param) }

private fun defaultRegionArguments(item: RsStructOrEnumItemElement): List<Region> =
    item.lifetimeParameters.map { param -> ReEarlyBound(param) }

private fun defaultConstArguments(item: RsStructOrEnumItemElement): List<Const> =
    item.constParameters.map { param -> CtConstParameter(param) }
