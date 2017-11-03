/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.ide.presentation.tyToString
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.TypeVisitor

data class TyTuple(val types: List<Ty>) : Ty(mergeFlags(types)) {

    override fun superFoldWith(folder: TypeFolder): Ty =
        TyTuple(types.map { it.foldWith(folder) })

    override fun superVisitWith(visitor: TypeVisitor): Boolean =
        types.any(visitor)

    override fun toString(): String = tyToString(this)
}

