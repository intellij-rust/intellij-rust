/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring

import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.actions.BaseRefactoringAction
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.RsEnumVariant
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.RsTupleFields
import org.rust.lang.core.psi.ext.ancestorOrSelf

class RsConvertToNamedFieldsAction : BaseRefactoringAction() {
    override fun isAvailableInEditorOnly(): Boolean = true

    override fun isEnabledOnElements(elements: Array<out PsiElement>): Boolean = false

    override fun isAvailableOnElementInEditorAndFile(element: PsiElement, editor: Editor, file: PsiFile, context: DataContext): Boolean {
        return findTupleStructBody(element) != null
    }

    override fun getHandler(dataContext: DataContext): RefactoringActionHandler = Handler


    override fun isAvailableForLanguage(language: Language): Boolean = language.`is`(RsLanguage)


    private object Handler : RefactoringActionHandler {
        override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
            val offset = editor.caretModel.offset
            val element = file.findElementAt(offset) ?: return
            invoke(project, arrayOf(element), dataContext)
        }

        override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
            WriteCommandAction.runWriteCommandAction(project) {
                doRefactoring(findTupleStructBody(elements.single())!!)
            }
        }
    }
}

private fun findTupleStructBody(element: PsiElement): RsTupleFields? {
    val parent = element.ancestorOrSelf<RsTupleFields>()
    if (parent != null) return parent

    val struct = element.ancestorOrSelf<RsStructItem>()
    if (struct != null) return struct.tupleFields

    val enumVariant = element.ancestorOrSelf<RsEnumVariant>()
    if (enumVariant != null) return enumVariant.tupleFields

    return null
}

private fun doRefactoring(tupleFields: RsTupleFields) {
    val fields = tupleFields.tupleFieldDeclList.mapIndexed { idx, field ->
        RsPsiFactory.BlockField(field.isPublic, "_$idx", field.typeReference)
    }
    val blockFields = RsPsiFactory(tupleFields.project).createBlockFields(fields)
    val parent = tupleFields.parent
    if (parent is RsStructItem) {
        parent.semicolon?.delete()
    }
    tupleFields.replace(blockFields)
}
