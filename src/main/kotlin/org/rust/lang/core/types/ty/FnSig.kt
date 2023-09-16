/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.lang.core.types.RsCallable
import org.rust.lang.core.types.infer.*

data class FnSig(
    val paramTypes: List<Ty>,
    val retType: Ty,
    val unsafety: Unsafety = Unsafety.Normal
) : TypeFoldable<FnSig> {
    override fun superFoldWith(folder: TypeFolder): FnSig {
        return FnSig(
            paramTypes.map { it.foldWith(folder) },
            retType.foldWith(folder),
            unsafety
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

        return unsafety == other.unsafety
    }

    companion object {
        fun of(callable: RsCallable): FnSig {
            val paramTypes = mutableListOf<Ty>()

            val self = callable.selfParameter
            if (self != null) {
                paramTypes += self.typeOfValue
            }

            paramTypes += callable.parameterTypes

            val rawReturnType = callable.rawReturnType
            return FnSig(
                paramTypes,
                if (callable.isAsync) callable.knownItems.makeFuture(rawReturnType) else rawReturnType,
                Unsafety.fromBoolean(callable.isActuallyUnsafe)
            )
        }
    }
}
