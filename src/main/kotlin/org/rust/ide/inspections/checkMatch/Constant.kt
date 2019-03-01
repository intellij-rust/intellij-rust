/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.checkMatch

import org.rust.lang.core.psi.RsPathExpr
import org.rust.lang.core.resolve.ref.deepResolve

sealed class Constant {
    data class Boolean(val value: kotlin.Boolean) : Constant() {
        override fun toString() = value.toString()
    }

    data class Integer(val value: Number) : Constant() {
        override fun toString() = value.toString()
    }

    data class Float(val value: kotlin.Double) : Constant() {
        override fun toString() = value.toString()
    }

    data class String(val value: kotlin.String) : Constant() {
        override fun toString() = value
    }

    data class Char(val value: kotlin.String) : Constant() {
        override fun toString() = value
    }

    data class Path(val value: RsPathExpr) : Constant() {
        override fun toString() = value.toString()
    }

    object Unknown : Constant()


    operator fun compareTo(other: Constant): Int {
        return when {
            this is Constant.Boolean && other is Constant.Boolean -> value.compareTo(other.value)

            this is Constant.Integer && other is Constant.Integer -> value.toLong().compareTo(other.value.toLong())

            this is Float && other is Float -> value.compareTo(other.value)

            this is Constant.String && other is Constant.String -> value.compareTo(other.value)

            this is Constant.Char && other is Constant.Char -> value.compareTo(other.value)

            this is Constant.Path && other is Constant.Path -> {
                val aR = value.path.reference.deepResolve()
                val bR = other.value.path.reference.deepResolve()
                if (aR?.equals(bR) == true) 0
                else -1
            }

            else -> -1
        }
    }
}
