/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.lang.core.psi.RsTypeAlias
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.TypeVisitor

data class TyPointer(
    val referenced: Ty,
    val mutability: Mutability,
    override val aliasedBy: BoundElement<RsTypeAlias>? = null
) : Ty(referenced.flags) {

    override fun superFoldWith(folder: TypeFolder): Ty =
        TyPointer(referenced.foldWith(folder), mutability, aliasedBy?.foldWith(folder))

    override fun superVisitWith(visitor: TypeVisitor): Boolean =
        referenced.visitWith(visitor)

    override fun withAlias(aliasedBy: BoundElement<RsTypeAlias>): Ty = copy(aliasedBy = aliasedBy)

    override fun isEquivalentToInner(other: Ty): Boolean {
        if (this === other) return true
        if (other !is TyPointer) return false

        if (!referenced.isEquivalentTo(other.referenced)) return false
        if (mutability != other.mutability) return false

        return true
    }
}
