/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsTypeAlias
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.knownItems
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.infer.*
import org.rust.lang.core.types.mergeFlags
import org.rust.lang.core.types.rawType

data class FnSig(
    val paramTypes: List<Ty>,
    val retType: Ty,
    val unsafety: Unsafety = Unsafety.Normal,
    val cVariadic: Boolean = false
) : TypeFoldable<FnSig> {
    override fun superFoldWith(folder: TypeFolder): FnSig {
        return FnSig(
            paramTypes.map { it.foldWith(folder) },
            retType.foldWith(folder),
            unsafety,
            cVariadic
        )
    }

    override fun superVisitWith(visitor: TypeVisitor): Boolean {
        return paramTypes.any { it.visitWith(visitor) } || retType.visitWith(visitor)
    }

    fun isEquivalentToInner(other: FnSig): Boolean {
        if (this === other) return true

        if (paramTypes.size != other.paramTypes.size) return false
        for (i in paramTypes.indices) {
            if (!paramTypes[i].isEquivalentTo(other.paramTypes[i])) return false
        }
        if (!retType.isEquivalentTo(other.retType)) return false

        if (unsafety != other.unsafety) return false
        return cVariadic == other.cVariadic
    }

    companion object {
        fun of(function: RsFunction): FnSig {
            val paramTypes = mutableListOf<Ty>()

            val self = function.selfParameter
            if (self != null) {
                paramTypes += self.typeOfValue
            }

            paramTypes += function.valueParameters.map { it.typeReference?.rawType ?: TyUnknown }

            val rawReturnType = function.rawReturnType
            return FnSig(
                paramTypes,
                if (function.isAsync) function.knownItems.makeFuture(rawReturnType) else rawReturnType,
                Unsafety.fromBoolean(function.isActuallyUnsafe),
                function.isVariadic
            )
        }
    }
}
