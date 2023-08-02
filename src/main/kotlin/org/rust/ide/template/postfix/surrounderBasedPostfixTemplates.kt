/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.codeInsight.template.postfix.templates.SurroundPostfixTemplateBase
import com.intellij.lang.surroundWith.Surrounder
import com.intellij.psi.PsiElement
import org.rust.ide.surroundWith.expression.RsWithIfExpSurrounder
import org.rust.ide.surroundWith.expression.RsWithParenthesesSurrounder
import org.rust.ide.surroundWith.expression.RsWithWhileExpSurrounder
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.utils.negate

class IfExpressionPostfixTemplate(provider: PostfixTemplateProvider) : SurroundPostfixTemplateBase(
    "if",
    "if exp {}",
    RsPostfixTemplatePsiInfo,
    RsExprParentsSelector(RsExpr::isBool),
    provider,
) {
    override fun getSurrounder(): Surrounder = RsWithIfExpSurrounder()
}

class ElseExpressionPostfixTemplate(provider: PostfixTemplateProvider) : SurroundPostfixTemplateBase(
    "else",
    "if !exp {}",
    RsPostfixTemplatePsiInfo,
    RsExprParentsSelector(RsExpr::isBool),
    provider,
) {
    override fun getSurrounder(): Surrounder = RsWithIfExpSurrounder()

    override fun getWrappedExpression(expression: PsiElement?): PsiElement = checkNotNull(expression).negate()
}

class WhileExpressionPostfixTemplate(provider: PostfixTemplateProvider) : SurroundPostfixTemplateBase(
    "while",
    "while exp {}",
    RsPostfixTemplatePsiInfo,
    RsExprParentsSelector(RsExpr::isBool),
    provider,
) {
    override fun getSurrounder(): Surrounder = RsWithWhileExpSurrounder()
}

class WhileNotExpressionPostfixTemplate(provider: PostfixTemplateProvider) : SurroundPostfixTemplateBase(
    "whilenot",
    "while !exp {}",
    RsPostfixTemplatePsiInfo,
    RsExprParentsSelector(RsExpr::isBool),
    provider,
) {
    override fun getSurrounder(): Surrounder = RsWithWhileExpSurrounder()

    override fun getWrappedExpression(expression: PsiElement?): PsiElement = checkNotNull(expression).negate()
}

class ParenPostfixTemplate(provider: PostfixTemplateProvider) : SurroundPostfixTemplateBase(
    "par",
    "(expr)",
    RsPostfixTemplatePsiInfo,
    RsExprParentsSelector(),
    provider,
) {
    override fun getSurrounder(): Surrounder = RsWithParenthesesSurrounder()
}
