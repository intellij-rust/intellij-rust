/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.lang.core.psi.RsLambdaExpr
import org.rust.lang.core.psi.RsTypeAlias
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.TypeVisitor

class TyClosure(
    val def: RsLambdaExpr,
    fnSig: FnSig,
    override val aliasedBy: BoundElement<RsTypeAlias>? = null
) : TyFunctionBase(fnSig) {
    override fun superFoldWith(folder: TypeFolder): TyClosure =
        TyClosure(def, fnSig.superFoldWith(folder), aliasedBy)

    override fun superVisitWith(visitor: TypeVisitor): Boolean =
        fnSig.superVisitWith(visitor)

    override fun withAlias(aliasedBy: BoundElement<RsTypeAlias>): Ty = TyClosure(def, fnSig, aliasedBy)

    override fun isEquivalentToInner(other: Ty): Boolean {
        if (this === other) return true
        if (other !is TyClosure) return false
        if (def != other.def) return false
        return fnSig.isEquivalentToInner(other.fnSig)
    }
}
