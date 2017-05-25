package org.rust.lang.core.psi.ext

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
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

enum class ArithmeticOp(val traitName: String, val itemName: String, val sign: String) {
    ADD("Add", "add", "+"), // `a + b`
    SUB("Sub", "sub", "-"), // `a - b`
    MUL("Mul", "mul", "*"), // `a * b`
    DIV("Div", "div", "/"), // `a / b`
    REM("Rem", "rem", "%"), // `a % b`
    BIT_AND("BitAnd", "bitand", "&"), // `a & b`
    BIT_OR("BitOr", "bitor", "|"), // `a | b`
    BIT_XOR("BitXor", "bitxor", "^"), // `a ^ b`
    SHL("Shl", "shl", "<<"), // `a << b`
    SHR("Shr", "shr", ">>"); // `a >> b`

    operator fun component1(): String = traitName
    operator fun component2(): String = itemName
    operator fun component3(): String = sign
}

val RsBinaryExpr.operator: PsiElement
    get() = requireNotNull(node.findChildByType(BINARY_OPS)) { "guaranteed to be not-null by parser" }.psi

// TODO: probably want to use a special `enum` here instead of `IElementType`.
val RsBinaryExpr.operatorType: IElementType
    get() = operator.elementType

val RsBinaryExpr.arithmeticOp: ArithmeticOp? get() = when (operatorType) {
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
    else -> null
}

val BOOL_BINARY_OPS = tokenSetOf(
    ANDAND,
    OROR,
    EQEQ,
    EXCLEQ,
    LT,
    GT,
    GTEQ,
    LTEQ
)

val ARITHMETIC_BINARY_OPS = tokenSetOf(
    PLUS,
    MINUS,
    MUL,
    DIV,
    REM,
    AND,
    OR,
    XOR,
    LTLT,
    GTGT
)

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
