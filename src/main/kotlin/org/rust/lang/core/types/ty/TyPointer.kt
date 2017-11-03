/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.TypeVisitor

data class TyPointer(val referenced: Ty, val mutability: Mutability) : Ty(referenced.flags) {

    override fun superFoldWith(folder: TypeFolder): Ty =
        TyPointer(referenced.foldWith(folder), mutability)

    override fun superVisitWith(visitor: TypeVisitor): Boolean =
        referenced.visitWith(visitor)

    override fun toString() = "*${if (mutability.isMut) "mut" else "const"} $referenced"
}
