package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustBinaryExpr
import org.rust.lang.core.psi.impl.RustExprImpl

abstract class RustBinaryExprImplMixin(node: ASTNode) : RustExprImpl(node), RustBinaryExpr {
    override fun getGtgteq(): PsiElement? = findChildByType(GTGTEQ)
    override fun getGtgt(): PsiElement? = findChildByType(GTGT)
    override fun getGteq(): PsiElement? = findChildByType(GTEQ)
    override fun getLtlteq(): PsiElement? = findChildByType(LTLTEQ)
    override fun getLtlt(): PsiElement? = findChildByType(LTLT)
    override fun getLteq(): PsiElement? = findChildByType(LTEQ)
}
