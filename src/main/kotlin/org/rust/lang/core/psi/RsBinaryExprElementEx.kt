package org.rust.lang.core.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.RsTokenElementTypes.BINARY_OPS
import org.rust.lang.core.psi.util.elementType

val RsBinaryExpr.operator: PsiElement
    get() = requireNotNull(node.findChildByType(BINARY_OPS)) { "guaranteed to be not-null by parser" }.psi

val RsBinaryExpr.operatorType: IElementType
    get() = operator.elementType
