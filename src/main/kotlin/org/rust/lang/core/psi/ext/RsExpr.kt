/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.macros.RsExpandedElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.stubs.RsPlaceholderStub

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
    override val traitName: String,
    override val itemName: String,
    override val sign: String
) : BinaryOperator(), OverloadableBinaryOperator {
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
    override val traitName: String,
    override val itemName: String,
    override val sign: String
) : AssignmentOp(), OverloadableBinaryOperator {
    object ANDEQ : ArithmeticAssignmentOp("BitAndAssign", "bitand_assign", "&=") // `a &= b`
    object OREQ : ArithmeticAssignmentOp("BitOrAssign", "bitor_assign", "|=") // `a |= b`
    object PLUSEQ : ArithmeticAssignmentOp("AddAssign", "add_assign", "+=") // `a += b`
    object MINUSEQ : ArithmeticAssignmentOp("SubAssign", "sub_assign", "-=") // `a -= b`
    object MULEQ : ArithmeticAssignmentOp("MulAssign", "mul_assign", "*=") // `a *= b`
    object DIVEQ : ArithmeticAssignmentOp("DivAssign", "div_assign", "/=") // `a /= b`
    object REMEQ : ArithmeticAssignmentOp("RemAssign", "rem_assign", "%=") // `a %= b`
    object XOREQ : ArithmeticAssignmentOp("BitXorAssign", "bitxor_assign", "^=") // `a ^= b`
    object GTGTEQ : ArithmeticAssignmentOp("ShrAssign", "shr_assign", ">>=") // `a >>= b`
    object LTLTEQ : ArithmeticAssignmentOp("ShlAssign", "shl_assign", "<<=") // `a <<= b`

    override val fnName: String get() = itemName

    companion object {
        fun values(): List<ArithmeticAssignmentOp> = listOf(ANDEQ, OREQ, PLUSEQ, MINUSEQ, MULEQ, DIVEQ, REMEQ, XOREQ, GTGTEQ, LTLTEQ)
    }
}

val RsBinaryOp.operatorType: BinaryOperator get() = when (op) {
    "+" -> ArithmeticOp.ADD
    "-" -> ArithmeticOp.SUB
    "*" -> ArithmeticOp.MUL
    "/" -> ArithmeticOp.DIV
    "%" -> ArithmeticOp.REM
    "&" -> ArithmeticOp.BIT_AND
    "|" -> ArithmeticOp.BIT_OR
    "^" -> ArithmeticOp.BIT_XOR
    "<<" -> ArithmeticOp.SHL
    ">>" -> ArithmeticOp.SHR

    "&&" -> LogicOp.AND
    "||" -> LogicOp.OR

    "==" -> EqualityOp.EQ
    "!=" -> EqualityOp.EXCLEQ

    ">" -> ComparisonOp.GT
    "<" -> ComparisonOp.LT
    "<=" -> ComparisonOp.LTEQ
    ">=" -> ComparisonOp.GTEQ

    "=" -> AssignmentOp.EQ
    "&=" -> ArithmeticAssignmentOp.ANDEQ
    "|=" -> ArithmeticAssignmentOp.OREQ
    "+=" -> ArithmeticAssignmentOp.PLUSEQ
    "-=" -> ArithmeticAssignmentOp.MINUSEQ
    "*=" -> ArithmeticAssignmentOp.MULEQ
    "/=" -> ArithmeticAssignmentOp.DIVEQ
    "%=" -> ArithmeticAssignmentOp.REMEQ
    "^=" -> ArithmeticAssignmentOp.XOREQ
    ">>=" -> ArithmeticAssignmentOp.GTGTEQ
    "<<=" -> ArithmeticAssignmentOp.LTLTEQ

    else -> error("Unknown binary operator type: `$text`")
}

val RsBinaryExpr.operator: PsiElement get() = binaryOp.operator
val RsBinaryExpr.operatorType: BinaryOperator get() = binaryOp.operatorType

abstract class RsExprMixin : RsStubbedElementImpl<RsPlaceholderStub>, RsExpr {
    constructor(node: ASTNode) : super(node)
    constructor(stub: RsPlaceholderStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getContext(): PsiElement? = RsExpandedElement.getContextImpl(this)
}

tailrec fun unwrapParenExprs(expr: RsExpr): RsExpr =
    if (expr is RsParenExpr) unwrapParenExprs(expr.expr) else expr

val RsExpr.isAssignBinaryExpr: Boolean
    get() = this is RsBinaryExpr && this.operatorType is AssignmentOp
