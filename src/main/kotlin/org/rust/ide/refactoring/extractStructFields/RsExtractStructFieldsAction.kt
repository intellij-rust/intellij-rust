/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.extractStructFields

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.RsBundle
import org.rust.ide.refactoring.RsBaseEditorRefactoringAction
import org.rust.ide.refactoring.generate.StructMember
import org.rust.ide.refactoring.generate.showStructMemberChooserDialog
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.psi.ext.isTupleStruct
import org.rust.lang.core.types.emptySubstitution

data class RsExtractStructFieldsContext(
    val struct: RsStructItem,
    val fields: List<StructMember>,
    val name: String
)

class RsExtractStructFieldsAction : RsBaseEditorRefactoringAction() {

    override fun isAvailableOnElementInEditorAndFile(
        element: PsiElement,
        editor: Editor,
        file: PsiFile,
        context: DataContext
    ): Boolean = findApplicableContext(editor, file) != null

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
        val struct = findApplicableContext(editor, file) ?: return
        val fields = StructMember.fromStruct(struct, emptySubstitution)

        val chosenFields = showStructMemberChooserDialog(
            project,
            struct,
            fields,
            RsBundle.message("action.Rust.RsExtractStructFields.choose.fields.title")
        ) ?: return
        if (chosenFields.isEmpty()) return

        val name = showExtractStructFieldsDialog(project) ?: return
        val ctx = RsExtractStructFieldsContext(struct, chosenFields, name)
        val processor = RsExtractStructFieldsProcessor(project, editor, ctx)
        processor.setPreviewUsages(false)
        processor.run()
    }

    companion object {
        private fun findApplicableContext(editor: Editor, file: PsiFile): RsStructItem? {
            val offset = editor.caretModel.offset
            val struct = file.findElementAt(offset)?.ancestorOrSelf<RsStructItem>() ?: return null
            if (struct.isTupleStruct) return null
            if (struct.blockFields?.namedFieldDeclList?.isEmpty() != false) return null
            return struct
        }
    }
}
