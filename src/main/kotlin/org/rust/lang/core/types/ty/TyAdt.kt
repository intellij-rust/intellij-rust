/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import com.intellij.codeInsight.completion.CompletionUtil
import org.rust.lang.core.psi.RsEnumItem
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.ext.RsStructOrEnumItemElement
import org.rust.lang.core.psi.ext.typeParameters
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.TypeVisitor
import org.rust.lang.core.types.type

/**
 * Represents struct/enum/union.
 * "ADT" may be read as "Algebraic Data Type".
 * The name is inspired by rustc
 */
data class TyAdt(
    val item: RsStructOrEnumItemElement,
    override val typeParameterValues: Substitution
) : Ty(mergeFlags(typeParameterValues)) {

    val typeArguments: List<Ty>
        get() = item.typeParameters.map { typeParameterValues.get(it) ?: TyUnknown }

    override fun superFoldWith(folder: TypeFolder): TyAdt =
        TyAdt(item, typeParameterValues.foldValues(folder))

    override fun superVisitWith(visitor: TypeVisitor): Boolean =
        typeParameterValues.values.any(visitor)

    companion object {
        fun valueOf(struct: RsStructOrEnumItemElement): TyAdt {
            val item = CompletionUtil.getOriginalOrSelf(struct)
            return TyAdt(item, defaultSubstitution(struct))
        }
    }
}

private fun defaultSubstitution(item: RsStructOrEnumItemElement): Substitution =
    item.typeParameters.associate { rsTypeParameter ->
        val tyTypeParameter = TyTypeParameter.named(rsTypeParameter)
        val defaultType = rsTypeParameter.typeReference?.type ?: tyTypeParameter
        tyTypeParameter to defaultType
    }
