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
        // https://github.com/rust-lang/rust/blob/f7b831ac8a897273f78b9f47165cf8e54066ce4b/compiler/rustc_mir_build/src/build/expr/category.rs#L34
        fun of(element: ThirExpr): MirCategory? {
            return when (element) {
                is ThirExpr.Deref,
                is ThirExpr.Field,
                is ThirExpr.Deref,
                is ThirExpr.Index,
                is ThirExpr.UpvarRef,
                is ThirExpr.VarRef,
                is ThirExpr.PlaceTypeAscription,
                is ThirExpr.ValueTypeAscription -> {
                    Place
                }
                is ThirExpr.ConstBlock,
                is ThirExpr.Literal,
                is ThirExpr.NonHirLiteral,
                is ThirExpr.ZstLiteral,
                is ThirExpr.ConstParam,
                is ThirExpr.StaticRef,
                is ThirExpr.NamedConst -> {
                    Constant
                }
                is ThirExpr.Array,
                is ThirExpr.Tuple,
                is ThirExpr.Closure,
                is ThirExpr.Unary,
                is ThirExpr.Binary,
                is ThirExpr.Box,
                is ThirExpr.Cast,
                is ThirExpr.Pointer,
                is ThirExpr.Repeat,
                is ThirExpr.Assign,
                is ThirExpr.AssignOp,
                is ThirExpr.ThreadLocalRef,
                is ThirExpr.OffsetOf -> {
                    Rvalue(MirRvalueFunc.AS_RVALUE)
                }
                is ThirExpr.Logical,
                is ThirExpr.Match,
                is ThirExpr.If,
                is ThirExpr.Let,
                is ThirExpr.NeverToAny,
                is ThirExpr.Use,
                is ThirExpr.Adt,
                is ThirExpr.Borrow,
                is ThirExpr.AddressOf,
                is ThirExpr.Yield,
                is ThirExpr.Call,
                is ThirExpr.InlineAsm,
                is ThirExpr.Loop,
                is ThirExpr.Block,
                is ThirExpr.Break,
                is ThirExpr.Continue,
                is ThirExpr.Return -> {
                    Rvalue(MirRvalueFunc.INTO)
                }
                is ThirExpr.Scope -> {
                    null
                }
            }
        }
    }
}

