/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.ide.presentation.tyToString
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.TypeVisitor

data class TyFunction(val paramTypes: List<Ty>, val retType: Ty) : Ty(mergeFlags(paramTypes) or retType.flags) {

    override fun superFoldWith(folder: TypeFolder): Ty =
        TyFunction(paramTypes.map { it.foldWith(folder) }, retType.foldWith(folder))

    override fun superVisitWith(visitor: TypeVisitor): Boolean =
        paramTypes.any(visitor) || retType.visitWith(visitor)

    override fun toString(): String = tyToString(this)
}
