package org.rust.ide.hints

import com.intellij.lang.ExpressionTypeProvider
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.SyntaxTraverser
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RustItemElement
import org.rust.lang.core.types.util.resolvedType


class RustExpressionTypeProvider : ExpressionTypeProvider<RsExpr>() {

    override fun getErrorHint(): String = "Select an expression!"

    override fun getExpressionsAt(pivot: PsiElement): MutableList<RsExpr> =
        SyntaxTraverser.psiApi().parents(pivot)
            .takeWhile { it !is RustItemElement }
            .filter(RsExpr::class.java)
            .toList()

    override fun getInformationHint(element: RsExpr): String =
        StringUtil.escapeXml(element.resolvedType.toString())

}
