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

interface OverloadableBinaryOperator {
    val modName: String
    val traitName: String
    val itemName: String
    val fnName: String
    val sign: String

    operator fun component1(): String = traitName
    operator fun component2(): String = itemName
    operator fun component3(): String = fnName
    operator fun component4(): String = sign
}

sealed class BinaryOperator

sealed class ArithmeticOp(
    override val modName: String,
    override val traitName: String,
    override val itemName: String,
    override val sign: String
) : BinaryOperator(), OverloadableBinaryOperator {
    object ADD : ArithmeticOp("arith", "Add", "add", "+") // `a + b`
    object SUB : ArithmeticOp("arith", "Sub", "sub", "-") // `a - b`
    object MUL : ArithmeticOp("arith", "Mul", "mul", "*") // `a * b`
    object DIV : ArithmeticOp("arith", "Div", "div", "/") // `a / b`
    object REM : ArithmeticOp("arith", "Rem", "rem", "%") // `a % b`
    object BIT_AND : ArithmeticOp("bit", "BitAnd", "bitand", "&") // `a & b`
    object BIT_OR : ArithmeticOp("bit", "BitOr", "bitor", "|") // `a | b`
    object BIT_XOR : ArithmeticOp("bit", "BitXor", "bitxor", "^") // `a ^ b`
    object SHL : ArithmeticOp("bit", "Shl", "shl", "<<") // `a << b`
    object SHR : ArithmeticOp("bit", "Shr", "shr", ">>") // `a >> b

    override val fnName: String get() = itemName

    companion object {
        fun values(): List<ArithmeticOp> = listOf(ADD, SUB, MUL, DIV, REM, BIT_AND, BIT_OR, BIT_XOR, SHL, SHR)
    }
}

sealed class BoolOp : BinaryOperator()

sealed class LogicOp : BoolOp() {
    object AND : LogicOp() // `a && b`
    object OR : LogicOp() // `a || b`
}

sealed class EqualityOp(
    override val sign: String
) : BoolOp(), OverloadableBinaryOperator {
    object EQ : EqualityOp("==") // `a == b`
    object EXCLEQ : EqualityOp("!=") // `a != b`

    override val modName: String = "cmp"
    override val traitName: String = "PartialEq"
    override val itemName: String = "eq"
    override val fnName: String = "eq"

    companion object {
        fun values(): List<EqualityOp> = listOf(EQ, EXCLEQ)
    }
}

sealed class ComparisonOp(
    override val sign: String
) : BoolOp(), OverloadableBinaryOperator {
    object LT : ComparisonOp("<") // `a < b`
    object LTEQ : ComparisonOp("<=") // `a <= b`
    object GT : ComparisonOp(">") // `a > b`
    object GTEQ : ComparisonOp(">=") // `a >= b`

    override val modName: String = "cmp"
    override val traitName: String = "PartialOrd"
    override val itemName: String = "ord"
    override val fnName: String = "partial_cmp"

    companion object {
        fun values(): List<ComparisonOp> = listOf(LT, LTEQ, GT, GTEQ)
    }
}

sealed class AssignmentOp : BinaryOperator() {
    object EQ : AssignmentOp() // `a = b`
}

sealed class ArithmeticAssignmentOp(
    override val modName: String,
    override val traitName: String,
    override val itemName: String,
    override val sign: String
) : AssignmentOp(), OverloadableBinaryOperator {
    object ANDEQ : ArithmeticAssignmentOp("arith", "BitAndAssign", "bitand_assign", "&=") // `a &= b`
    object OREQ : ArithmeticAssignmentOp("arith", "BitOrAssign", "bitor_assign", "|=") // `a |= b`
    object PLUSEQ : ArithmeticAssignmentOp("arith", "AddAssign", "add_assign", "+=") // `a += b`
    object MINUSEQ : ArithmeticAssignmentOp("arith", "SubAssign", "sub_assign", "-=") // `a -= b`
    object MULEQ : ArithmeticAssignmentOp("arith", "MulAssign", "mul_assign", "*=") // `a *= b`
    object DIVEQ : ArithmeticAssignmentOp("arith", "DivAssign", "div_assign", "/=") // `a /= b`
    object REMEQ : ArithmeticAssignmentOp("arith", "RemAssign", "rem_assign", "%=") // `a %= b`
    object XOREQ : ArithmeticAssignmentOp("bit", "BitXorAssign", "bitxor_assign", "^=") // `a ^= b`
    object GTGTEQ : ArithmeticAssignmentOp("bit", "ShrAssign", "shr_assign", ">>=") // `a >>= b`
    object LTLTEQ : ArithmeticAssignmentOp("bit", "ShlAssign", "shl_assign", "<<=") // `a <<= b`

    override val fnName: String get() = itemName

    companion object {
        fun values(): List<ArithmeticAssignmentOp> = listOf(ANDEQ, OREQ, PLUSEQ, MINUSEQ, MULEQ, DIVEQ, REMEQ, XOREQ, GTGTEQ, LTLTEQ)
    }
}

val RsBinaryOp.operator: PsiElement
    get() = requireNotNull(node.findChildByType(RS_BINARY_OPS)) { "guaranteed to be not-null by parser" }.psi

val RsBinaryOp.operatorType: BinaryOperator get() = when (operator.elementType) {
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

    EQEQ -> EqualityOp.EQ
    EXCLEQ -> EqualityOp.EXCLEQ

    GT -> ComparisonOp.GT
    LT -> ComparisonOp.LT
    LTEQ -> ComparisonOp.LTEQ
    GTEQ -> ComparisonOp.GTEQ

    EQ -> AssignmentOp.EQ
    ANDEQ -> ArithmeticAssignmentOp.ANDEQ
    OREQ -> ArithmeticAssignmentOp.OREQ
    PLUSEQ -> ArithmeticAssignmentOp.PLUSEQ
    MINUSEQ -> ArithmeticAssignmentOp.MINUSEQ
    MULEQ -> ArithmeticAssignmentOp.MULEQ
    DIVEQ -> ArithmeticAssignmentOp.DIVEQ
    REMEQ -> ArithmeticAssignmentOp.REMEQ
    XOREQ -> ArithmeticAssignmentOp.XOREQ
    GTGTEQ -> ArithmeticAssignmentOp.GTGTEQ
    LTLTEQ -> ArithmeticAssignmentOp.LTLTEQ

    else -> error("Unknown binary operator type: `$text`")
}

val RsBinaryExpr.operator: PsiElement get() = binaryOp.operator
val RsBinaryExpr.operatorType: BinaryOperator get() = binaryOp.operatorType
