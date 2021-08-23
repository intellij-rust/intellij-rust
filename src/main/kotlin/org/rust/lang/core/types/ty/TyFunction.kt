/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.lang.core.psi.RsTypeAlias
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.TypeVisitor
import org.rust.lang.core.types.mergeFlags

data class TyFunction(
    val paramTypes: List<Ty>,
    val retType: Ty,
    override val aliasedBy: BoundElement<RsTypeAlias>? = null
) : Ty(mergeFlags(paramTypes) or retType.flags) {

    override fun superFoldWith(folder: TypeFolder): Ty =
        TyFunction(paramTypes.map { it.foldWith(folder) }, retType.foldWith(folder), aliasedBy?.foldWith(folder))

    override fun superVisitWith(visitor: TypeVisitor): Boolean =
        paramTypes.any { it.visitWith(visitor) } || retType.visitWith(visitor)

    override fun withAlias(aliasedBy: BoundElement<RsTypeAlias>): Ty = copy(aliasedBy = aliasedBy)

    override fun isEquivalentToInner(other: Ty): Boolean {
        if (this === other) return true
        if (other !is TyFunction) return false

        if (paramTypes.size != other.paramTypes.size) return false
        for (i in paramTypes.indices) {
            if (!paramTypes[i].isEquivalentTo(other.paramTypes[i])) return false
        }
        if (!retType.isEquivalentTo(other.retType)) return false

        return true
    }
}
