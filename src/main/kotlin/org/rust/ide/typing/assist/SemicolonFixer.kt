/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.typing.assist

import com.intellij.lang.SmartEnterProcessorWithFixers
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsBlock
import org.rust.lang.core.psi.RsStmt

/**
 * Fixer that adds missing semicolons at the end of statements.
 */
class SemicolonFixer : SmartEnterProcessorWithFixers.Fixer<RsSmartEnterProcessor>() {
    override fun apply(editor: Editor, processor: RsSmartEnterProcessor, element: PsiElement) {
        fixStatement(editor, element)
        fixLastExprInBlock(editor, element)
    }

    private fun fixLastExprInBlock(editor: Editor, element: PsiElement) {
        val parent = element.parent
        if (parent is RsBlock && element == parent.expr) {
            editor.document.insertString(element.textRange.endOffset, ";")
        }
    }

    private fun fixStatement(editor: Editor, element: PsiElement) {
        if (element is RsStmt && element.semicolon == null) {
            editor.document.insertString(element.textRange.endOffset, ";")
        }
    }
}
