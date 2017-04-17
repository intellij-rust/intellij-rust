package org.rust.ide.typing

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.RsElementTypes
import org.rust.lang.core.psi.RsFile

class RsTypingHandler : TypedHandlerDelegate() {

    private var rsLTTyped = false

    override fun beforeCharTyped(c: Char, project: Project, editor: Editor, file: PsiFile, fileType: FileType): Result {
        if (file !is RsFile) {
            return Result.CONTINUE
        }
        if (c == '<' && CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) {
            rsLTTyped = isAfterColonColonToken(editor)
        }

        return Result.CONTINUE
    }

    override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (file !is RsFile) {
            return Result.CONTINUE
        }
        if (rsLTTyped) {
            rsLTTyped = false
            val offset = editor.caretModel.offset
            editor.document.insertString(offset, ">")
            return Result.STOP

        }
        return Result.CONTINUE
    }

    private fun isAfterColonColonToken(editor: Editor): Boolean {
        val offset = editor.caretModel.offset
        val iterator = (editor as EditorEx).highlighter.createIterator(offset)
        if (iterator.atEnd()) {
            return false
        }
        if (iterator.start > 0) {
            iterator.retreat()
        }
        return iterator.tokenType == RsElementTypes.COLONCOLON
    }
}
