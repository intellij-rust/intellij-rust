/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.TypeVisitor
import org.rust.lang.core.types.mergeFlags

data class TyFunction(val paramTypes: List<Ty>, val retType: Ty) : Ty(mergeFlags(paramTypes) or retType.flags) {

    override fun superFoldWith(folder: TypeFolder): Ty =
        TyFunction(paramTypes.map { it.foldWith(folder) }, retType.foldWith(folder))

    override fun superVisitWith(visitor: TypeVisitor): Boolean =
        paramTypes.any { it.visitWith(visitor) } || retType.visitWith(visitor)
}
