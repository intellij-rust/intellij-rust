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
import org.rust.openapiext.testAssert

data class TyTuple(
    val types: List<Ty>,
    override val aliasedBy: BoundElement<RsTypeAlias>? = null
) : Ty(mergeFlags(types)) {

    init {
        testAssert { types.isNotEmpty() }
    }

    override fun superFoldWith(folder: TypeFolder): Ty =
        TyTuple(types.map { it.foldWith(folder) }, aliasedBy?.foldWith(folder))

    override fun superVisitWith(visitor: TypeVisitor): Boolean =
        types.any { it.visitWith(visitor) }

    override fun withAlias(aliasedBy: BoundElement<RsTypeAlias>): TyTuple = copy(aliasedBy = aliasedBy)

    override fun isEquivalentToInner(other: Ty): Boolean {
        if (this === other) return true
        if (other !is TyTuple) return false

        if (types.size != other.types.size) return false
        for (i in types.indices) {
            if (!types[i].isEquivalentTo(other.types[i])) return false
        }

        return true
    }
}
