package org.rust.ide.hints

import com.intellij.lang.ExpressionTypeProvider
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.SyntaxTraverser
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.ext.RsItemElement
import org.rust.lang.core.types.type


class RsExpressionTypeProvider : ExpressionTypeProvider<RsExpr>() {

    override fun getErrorHint(): String = "Select an expression!"

    override fun getExpressionsAt(pivot: PsiElement): MutableList<RsExpr> =
        SyntaxTraverser.psiApi().parents(pivot)
            .takeWhile { it !is RsItemElement }
            .filter(RsExpr::class.java)
            .toList()

    override fun getInformationHint(element: RsExpr): String =
        StringUtil.escapeXml(element.type.toString())

}
