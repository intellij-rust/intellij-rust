/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.utils.evaluation

sealed class ExprValue {
    data class Bool(val value: Boolean) : ExprValue() {
        override fun toString(): String = value.toString()
    }

    data class Integer(val value: Long) : ExprValue() {
        override fun toString(): String = value.toString()
    }

    data class Float(val value: Double) : ExprValue() {
        override fun toString(): String = value.toString()
    }

    data class Str(val value: String) : ExprValue() {
        override fun toString(): String = value
    }

    data class Char(val value: String) : ExprValue() {
        override fun toString(): String = value
    }
}
