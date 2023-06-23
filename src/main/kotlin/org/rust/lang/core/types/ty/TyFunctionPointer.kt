/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.lang.core.psi.RsTypeAlias
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.TypeVisitor

class TyFunctionPointer(
    fnSig: FnSig,
    override val aliasedBy: BoundElement<RsTypeAlias>? = null
) : TyFunctionBase(fnSig) {
    override fun superFoldWith(folder: TypeFolder): Ty =
        TyFunctionPointer(fnSig.superFoldWith(folder), aliasedBy)

    override fun superVisitWith(visitor: TypeVisitor): Boolean =
        fnSig.superVisitWith(visitor)

    override fun withAlias(aliasedBy: BoundElement<RsTypeAlias>): TyFunctionPointer = TyFunctionPointer(fnSig, aliasedBy)

    override fun isEquivalentToInner(other: Ty): Boolean {
        if (this === other) return true
        if (other !is TyFunctionPointer) return false

        return fnSig.isEquivalentToInner(other.fnSig)
    }
}
