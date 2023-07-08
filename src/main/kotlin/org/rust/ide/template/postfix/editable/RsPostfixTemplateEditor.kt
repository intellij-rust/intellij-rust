/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix.editable

import com.intellij.codeInsight.template.impl.TemplateEditorUtil
import com.intellij.codeInsight.template.postfix.settings.PostfixTemplateEditorBase
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.Messages
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.UIUtil
import org.rust.RsBundle
import org.rust.ide.template.postfix.RsPostfixTemplateProvider
import javax.swing.JComponent
import javax.swing.JPanel

open class RsPostfixTemplateEditor(provider: RsPostfixTemplateProvider) :
    PostfixTemplateEditorBase<RsPostfixTemplateExpressionCondition>(provider, createEditor(), true) {

    var myPanel: JPanel = FormBuilder.createFormBuilder()
        .addComponentFillVertically(myEditTemplateAndConditionsPanel, UIUtil.DEFAULT_VGAP)
        .panel

    override fun createTemplate(templateId: String, templateName: String): PostfixTemplate {
        val types = myExpressionTypesListModel.elements().asSequence().toSet()
        val templateText = myTemplateEditor.document.text
        val useTopmostExpression = myApplyToTheTopmostJBCheckBox.isSelected

        return RsEditablePostfixTemplate(templateId, templateName, templateText, "", types, useTopmostExpression, myProvider)
    }

    override fun getComponent(): JComponent = myPanel

    override fun fillConditions(group: DefaultActionGroup) {
        for (type in RsPostfixTemplateExpressionCondition.Type.values().filter { it != RsPostfixTemplateExpressionCondition.Type.UserEntered })
            group.add(AddConditionAction(RsPostfixTemplateExpressionCondition(type)))
        group.add(EnterCustomTypeNameAction())
    }

    private inner class EnterCustomTypeNameAction : DumbAwareAction(RsBundle.message("action.enter.type.name.text")) {
        override fun actionPerformed(e: AnActionEvent) {
            val typeName = Messages.showInputDialog(myPanel, RsBundle.message("dialog.message.enter.custom.type.name.type.parameters.are.not.supported"), RsBundle.message("dialog.title.enter.type.name"), null)
            if (typeName != null) {
                val userEnteredType = RsPostfixTemplateExpressionCondition(RsPostfixTemplateExpressionCondition.Type.UserEntered, typeName)
                myExpressionTypesListModel.addElement(userEnteredType)
            }
        }
    }

    companion object {
        fun createEditor(): Editor {
            val project = ProjectManager.getInstance().defaultProject
            val document = EditorFactory.getInstance().createDocument("")
            return TemplateEditorUtil.createEditor(false, document, project)!!
        }
    }
}
