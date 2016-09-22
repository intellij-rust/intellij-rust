package org.rust.ide.typing

import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

class RustRawLiteralHashesDeleter : BackspaceHandlerDelegate() {
    override fun beforeCharDeleted(c: Char, file: PsiFile, editor: Editor) {
        TODO()
    }

    override fun charDeleted(c: Char, file: PsiFile, editor: Editor): Boolean {
        TODO()
    }
}
