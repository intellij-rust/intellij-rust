package org.rust.ide.actions.smartenter

import com.intellij.lang.SmartEnterProcessorWithFixers
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.rust.lang.core.psi.RustCallExpr
import org.rust.lang.core.psi.RustLetDecl
import org.rust.lang.core.psi.util.parentOfType

/**
 * Fixer that adds missing semicolons at the end of statements.
 */
class SemicolonFixer : SmartEnterProcessorWithFixers.Fixer<RustSmartEnterProcessor>() {
    override fun apply(editor: Editor, processor: RustSmartEnterProcessor, element: PsiElement) {
        fixMethodCall(editor, element)
        fixDeclaration(editor, element)
    }

    private fun fixMethodCall(editor: Editor, element: PsiElement) {
        if (isOutermostCallExpr(element) && !endsWithSemicolon(element)) {
            editor.document.insertString(element.textRange.endOffset, ";")
        }
    }

    private fun fixDeclaration(editor: Editor, element: PsiElement) {
        if (element is RustLetDecl && element.semicolon == null) {
            editor.document.insertString(element.textRange.endOffset, ";")
        }
    }

    private fun isOutermostCallExpr(element: PsiElement): Boolean {
        return element is RustCallExpr && element.parentOfType<RustCallExpr>() == null
    }

    private fun endsWithSemicolon(element: PsiElement): Boolean {
        if (element.nextSibling != null) {
            if (element.nextSibling is PsiWhiteSpace) {
                return endsWithSemicolon(element.nextSibling)
            }
            return element.nextSibling.text == ";"
        }
        return false
    }
}
