/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.ide.presentation.tyToString
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.TypeVisitor

data class TyFunction(val paramTypes: List<Ty>, val retType: Ty) : Ty {

    override fun unifyWith(other: Ty, lookup: ImplLookup): UnifyResult {
        return if(other is TyFunction && paramTypes.size == other.paramTypes.size) {
            UnifyResult.mergeAll(
                paramTypes.zip(other.paramTypes).map { (type1, type2) -> type1.unifyWith(type2, lookup) }
            ).merge(retType.unifyWith(other.retType, lookup))
        } else {
            UnifyResult.fail
        }
    }

    override fun superFoldWith(folder: TypeFolder): Ty =
        TyFunction(paramTypes.map { it.foldWith(folder) }, retType.foldWith(folder))

    override fun superVisitWith(visitor: TypeVisitor): Boolean =
        paramTypes.any(visitor) || retType.visitWith(visitor)

    override fun toString(): String = tyToString(this)
}
