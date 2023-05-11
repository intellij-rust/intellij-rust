/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.thir

import org.rust.lang.core.mir.schemas.MirBorrowKind
import org.rust.lang.core.mir.schemas.MirSpan
import org.rust.lang.core.psi.RsLitExpr
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.ext.ArithmeticOp
import org.rust.lang.core.psi.ext.LogicOp
import org.rust.lang.core.psi.ext.UnaryOperator
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.regions.Scope as RegionScope

sealed class ThirExpr(val ty: Ty, val span: MirSpan) {

    /**
     * The lifetime of this expression if it should be spilled into a temporary;
     * Should be `null` only if in a constant context
     */
    val tempLifetime: RegionScope? = null  // TODO

    class Scope(
        val regionScope: RegionScope,
        val expr: ThirExpr,
        ty: Ty,
        span: MirSpan,
    ) : ThirExpr(ty, span)

    class Literal(
        val literal: RsLitExpr,
        val neg: Boolean,
        ty: Ty,
        span: MirSpan,
    ) : ThirExpr(ty, span)

    class Unary(
        val op: UnaryOperator,
        val arg: ThirExpr,
        ty: Ty,
        span: MirSpan,
    ) : ThirExpr(ty, span)

    class Binary(
        val op: ArithmeticOp,
        val left: ThirExpr,
        val right: ThirExpr,
        ty: Ty,
        span: MirSpan,
    ) : ThirExpr(ty, span)

    class Logical(
        val op: LogicOp,
        val left: ThirExpr,
        val right: ThirExpr,
        ty: Ty,
        span: MirSpan,
    ) : ThirExpr(ty, span)

    class Block(
        val block: ThirBlock,
        ty: Ty,
        span: MirSpan,
    ) : ThirExpr(ty, span)

    class If(
        val ifThenScope: RegionScope.IfThen,
        val cond: ThirExpr,
        val then: ThirExpr,
        val `else`: ThirExpr?,
        ty: Ty,
        span: MirSpan,
    ) : ThirExpr(ty, span)

    class Tuple(
        val fields: List<ThirExpr>,
        ty: Ty,
        span: MirSpan,
    ) : ThirExpr(ty, span)

    /** Access to a field of a struct, a tuple, an union, or an enum */
    class Field(
        val expr: ThirExpr,
        /** This can be a named (`.foo`) or unnamed (`.0`) field */
        val fieldIndex: MirFieldIndex,
        ty: Ty,
        span: MirSpan,
    ) : ThirExpr(ty, span)

    class Loop(
        val body: ThirExpr,
        ty: Ty,
        span: MirSpan,
    ) : ThirExpr(ty, span)

    class NeverToAny(
        val spanExpr: ThirExpr,
        ty: Ty,
        span: MirSpan
    ) : ThirExpr(ty, span)

    class Break(
        val label: RegionScope,
        val expr: ThirExpr?,
        ty: Ty,
        span: MirSpan,
    ) : ThirExpr(ty, span)

    class VarRef(
        val local: LocalVar,
        ty: Ty,
        span: MirSpan,
    ) : ThirExpr(ty, span)

    class Assign(
        val left: ThirExpr,
        val right: ThirExpr,
        ty: Ty,
        span: MirSpan,
    ) : ThirExpr(ty, span)

    class Adt(
        val definition: RsStructItem,
        // TODO: more properties here
        ty: Ty,
        span: MirSpan,
    ) : ThirExpr(ty, span)

    class Borrow(
        val kind: MirBorrowKind,
        val arg: ThirExpr,
        ty: Ty,
        span: MirSpan,
    ) : ThirExpr(ty, span)
}

typealias MirFieldIndex = Int
