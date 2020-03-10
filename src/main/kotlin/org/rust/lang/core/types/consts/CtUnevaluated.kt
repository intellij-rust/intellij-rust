/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.consts

import org.rust.lang.core.types.HAS_CT_UNEVALUATED_MASK
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.TypeVisitor
import org.rust.lang.utils.evaluation.ConstExpr

data class CtUnevaluated(val expr: ConstExpr<*>) : Const(HAS_CT_UNEVALUATED_MASK or expr.flags) {
    override fun superFoldWith(folder: TypeFolder): Const =
        CtUnevaluated(expr.foldWith(folder))

    override fun superVisitWith(visitor: TypeVisitor): Boolean =
        expr.visitWith(visitor)

    override fun toString(): String = "<unknown>"
}
