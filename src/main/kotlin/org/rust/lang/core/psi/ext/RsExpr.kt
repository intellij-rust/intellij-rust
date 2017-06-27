/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.*

/**
 * Extracts [RsLitExpr] raw value
 */
val RsLitExpr.stringLiteralValue: String? get() = (kind as? RsTextLiteral)?.value

enum class UnaryOperator {
    REF, // `&a`
    REF_MUT, // `&mut a`
    DEREF, // `*a`
    MINUS, // `-a`
    NOT, // `!a`
    BOX, // `box a`
}

val RsUnaryExpr.operatorType: UnaryOperator get() = when {
    mut != null -> UnaryOperator.REF_MUT
    and != null -> UnaryOperator.REF
    mul != null -> UnaryOperator.DEREF
    minus != null -> UnaryOperator.MINUS
    excl != null -> UnaryOperator.NOT
    box != null -> UnaryOperator.BOX
    else -> error("Unknown unary operator type: `$text`")
}

sealed class BinaryOperator

sealed class ArithmeticOp(val traitName: String, val itemName: String, val sign: String) : BinaryOperator() {
    object ADD : ArithmeticOp("Add", "add", "+") // `a + b`
    object SUB : ArithmeticOp("Sub", "sub", "-") // `a - b`
    object MUL : ArithmeticOp("Mul", "mul", "*") // `a * b`
    object DIV : ArithmeticOp("Div", "div", "/") // `a / b`
    object REM : ArithmeticOp("Rem", "rem", "%") // `a % b`
    object BIT_AND : ArithmeticOp("BitAnd", "bitand", "&") // `a & b`
    object BIT_OR : ArithmeticOp("BitOr", "bitor", "|") // `a | b`
    object BIT_XOR : ArithmeticOp("BitXor", "bitxor", "^") // `a ^ b`
    object SHL : ArithmeticOp("Shl", "shl", "<<") // `a << b`
    object SHR : ArithmeticOp("Shr", "shr", ">>") // `a >> b

    operator fun component1(): String = traitName
    operator fun component2(): String = itemName
    operator fun component3(): String = sign

    companion object {
        fun values(): List<ArithmeticOp> = listOf(ADD, SUB, MUL, DIV, REM, BIT_AND, BIT_OR, BIT_XOR, SHL, SHR)
    }
}

sealed class BoolOp : BinaryOperator()

sealed class LogicOp : BoolOp() {
    object AND : LogicOp() // `a && b`
    object OR : LogicOp() // `a || b`
}

sealed class ComparisonOp : BoolOp() {
    object EQ : ComparisonOp() // `a == b`
    object EXCLEQ : ComparisonOp() // `a != b`
    object LT : ComparisonOp() // `a < b`
    object LTEQ : ComparisonOp() // `a <= b`
    object GT : ComparisonOp() // `a > b`
    object GTEQ : ComparisonOp() // `a >= b`
}

sealed class AssignmentOp : BinaryOperator() {
    object EQ : AssignmentOp() // `a = b`
    object ANDEQ : AssignmentOp() // `a &= b`
    object OREQ : AssignmentOp() // `a |= b`
    object PLUSEQ : AssignmentOp() // `a += b`
    object MINUSEQ : AssignmentOp() // `a -= b`
    object MULEQ : AssignmentOp() // `a *= b`
    object DIVEQ : AssignmentOp() // `a /= b`
    object REMEQ : AssignmentOp() // `a %= b`
    object XOREQ : AssignmentOp() // `a ^= b`
    object GTGTEQ : AssignmentOp() // `a >>= b`
    object LTLTEQ : AssignmentOp() // `a <<= b`
}

val RsBinaryExpr.operator: PsiElement
    get() = requireNotNull(node.findChildByType(BINARY_OPS)) { "guaranteed to be not-null by parser" }.psi

val RsBinaryExpr.operatorType: BinaryOperator get() = when (operator.elementType) {
    PLUS -> ArithmeticOp.ADD
    MINUS -> ArithmeticOp.SUB
    MUL -> ArithmeticOp.MUL
    DIV -> ArithmeticOp.DIV
    REM -> ArithmeticOp.REM
    AND -> ArithmeticOp.BIT_AND
    OR -> ArithmeticOp.BIT_OR
    XOR -> ArithmeticOp.BIT_XOR
    LTLT -> ArithmeticOp.SHL
    GTGT -> ArithmeticOp.SHR

    ANDAND -> LogicOp.AND
    OROR -> LogicOp.OR

    EQEQ -> ComparisonOp.EQ
    EXCLEQ -> ComparisonOp.EXCLEQ
    GT -> ComparisonOp.GT
    LT -> ComparisonOp.LT
    LTEQ -> ComparisonOp.LTEQ
    GTEQ -> ComparisonOp.GTEQ

    EQ -> AssignmentOp.EQ
    ANDEQ -> AssignmentOp.ANDEQ
    OREQ -> AssignmentOp.OREQ
    PLUSEQ -> AssignmentOp.PLUSEQ
    MINUSEQ -> AssignmentOp.MINUSEQ
    MULEQ -> AssignmentOp.MULEQ
    DIVEQ -> AssignmentOp.DIVEQ
    REMEQ -> AssignmentOp.REMEQ
    XOREQ -> AssignmentOp.XOREQ
    GTGTEQ -> AssignmentOp.GTGTEQ
    LTLTEQ -> AssignmentOp.LTLTEQ

    else -> error("Unknown binary operator type: `$text`")
}

private val BINARY_OPS = tokenSetOf(
    AND,
    ANDEQ,
    DIV,
    DIVEQ,
    EQ,
    EQEQ,
    EXCLEQ,
    GT,
    LT,
    MINUS,
    MINUSEQ,
    MUL,
    MULEQ,
    OR,
    OREQ,
    PLUS,
    PLUSEQ,
    REM,
    REMEQ,
    XOR,
    XOREQ,
    GTGTEQ,
    GTGT,
    GTEQ,
    LTLTEQ,
    LTLT,
    LTEQ,
    OROR,
    ANDAND
)
