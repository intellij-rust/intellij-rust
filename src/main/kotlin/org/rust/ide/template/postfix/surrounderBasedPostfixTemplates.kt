package org.rust.ide.template.postfix

import com.intellij.codeInsight.template.postfix.templates.SurroundPostfixTemplateBase
import com.intellij.lang.surroundWith.Surrounder
import com.intellij.psi.PsiElement
import org.rust.ide.surroundWith.expression.RustWithIfExpSurrounder
import org.rust.ide.surroundWith.expression.RustWithWhileExpSurrounder
import org.rust.lang.core.psi.RustExprElement
import org.rust.lang.core.types.RustBooleanType
import org.rust.lang.core.types.util.resolvedType
import org.rust.lang.utils.negate

class IfExpressionPostfixTemplate : SurroundPostfixTemplateBase(
    "if",
    "if exp {}",
    RustPostfixTemplatePsiInfo,
    RustTopMostInScopeSelector(RustExprElement::isBool)
) {
    override fun getSurrounder(): Surrounder = RustWithIfExpSurrounder()
}

class ElseExpressionPostfixTemplate : SurroundPostfixTemplateBase(
    "else",
    "if !exp {}",
    RustPostfixTemplatePsiInfo,
    RustTopMostInScopeSelector(RustExprElement::isBool)
) {
    override fun getSurrounder(): Surrounder = RustWithIfExpSurrounder()

    override fun getWrappedExpression(expression: PsiElement?): PsiElement = checkNotNull(expression).negate()
}

class WhileExpressionPostfixTemplate : SurroundPostfixTemplateBase(
    "while",
    "while exp {}",
    RustPostfixTemplatePsiInfo,
    RustTopMostInScopeSelector(RustExprElement::isBool)
) {
    override fun getSurrounder(): Surrounder = RustWithWhileExpSurrounder()
}

class WhileNotExpressionPostfixTemplate : SurroundPostfixTemplateBase(
    "whilenot",
    "while !exp {}",
    RustPostfixTemplatePsiInfo,
    RustTopMostInScopeSelector(RustExprElement::isBool)
) {
    override fun getSurrounder(): Surrounder = RustWithWhileExpSurrounder()

    override fun getWrappedExpression(expression: PsiElement?): PsiElement = checkNotNull(expression).negate()
}

private fun RustExprElement.isBool() = resolvedType == RustBooleanType
