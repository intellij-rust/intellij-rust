package org.rust.lang.core.psi.ext

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.*

/**
 *  `RsExpr` related extensions
 */
// TODO: rename to make it clear that these are fields of the type and not of the expression.
val RsStructExpr.fields: List<RsNamedElement>
    get() = (path.reference.resolve() as? RsFieldsOwner)?.namedFields.orEmpty()


/**
 * Extracts [RsLitExpr] raw value
 */
val RsLitExpr.stringLiteralValue: String? get() = (kind as? RsTextLiteral)?.value

/**
 * Extracts the expression that defines the size of an array.
 */
val RsArrayExpr.sizeExpr: RsExpr?
    get() = if (semicolon != null && exprList.size == 2) exprList[1] else null


val RsBinaryExpr.operator: PsiElement
    get() = requireNotNull(node.findChildByType(BINARY_OPS)) { "guaranteed to be not-null by parser" }.psi

val RsBinaryExpr.operatorType: IElementType
    get() = operator.elementType


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
