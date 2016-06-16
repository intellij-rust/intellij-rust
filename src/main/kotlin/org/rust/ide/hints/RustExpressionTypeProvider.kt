package org.rust.ide.hints

import com.intellij.lang.ExpressionTypeProvider
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.SyntaxTraverser
import org.rust.lang.core.psi.RustExprElement
import org.rust.lang.core.psi.RustItemElement
import org.rust.lang.core.types.util.resolvedType


class RustExpressionTypeProvider : ExpressionTypeProvider<RustExprElement>() {

    override fun getErrorHint(): String = "Select an expression!"

    override fun getExpressionsAt(pivot: PsiElement): MutableList<RustExprElement> =
        SyntaxTraverser.psiApi().parents(pivot)
                                .takeWhile  { it !is RustItemElement }
                                .filter(RustExprElement::class.java)
                                .toList()

    override fun getInformationHint(element: RustExprElement): String =
        StringUtil.escapeXml(element.resolvedType.toString())

}
