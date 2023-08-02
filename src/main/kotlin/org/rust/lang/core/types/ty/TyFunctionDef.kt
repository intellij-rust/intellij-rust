/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.lang.core.psi.RsTypeAlias
import org.rust.lang.core.types.RsCallable
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.TypeVisitor

class TyFunctionDef(
    val def: RsCallable,
    fnSig: FnSig = FnSig.of(def),
    override val aliasedBy: BoundElement<RsTypeAlias>? = null
) : TyFunctionBase(fnSig) {
    override fun superFoldWith(folder: TypeFolder): Ty =
        TyFunctionDef(def, fnSig.superFoldWith(folder), aliasedBy)

    override fun superVisitWith(visitor: TypeVisitor): Boolean =
        fnSig.superVisitWith(visitor)

    override fun withAlias(aliasedBy: BoundElement<RsTypeAlias>): TyFunctionDef = TyFunctionDef(def, fnSig, aliasedBy)

    override fun isEquivalentToInner(other: Ty): Boolean {
        if (this === other) return true
        if (other !is TyFunctionDef) return false

        if (def != other.def) return false
        return fnSig.isEquivalentToInner(other.fnSig)
    }
}
