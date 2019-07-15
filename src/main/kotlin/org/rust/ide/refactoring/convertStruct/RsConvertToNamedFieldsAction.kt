/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.convertStruct

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.ui.RefactoringDialog
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.GridBag
import org.rust.ide.refactoring.RsBaseEditorRefactoringAction
import org.rust.ide.refactoring.isValidRustVariableIdentifier
import org.rust.lang.core.psi.ext.RsFieldsOwner
import org.rust.lang.core.psi.ext.RsNameIdentifierOwner
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.openapiext.isHeadlessEnvironment
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JPanel

class RsConvertToNamedFieldsAction : RsBaseEditorRefactoringAction() {

    override fun isAvailableOnElementInEditorAndFile(element: PsiElement, editor: Editor, file: PsiFile, context: DataContext): Boolean {
        val owner = element.ancestorOrSelf<RsFieldsOwner>() ?: return false
        return owner.tupleFields != null
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset)?.ancestorOrSelf<RsFieldsOwner>() ?: return

        if (isHeadlessEnvironment) {
            val processor = RsConvertToNamedFieldsProcessor(project, element, true)
            processor.setPreviewUsages(false)
            processor.run()
        } else {
            Dialog(project, element).show()
        }
    }

    private class Dialog(project: Project, val element: RsFieldsOwner) : RefactoringDialog(project, false) {
        val cb = JBCheckBox("Convert all usages", true)
        val editors = (0..element.tupleFields!!.tupleFieldDeclList.size).map {
            EditorTextField("_$it").apply {
                addDocumentListener(object : DocumentListener {
                    override fun documentChanged(event: DocumentEvent) =
                        updateErrorInfo(doValidateAll())
                })
                selectAll()
            }
        }

        init {
            super.init()
            title = "Convert to named fields settings"
        }

        override fun doValidateAll(): List<ValidationInfo> {
            refactorAction.isEnabled = true
            return editors
                .filter { !isValidRustVariableIdentifier(it.text) }
                .map {
                    refactorAction.isEnabled = false
                    ValidationInfo("invalid identifier", it)
                }
        }

        override fun doAction() =
            invokeRefactoring(RsConvertToNamedFieldsProcessor(project, element, cb.isSelected, editors.map { it.text }))

        override fun createCenterPanel(): JComponent? {
            val panel = JPanel(BorderLayout(2, 2))
            panel.preferredSize = Dimension(400, 200)

            val gridPanel = JPanel(GridBagLayout())
            val gridBuilder = GridBag()
                .setDefaultWeightX(1.0)
                .setDefaultFill(GridBagConstraints.HORIZONTAL)
                .setDefaultInsets(0, 0, 2, 2)
            gridPanel.add(
                JBLabel("struct ${(element as RsNameIdentifierOwner).name!!}{"),
                gridBuilder.nextLine().next()
            )
            val input = element.tupleFields!!.tupleFieldDeclList
                .map {
                    JBLabel(": " + it.text)
                }
                .zip(editors)
                .fold(gridPanel) { p, (a, b) ->
                    p.apply {
                        add(b, gridBuilder.nextLine().next())
                        add(a, gridBuilder.next())
                    }
                }
            input.border = createContentPaneBorder()
            input.add(JBLabel("}"), gridBuilder.weighty(1.0).nextLine())

            panel.add(input, BorderLayout.NORTH)
            panel.add(cb, BorderLayout.SOUTH)
            return panel
        }

    }
}
