/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.consts

import org.rust.lang.utils.evaluation.ConstExpr

fun Const.asBool(): Boolean? {
    if (this !is CtValue) return null
    return (expr as? ConstExpr.Value.Bool)?.value
}

fun Const.asLong(): Long? {
    if (this !is CtValue) return null
    return (expr as? ConstExpr.Value.Integer)?.value
}

data class CtValue(val expr: ConstExpr.Value<*>) : Const() {
    override fun toString(): String = expr.toString()
}
