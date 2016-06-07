package org.rust.ide.typing.assist

import com.intellij.lang.SmartEnterProcessorWithFixers
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*

/**
 * Fixer that adds missing semicolons at the end of statements.
 */
class SemicolonFixer : SmartEnterProcessorWithFixers.Fixer<RustSmartEnterProcessor>() {
    override fun apply(editor: Editor, processor: RustSmartEnterProcessor, element: PsiElement) {
        fixStatement(editor, element)
        fixLastExprInBlock(editor, element)
    }

    private fun fixLastExprInBlock(editor: Editor, element: PsiElement) {
        val parent = element.parent
        if (parent is RustBlockElement && element == parent.expr) {
            editor.document.insertString(element.textRange.endOffset, ";")
        }
    }

    private fun fixStatement(editor: Editor, element: PsiElement) {
        if (element is RustStmtElement && element.semicolon == null) {
            editor.document.insertString(element.textRange.endOffset, ";")
        }
    }
}
