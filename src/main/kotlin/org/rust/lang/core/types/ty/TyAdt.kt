/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import com.intellij.codeInsight.completion.CompletionUtil
import org.rust.lang.core.psi.ext.RsStructOrEnumItemElement
import org.rust.lang.core.psi.ext.typeParameters
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.TypeVisitor
import org.rust.lang.core.types.type

/**
 * Represents struct/enum/union.
 * "ADT" may be read as "Algebraic Data Type".
 * The name is inspired by rustc
 */
@Suppress("DataClassPrivateConstructor")
data class TyAdt private constructor(
    val item: RsStructOrEnumItemElement,
    val typeArguments: List<Ty>
) : Ty(mergeFlags(typeArguments)) {

    // This method is rarely called (in comparison with folding),
    // so we can implement it in a such inefficient way
    override val typeParameterValues: Substitution
        get() = item.typeParameters.withIndex().associate { (i, param) ->
            TyTypeParameter.named(param) to typeArguments.getOrElse(i) { TyUnknown }
        }

    override fun superFoldWith(folder: TypeFolder): TyAdt =
        TyAdt(item, typeArguments.map { it.foldWith(folder) })

    override fun superVisitWith(visitor: TypeVisitor): Boolean =
        typeArguments.any(visitor)

    companion object {
        fun valueOf(struct: RsStructOrEnumItemElement): TyAdt {
            val item = CompletionUtil.getOriginalOrSelf(struct)
            return TyAdt(item, defaultTypeArguments(struct))
        }
    }
}

private fun defaultTypeArguments(item: RsStructOrEnumItemElement): List<Ty> =
    item.typeParameters.map { param ->
        param.typeReference?.type ?: TyTypeParameter.named(param)
    }
