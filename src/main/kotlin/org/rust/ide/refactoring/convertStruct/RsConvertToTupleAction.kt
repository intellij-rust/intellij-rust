/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.convertStruct

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.ui.RefactoringDialog
import com.intellij.ui.components.JBCheckBox
import org.rust.ide.refactoring.RsBaseEditorRefactoringAction
import org.rust.lang.core.psi.ext.RsFieldsOwner
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.openapiext.isHeadlessEnvironment
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

class RsConvertToTupleAction : RsBaseEditorRefactoringAction() {
    override fun isAvailableOnElementInEditorAndFile(element: PsiElement, editor: Editor, file: PsiFile, context: DataContext): Boolean {
        val owner = element.ancestorOrSelf<RsFieldsOwner>() ?: return false
        return owner.blockFields != null
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset)?.ancestorOrSelf<RsFieldsOwner>() ?: return

        if (isHeadlessEnvironment) {
            val processor = RsConvertToTupleProcessor(project, element, true)
            processor.setPreviewUsages(false)
            processor.run()
        } else {
            Dialog(project, element).show()
        }
    }

    private class Dialog(project: Project, val element: RsFieldsOwner) : RefactoringDialog(project, false) {
        val cb = JBCheckBox("Convert all usages", true)

        init {
            super.init()
            title = "Convert to tuple"
        }

        override fun doAction() {
            invokeRefactoring(RsConvertToTupleProcessor(project, element, cb.isSelected))
        }

        override fun createCenterPanel(): JComponent? {
            val panel = JPanel(BorderLayout(2, 2))
            panel.preferredSize = Dimension(300, 100)
            panel.add(cb)
            return panel
        }
    }
}
