package org.rust.lang.core.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import org.rust.lang.core.psi.RsTokenElementTypes.*
import org.rust.lang.core.psi.util.elementType

val RsBinaryExpr.operator: PsiElement
    get() = requireNotNull(node.findChildByType(BINARY_OPS)) { "guaranteed to be not-null by parser" }.psi

val RsBinaryExpr.operatorType: IElementType
    get() = operator.elementType


private val BINARY_OPS = TokenSet.create(
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
