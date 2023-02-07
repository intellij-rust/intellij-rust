/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.building

import org.rust.lang.core.mir.schemas.MirSourceInfo
import org.rust.lang.core.psi.RsLitExpr
import org.rust.lang.core.psi.ext.ArithmeticOp
import org.rust.lang.core.psi.ext.LogicOp
import org.rust.lang.core.psi.ext.UnaryOperator
import org.rust.lang.core.types.ty.Ty

sealed class ThirExpr(val ty: Ty, val source: MirSourceInfo) {
    class Scope(
        val expr: ThirExpr,
        ty: Ty,
        source: MirSourceInfo,
    ) : ThirExpr(ty, source)

    class Literal(
        val literal: RsLitExpr,
        val neg: Boolean,
        ty: Ty,
        source: MirSourceInfo,
    ) : ThirExpr(ty, source)

    class Unary(
        val op: UnaryOperator,
        val arg: ThirExpr,
        ty: Ty,
        source: MirSourceInfo,
    ) : ThirExpr(ty, source)

    class Binary(
        val op: ArithmeticOp,
        val left: ThirExpr,
        val right: ThirExpr,
        ty: Ty,
        source: MirSourceInfo,
    ) : ThirExpr(ty, source)

    class Logical(
        val op: LogicOp,
        val left: ThirExpr,
        val right: ThirExpr,
        ty: Ty,
        source: MirSourceInfo,
    ) : ThirExpr(ty, source)

    class Block(
        val block: ThirBlock,
        ty: Ty,
        source: MirSourceInfo,
    ) : ThirExpr(ty, source)

    class If(
        val cond: ThirExpr,
        val then: ThirExpr,
        val `else`: ThirExpr?,
        ty: Ty,
        source: MirSourceInfo,
    ) : ThirExpr(ty, source)

    class Tuple(
        val fields: List<ThirExpr>,
        ty: Ty,
        source: MirSourceInfo,
    ) : ThirExpr(ty, source)
}
