/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateWithExpressionSelector
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.ide.refactoring.introduceVariable.extractExpression
import org.rust.lang.core.psi.RsExpr

class RsPostfixTemplateProvider : PostfixTemplateProvider {
    private val templates: Set<PostfixTemplate> = setOf(
        AssertPostfixTemplate(this),
        DebugAssertPostfixTemplate(this),
        IfExpressionPostfixTemplate(),
        ElseExpressionPostfixTemplate(),
        WhileExpressionPostfixTemplate(),
        WhileNotExpressionPostfixTemplate(),
        MatchPostfixTemplate(this),
        ParenPostfixTemplate(),
        LambdaPostfixTemplate(this),
        NotPostfixTemplate(this),
        RefPostfixTemplate(this),
        RefmPostfixTemplate(this),
        LetPostfixTemplate(this),
        IterPostfixTemplate("iter", this),
        IterPostfixTemplate("for", this),
        PrintlnPostfixTemplate(this),
        DbgPostfixTemplate(this)
    )

    override fun getTemplates(): Set<PostfixTemplate> = templates

    override fun isTerminalSymbol(currentChar: Char): Boolean =
        currentChar == '.' || currentChar == '!'

    override fun afterExpand(file: PsiFile, editor: Editor) {
    }

    override fun preCheck(copyFile: PsiFile, realEditor: Editor, currentOffset: Int) = copyFile

    override fun preExpand(file: PsiFile, editor: Editor) {
    }
}

class LetPostfixTemplate(provider: RsPostfixTemplateProvider) :
    PostfixTemplateWithExpressionSelector(null, "let", "let name = expr;", RsAllParentsSelector(), provider) {
    override fun expandForChooseExpression(expression: PsiElement, editor: Editor) {
        if (expression !is RsExpr) return
        extractExpression(editor, expression)
    }
}
