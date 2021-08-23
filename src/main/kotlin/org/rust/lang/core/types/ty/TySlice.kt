/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.lang.core.psi.RsTypeAlias
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.TypeVisitor

data class TySlice(
    val elementType: Ty,
    override val aliasedBy: BoundElement<RsTypeAlias>? = null
) : Ty(elementType.flags) {

    override fun superFoldWith(folder: TypeFolder): Ty =
        TySlice(elementType.foldWith(folder), aliasedBy?.foldWith(folder))

    override fun superVisitWith(visitor: TypeVisitor): Boolean =
        elementType.visitWith(visitor)

    override fun withAlias(aliasedBy: BoundElement<RsTypeAlias>): Ty = copy(aliasedBy = aliasedBy)

    override fun isEquivalentToInner(other: Ty): Boolean {
        if (this === other) return true
        if (other !is TySlice) return false

        if (!elementType.isEquivalentTo(other.elementType)) return false

        return true
    }
}
