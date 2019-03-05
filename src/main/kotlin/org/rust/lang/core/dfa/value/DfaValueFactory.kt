/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.dfa.value

import org.rust.lang.core.dfa.DfaFactType
import org.rust.lang.core.dfa.LongRangeSet
import org.rust.lang.core.psi.RsLitExpr
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyBool
import org.rust.lang.core.types.ty.TyInteger

class DfaValueFactory {
    private val values = mutableListOf<DfaValue>()

    init {
        values += DfaUnknownValue
    }

    val factFactory = DfaFactMapFactory(this)
    val constFactory = DfaConstFactory(this)

    fun registerValue(value: DfaValue): Int {
        values += value
        return values.size - 1
    }

    fun createTypeValue(type: Ty?): DfaValue = when (type) {
        is TyInteger -> createRange(LongRangeSet.fromType(type))
        is TyBool -> createBoolValue(null)
        else -> DfaUnknownValue
    }

    fun createIntegerValue(expr: String, type: Ty): DfaValue {
        val number = expr.toLongOrNull()
        val range = LongRangeSet.fromConstant(number, type)
        return if (range != null) createFactValue(DfaFactType.RANGE, range)
        else DfaUnknownValue
    }

    fun createLiteralValue(expr: RsLitExpr): DfaValue = constFactory.create(expr)

    fun createRange(value: LongRangeSet?) = createFactValue(DfaFactType.RANGE, value)

    fun createBoolValue(value: Boolean?): DfaValue = if (value != null) constFactory.createFromValue(value, TyBool) else DfaUnknownValue

    fun <T> createFactValue(factType: DfaFactType<T>, value: T?): DfaValue = factFactory.createValue(factType, value)

    fun getValue(id: Int): DfaValue = values[id]
}
