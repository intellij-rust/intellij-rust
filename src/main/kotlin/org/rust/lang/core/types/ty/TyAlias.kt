/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.lang.core.psi.RsTypeAlias
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.TypeVisitor

/*tailrec fun Ty.followAliases(): Ty {
    return if (this !is TyAlias) this
    else aliases.followAliases()
}*/

/**
 * Represents a type that aliases another type via a type alias.
 */
@Suppress("DataClassPrivateConstructor")
data class TyAlias private constructor(
    val aliases: Ty,
    val typeAlias: BoundElement<RsTypeAlias>? = null
) : Ty(aliases.flags) {

    override fun superFoldWith(folder: TypeFolder): Ty =
        TyAlias(aliases.foldWith(folder), typeAlias?.foldWith(folder))

    override fun superVisitWith(visitor: TypeVisitor): Boolean =
        aliases.visitWith(visitor)

    fun withAlias(typeAlias: BoundElement<RsTypeAlias>?): TyAlias =
        copy(typeAlias = typeAlias)

    companion object {
        fun valueOf(aliases: Ty): TyAlias {
            return TyAlias(aliases)
        }
    }
}
