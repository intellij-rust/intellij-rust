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

data class TyTuple(
    val types: List<Ty>,
    override val aliasedBy: BoundElement<RsTypeAlias>? = null
) : Ty(mergeFlags(types)) {

    override fun superFoldWith(folder: TypeFolder): Ty =
        TyTuple(types.map { it.foldWith(folder) }, aliasedBy?.foldWith(folder))

    override fun superVisitWith(visitor: TypeVisitor): Boolean =
        types.any { it.visitWith(visitor) }

    override fun withAlias(aliasedBy: BoundElement<RsTypeAlias>): TyTuple = copy(aliasedBy = aliasedBy)
}
