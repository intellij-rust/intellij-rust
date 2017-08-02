/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.typing

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.RsDotExpr
import org.rust.lang.core.psi.RsFile


class RsDotTypedHandler : TypedHandlerDelegate() {
    override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (file !is RsFile) return Result.CONTINUE
        if (c != '.') return Result.CONTINUE

        val offset = editor.caretModel.offset
        PsiDocumentManager.getInstance(project).commitDocument(editor.document)
        if (indentDotIfNeeded(project, file, offset)) return Result.STOP

        return Result.CONTINUE
    }

    private fun indentDotIfNeeded(project: Project, file: RsFile, offset: Int): Boolean {
        val currElement = file.findElementAt(offset - 1) ?: return false
        val prevLeaf = PsiTreeUtil.prevLeaf(currElement)
        if (!(prevLeaf is PsiWhiteSpace && prevLeaf.text.contains("\n"))) return false
        if (currElement.parent !is RsDotExpr) return false
        val curElementLength = currElement.text.length
        if (offset < curElementLength) return false
        CodeStyleManager.getInstance(project).adjustLineIndent(file, offset - curElementLength)
        return true
    }
}
