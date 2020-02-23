/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.introduceField

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.AbstractFileViewProvider.findElementAt
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.psi.ext.rightSiblings
import org.rust.openapiext.runWriteCommandAction

class RsIntroduceFieldHandler: RefactoringActionHandler {
    override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext?) {
        if (file !is RsFile) return
        if (editor == null) return

        val offset = editor.caretModel.offset
        val struct = findElementAt(file, offset)?.ancestorOrSelf<RsStructItem>()
        if (struct == null || (struct.tupleFields == null && struct.blockFields == null)) {
            val message = RefactoringBundle.message("refactoring.introduce.selection.error")
            val title = RefactoringBundle.message("introduce.field.title")
            val helpId = "refactoring.introduceField"
            CommonRefactoringUtil.showErrorHint(project, editor, message, title, helpId)
        }
        else {
            showIntroduceFieldChooser(struct) { fields ->
                fields?.let {
                    introduceFields(struct, it)
                }
            }
        }
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        //this doesn't get called form the editor.
    }
}

private fun introduceFields(struct: RsStructItem, info: ParameterInfo) {
    if (info.fields.isEmpty()) return

    val project = struct.project
    val factory = RsPsiFactory(project)

    val block = struct.blockFields
    val tuples = struct.tupleFields
    when {
        block != null -> introduceBlockFields(factory, info, block)
        tuples != null -> introduceTupleFields(factory, info, tuples)
    }
}

private fun introduceBlockFields(factory: RsPsiFactory, info: ParameterInfo, fields: RsBlockFields) {
    val newFields = factory.createBlockFields(info.fields.map {
        RsPsiFactory.BlockField(it.pub, it.name, it.type)
    })
    val blockFields = fields.namedFieldDeclList
    var anchor = blockFields.lastOrNull() ?: fields.lbrace

    val addNewline = if (blockFields.isEmpty()) {
        true
    }
    else blockFields.firstOrNull()?.typeReference?.rightSiblings?.lineBreak() != null
    var addComma = blockFields.isNotEmpty()

    fields.project.runWriteCommandAction {
        for (field in newFields.namedFieldDeclList) {
            if (addComma) {
                val comma = factory.createComma()
                anchor = fields.addAfter(comma, anchor)
            }
            else addComma = true
            anchor = fields.addAfter(field, anchor)
            if (addNewline) {
                val newline = factory.createNewline()
                anchor = fields.addAfter(newline, anchor)
            }
        }
    }
}
private fun introduceTupleFields(factory: RsPsiFactory, info: ParameterInfo, fields: RsTupleFields) {
    val newFields = factory.createTupleFields(info.fields.map {
        RsPsiFactory.BlockField(it.pub, it.name, it.type)
    })
    var anchor: PsiElement = fields.tupleFieldDeclList.lastOrNull() ?: fields.lparen
    var addComma = fields.tupleFieldDeclList.isNotEmpty()

    fields.project.runWriteCommandAction {
        for (field in newFields.tupleFieldDeclList) {
            if (addComma) {
                val comma = factory.createComma()
                anchor = fields.addAfter(comma, anchor)
            }
            else addComma = true

            anchor = fields.addAfter(field, anchor)
        }
    }
}

// TODO: support tuple fields
// TODO: import types
// TODO: add lifetimes/type parameters to struct
// TODO: change call sites, add default value to new fields

private fun Sequence<PsiElement>.lineBreak(): PsiWhiteSpace? =
    dropWhile { it !is PsiWhiteSpace && it !is PsiComment }
        .takeWhile { it is PsiWhiteSpace || it is PsiComment }
        .firstOrNull { it is PsiWhiteSpace && it.textContains('\n') } as? PsiWhiteSpace
