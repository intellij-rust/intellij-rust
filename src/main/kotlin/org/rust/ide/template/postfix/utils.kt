package org.rust.ide.template.postfix

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateExpressionSelector
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplatePsiInfo
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import com.intellij.util.Function
import org.rust.lang.core.psi.RustBlockElement
import org.rust.lang.core.psi.RustElementFactory
import org.rust.lang.core.psi.RustExprElement
import org.rust.lang.core.psi.util.ancestors
import org.rust.lang.core.types.RustBooleanType
import org.rust.lang.core.types.util.resolvedType
import org.rust.lang.utils.negate

internal object RustPostfixTemplatePsiInfo : PostfixTemplatePsiInfo() {
    override fun getNegatedExpression(element: PsiElement): PsiElement =
        element.negate()

    override fun createExpression(context: PsiElement, prefix: String, suffix: String): PsiElement =
        RustElementFactory.createExpression(context.project, "$prefix${context.text}$suffix")!!
}

abstract class RustExprParentsSelectorBase(val pred: (RustExprElement) -> Boolean) : PostfixTemplateExpressionSelector {
    override fun getRenderer(): Function<PsiElement, String> = Function { it.text }

    abstract override fun getExpressions(context: PsiElement, document: Document, offset: Int): List<PsiElement>
}

class RustTopMostInScopeSelector(pred: (RustExprElement) -> Boolean) : RustExprParentsSelectorBase(pred) {
    override fun getExpressions(context: PsiElement, document: Document, offset: Int): List<PsiElement> =
        listOf(
            context
                .ancestors
                .takeWhile { it !is RustBlockElement }
                .filter { it is RustExprElement && pred(it) }
                .last()
        )

    override fun hasExpression(context: PsiElement, copyDocument: Document, newOffset: Int): Boolean =
        context
            .ancestors
            .takeWhile { it !is RustBlockElement }
            .any { it is RustExprElement && pred(it) }
}

fun RustExprElement.isBool() = resolvedType == RustBooleanType
