/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.consts

import org.rust.lang.core.types.Kind
import org.rust.lang.core.types.TypeFlags
import org.rust.lang.core.types.infer.TypeFoldable
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.TypeVisitor

abstract class Const(override val flags: TypeFlags = 0) : Kind, TypeFoldable<Const> {

    override fun foldWith(folder: TypeFolder): Const = folder.foldConst(this)

    override fun superFoldWith(folder: TypeFolder): Const = this

    override fun visitWith(visitor: TypeVisitor): Boolean = visitor.visitConst(this)

    override fun superVisitWith(visitor: TypeVisitor): Boolean = false
}
