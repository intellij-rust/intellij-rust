/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.ide.presentation.tyToString
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.TypeVisitor

data class TyReference(val referenced: Ty, val mutability: Mutability) : Ty(referenced.flags) {

    override fun superFoldWith(folder: TypeFolder): Ty =
        TyReference(referenced.foldWith(folder), mutability)

    override fun superVisitWith(visitor: TypeVisitor): Boolean =
        referenced.visitWith(visitor)

    override fun toString(): String = tyToString(this)
}
