/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

import com.intellij.codeInsight.template.impl.TemplateSettings
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateWithExpressionSelector
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplatesUtils
import com.intellij.codeInsight.template.postfix.templates.editable.DefaultPostfixTemplateEditor
import com.intellij.codeInsight.template.postfix.templates.editable.PostfixTemplateEditor
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jdom.Element
import org.rust.ide.refactoring.introduceVariable.extractExpression
import org.rust.ide.template.postfix.editable.RsEditablePostfixTemplate
import org.rust.ide.template.postfix.editable.RsPostfixTemplateEditor
import org.rust.ide.template.postfix.editable.RsPostfixTemplateExpressionCondition
import org.rust.lang.RsLanguage
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
        DerefPostfixTemplate(this),
        LetPostfixTemplate(this),
        IterPostfixTemplate("iter", this),
        IterPostfixTemplate("for", this),
        PrintlnPostfixTemplate(this),
        DbgPostfixTemplate(this),
        DbgrPostfixTemplate(this),
        OkPostfixTemplate(this),
        SomePostfixTemplate(this),
        ErrPostfixTemplate(this),
        WrapTypePathPostfixTemplate(this)
    )

    override fun getTemplates(): Set<PostfixTemplate> = templates

    override fun isTerminalSymbol(currentChar: Char): Boolean =
        currentChar == '.' || currentChar == '!'

    override fun afterExpand(file: PsiFile, editor: Editor) {
    }

    override fun preCheck(copyFile: PsiFile, realEditor: Editor, currentOffset: Int) = copyFile

    override fun preExpand(file: PsiFile, editor: Editor) {
    }

    override fun getPresentableName(): String = RsLanguage.displayName

    override fun createEditor(templateToEdit: PostfixTemplate?): PostfixTemplateEditor {
        // forbid editing (overwriting) of built-in templates; default editor allows only renaming
        if (templateToEdit !is RsEditablePostfixTemplate && templateToEdit != null)
            return DefaultPostfixTemplateEditor(this, templateToEdit)

        val editor = RsPostfixTemplateEditor(this)
        editor.setTemplate(templateToEdit)
        return editor
    }

    override fun readExternalTemplate(id: String, name: String, template: Element): PostfixTemplate? {
        val liveTemplate = template.getChild(TemplateSettings.TEMPLATE)?.let {
            TemplateSettings.readTemplateFromElement("", it, this.javaClass.classLoader)
        } ?: return null

        val conditions = PostfixTemplatesUtils.readExternalConditions(template) { param ->
            param?.let { RsPostfixTemplateExpressionCondition.readExternal(it) }
        }.filterNotNull().toSet()

        val useTopmostExpression = template.getAttributeValue(PostfixTemplatesUtils.TOPMOST_ATTR).toBoolean()

        return RsEditablePostfixTemplate(id, name, liveTemplate.string, "", conditions, useTopmostExpression, this)
    }

    override fun writeExternalTemplate(template: PostfixTemplate, parentElement: Element) {
        if (template is RsEditablePostfixTemplate) {
            PostfixTemplatesUtils.writeExternalTemplate(template, parentElement)
        }
    }
}

class LetPostfixTemplate(provider: RsPostfixTemplateProvider) :
    PostfixTemplateWithExpressionSelector(null, "let", "let name = expr;", RsExprParentsSelector(), provider) {
    override fun expandForChooseExpression(expression: PsiElement, editor: Editor) {
        if (expression !is RsExpr) return
        extractExpression(editor, expression, postfixLet = true)
    }
}
