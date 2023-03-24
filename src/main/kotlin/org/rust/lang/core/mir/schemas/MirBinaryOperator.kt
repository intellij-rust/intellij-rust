/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

import org.rust.lang.core.psi.ext.ArithmeticOp
import org.rust.lang.core.psi.ext.BinaryOperator
import org.rust.lang.core.psi.ext.ComparisonOp
import org.rust.lang.core.psi.ext.EqualityOp

sealed interface MirBinaryOperator {
    val underlyingOp: BinaryOperator?

    data class Arithmetic(val op: ArithmeticOp) : MirBinaryOperator {
        override val underlyingOp get() = op
    }

    data class Equality(val op: EqualityOp) : MirBinaryOperator {
        override val underlyingOp get() = op
    }

    data class Comparison(val op: ComparisonOp) : MirBinaryOperator {
        override val underlyingOp get() = op
    }

    object Offset : MirBinaryOperator {
        override val underlyingOp get() = null
    }

    companion object {
        fun BinaryOperator.toMir() = when (this) {
            is ArithmeticOp -> Arithmetic(this)
            is EqualityOp -> Equality(this)
            is ComparisonOp -> Comparison(this)
            else -> error("$this cannot be a mir operator")
        }
    }
}
