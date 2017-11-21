/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring.implementMembers

import com.intellij.lang.LanguageCodeInsightActionHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.openapiext.Testmark

class ImplementMembersHandler : LanguageCodeInsightActionHandler {
    override fun isValidFor(editor: Editor, file: PsiFile): Boolean {
        if (file !is RsFile) return false

        val elementAtCaret = file.findElementAt(editor.caretModel.offset)
        val classOrObject = elementAtCaret?.ancestorOrSelf<RsImplItem>()
        return if (classOrObject == null) {
            ImplementMembersMarks.noImplInHandler.hit()
            false
        } else {
            true
        }
    }

    override fun startInWriteAction() = false

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val elementAtCaret = file.findElementAt(editor.caretModel.offset)
        val implItem = elementAtCaret?.ancestorOrSelf<RsImplItem>()
            ?: error("No impl trait item")
        generateTraitMembers(implItem, editor)
    }

}

object ImplementMembersMarks {
    val noImplInHandler = Testmark("noImplInHandler")
}

