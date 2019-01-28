/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.dfa.value

import org.rust.lang.core.dfa.LongRangeSet
import org.rust.lang.core.psi.RsLitExpr
import org.rust.lang.core.psi.RsLiteralKind
import org.rust.lang.core.psi.kind
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyBool
import org.rust.lang.core.types.ty.TyInteger
import org.rust.lang.core.types.type

class DfaConstValue(factory: DfaValueFactory, val value: Any, override val type: Ty) : DfaValue(factory) {
    override val negated: DfaValue
        get() = when (this) {
            dfaTrue -> dfaFalse
            dfaFalse -> dfaTrue
            else -> DfaUnknownValue
        }

    override val invert: DfaValue
        get() = when (type) {
            is TyBool -> negated
            is TyInteger -> factory.createRange(LongRangeSet.fromDfaValue(this)?.invert)
            else -> DfaUnknownValue
        }

    override val minus: DfaValue
        get() = when {
            type is TyInteger && value is Long -> factory.constFactory.createFromValue(-value, type)
            else -> DfaUnknownValue
        }

    override val isEmpty: Boolean get() = this == dfaNothing

    override fun unite(other: DfaValue): DfaValue = when {
        other !is DfaConstValue -> DfaUnknownValue
        this == other -> this
        this == dfaNothing -> other
        other == dfaNothing -> this
        else -> DfaUnknownValue
    }

    override fun toString(): String = if (isEmpty) "{}" else "{$value}"

    private val dfaTrue: DfaValue get() = factory.constFactory.dfaTrue
    private val dfaFalse: DfaValue get() = factory.constFactory.dfaFalse
    private val dfaNothing: DfaValue get() = factory.constFactory.dfaNothing
}

class DfaConstFactory(val factory: DfaValueFactory) {
    private val values = hashMapOf<Any, DfaConstValue>()
    val dfaTrue = DfaConstValue(factory, true, TyBool)
    val dfaFalse = DfaConstValue(factory, false, TyBool)
    val dfaNothing = DfaConstValue(factory, 0, TyBool)

    fun create(expr: RsLitExpr): DfaValue {
        val kind = expr.kind

        return when (kind) {
            is RsLiteralKind.Integer -> {
                val number = kind.toStringNumber() ?: return DfaUnknownValue
                factory.createIntegerValue(number, expr.type)
            }
            is RsLiteralKind.Boolean -> createFromValue(kind.value, TyBool)
            else -> DfaUnknownValue
        }
    }

    fun createFromValue(value: Any, type: Ty): DfaConstValue {
        if (value == true) return dfaTrue
        if (value == false) return dfaFalse
        return values.getOrPut(value) { DfaConstValue(factory, value, type) }
    }
}

private fun RsLiteralKind.Integer.toStringNumber(): String? = this.offsets.value?.substring(this.node.text)?.filter { it != '_' }

/**
 * Expected integer literal
 */
fun integerFromLitExpr(expr: RsLitExpr): String {
    val kind = expr.kind as? RsLiteralKind.Integer ?: error("Expected integer literal")
    return kind.toStringNumber() ?: error("Expected number")
}
