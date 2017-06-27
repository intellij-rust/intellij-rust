/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

import com.intellij.codeInsight.template.postfix.templates.SurroundPostfixTemplateBase
import com.intellij.lang.surroundWith.Surrounder
import com.intellij.psi.PsiElement
import org.rust.ide.surroundWith.expression.RsWithIfExpSurrounder
import org.rust.ide.surroundWith.expression.RsWithParenthesesSurrounder
import org.rust.ide.surroundWith.expression.RsWithWhileExpSurrounder
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.utils.negate

class IfExpressionPostfixTemplate : SurroundPostfixTemplateBase(
    "if",
    "if exp {}",
    RsPostfixTemplatePsiInfo,
    RsTopMostInScopeSelector(RsExpr::isBool)
) {
    override fun getSurrounder(): Surrounder = RsWithIfExpSurrounder()
}

class ElseExpressionPostfixTemplate : SurroundPostfixTemplateBase(
    "else",
    "if !exp {}",
    RsPostfixTemplatePsiInfo,
    RsTopMostInScopeSelector(RsExpr::isBool)
) {
    override fun getSurrounder(): Surrounder = RsWithIfExpSurrounder()

    override fun getWrappedExpression(expression: PsiElement?): PsiElement = checkNotNull(expression).negate()
}

class WhileExpressionPostfixTemplate : SurroundPostfixTemplateBase(
    "while",
    "while exp {}",
    RsPostfixTemplatePsiInfo,
    RsTopMostInScopeSelector(RsExpr::isBool)
) {
    override fun getSurrounder(): Surrounder = RsWithWhileExpSurrounder()
}

class WhileNotExpressionPostfixTemplate : SurroundPostfixTemplateBase(
    "whilenot",
    "while !exp {}",
    RsPostfixTemplatePsiInfo,
    RsTopMostInScopeSelector(RsExpr::isBool)
) {
    override fun getSurrounder(): Surrounder = RsWithWhileExpSurrounder()

    override fun getWrappedExpression(expression: PsiElement?): PsiElement = checkNotNull(expression).negate()
}

class ParenPostfixTemplate : SurroundPostfixTemplateBase(
    "par",
    "(expr)",
    RsPostfixTemplatePsiInfo,
    RsAllParentsSelector({ true })
) {
    override fun getSurrounder(): Surrounder = RsWithParenthesesSurrounder()
}
