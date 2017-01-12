package org.rust.ide.template.postfix

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateExpressionSelector
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplatePsiInfo
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import com.intellij.util.Function
import org.rust.lang.core.psi.RsBlock
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RustPsiFactory
import org.rust.lang.core.psi.util.ancestors
import org.rust.lang.core.types.RustBooleanType
import org.rust.lang.core.types.RustEnumType
import org.rust.lang.core.types.util.resolvedType
import org.rust.lang.utils.negate

internal object RustPostfixTemplatePsiInfo : PostfixTemplatePsiInfo() {
    override fun getNegatedExpression(element: PsiElement): PsiElement =
        element.negate()

    override fun createExpression(context: PsiElement, prefix: String, suffix: String): PsiElement =
        RustPsiFactory(context.project).createExpression("$prefix${context.text}$suffix")
}

abstract class RustExprParentsSelectorBase(val pred: (RsExpr) -> Boolean) : PostfixTemplateExpressionSelector {
    override fun getRenderer(): Function<PsiElement, String> = Function { it.text }

    abstract override fun getExpressions(context: PsiElement, document: Document, offset: Int): List<PsiElement>
}

class RustTopMostInScopeSelector(pred: (RsExpr) -> Boolean) : RustExprParentsSelectorBase(pred) {
    override fun getExpressions(context: PsiElement, document: Document, offset: Int): List<PsiElement> =
        listOf(
            context
                .ancestors
                .takeWhile { it !is RsBlock }
                .filter { it is RsExpr && pred(it) }
                .last()
        )

    override fun hasExpression(context: PsiElement, copyDocument: Document, newOffset: Int): Boolean =
        context
            .ancestors
            .takeWhile { it !is RsBlock }
            .any { it is RsExpr && pred(it) }
}

class RustAllParentsSelector(pred: (RsExpr) -> Boolean) : RustExprParentsSelectorBase(pred) {
    override fun getExpressions(context: PsiElement, document: Document, offset: Int): List<PsiElement> =
        context
            .ancestors
            .takeWhile { it !is RsBlock }
            .filter { it is RsExpr && pred(it) }
            .toList()

    override fun hasExpression(context: PsiElement, copyDocument: Document, newOffset: Int): Boolean =
        context
            .ancestors
            .takeWhile { it !is RsBlock }
            .any { it is RsExpr && pred(it) }
}

fun RsExpr.isBool() = resolvedType == RustBooleanType
fun RsExpr.isEnum() = resolvedType is RustEnumType
fun RsExpr.any() = true
