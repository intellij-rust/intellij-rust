/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

import org.rust.lang.core.thir.ThirExpr

sealed class MirCategory {
    object Place : MirCategory() {
        override fun toString() = "Place"
    }

    object Constant : MirCategory() {
        override fun toString() = "Constant"
    }

    data class Rvalue(val value: MirRvalueFunc) : MirCategory()

    companion object {
        fun of(element: ThirExpr): MirCategory? {
            return when (element) {
                is ThirExpr.Field,
                is ThirExpr.VarRef -> {
                    Place
                }
                is ThirExpr.Literal -> {
                    Constant
                }
                is ThirExpr.Unary,
                is ThirExpr.Binary,
                is ThirExpr.Assign,
                is ThirExpr.Tuple -> {
                    Rvalue(MirRvalueFunc.AS_RVALUE)
                }
                is ThirExpr.Block,
                is ThirExpr.If,
                is ThirExpr.Adt,
                is ThirExpr.Logical,
                is ThirExpr.Loop,
                is ThirExpr.Borrow,
                is ThirExpr.NeverToAny,
                is ThirExpr.Break -> {
                    Rvalue(MirRvalueFunc.INTO)
                }
                is ThirExpr.Scope -> {
                    null
                }
            }
        }
    }
}

