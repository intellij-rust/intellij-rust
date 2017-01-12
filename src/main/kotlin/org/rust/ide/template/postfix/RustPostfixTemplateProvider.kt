package org.rust.ide.template.postfix

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateWithExpressionSelector
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.impl.RustFile
import org.rust.lang.refactoring.RustLocalVariableHandler

class RustPostfixTemplateProvider : PostfixTemplateProvider {
    private val templates: Set<PostfixTemplate> = setOf(
        AssertPostfixTemplate(),
        DebugAssertPostfixTemplate(),
        IfExpressionPostfixTemplate(),
        ElseExpressionPostfixTemplate(),
        WhileExpressionPostfixTemplate(),
        WhileNotExpressionPostfixTemplate(),
        MatchPostfixTemplate(),
        ParenPostfixTemplate(),
        LambdaPostfixTemplate(),
        LetPostfixTemplate()
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

class LetPostfixTemplate : PostfixTemplateWithExpressionSelector(
    "let",
    "let name = expr;",
    RustAllParentsSelector(RsExpr::any)
) {
    override fun expandForChooseExpression(expression: PsiElement, editor: Editor) {
        var rustFile = expression.containingFile as? RustFile ?: return
        if (expression !is RsExpr) return
        RustLocalVariableHandler().doRefactoring(editor, expression.project, rustFile, expression)
    }
}
