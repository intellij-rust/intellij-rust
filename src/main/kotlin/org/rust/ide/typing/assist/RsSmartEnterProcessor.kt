/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.typing.assist

import com.intellij.lang.SmartEnterProcessorWithFixers
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import org.rust.lang.core.psi.RsBlock
import org.rust.lang.core.psi.RsElementTypes

/**
 * Smart enter implementation for the Rust language.
 */
class RsSmartEnterProcessor : SmartEnterProcessorWithFixers() {

    init {
        addFixers(
            MethodCallFixer(),
            SemicolonFixer())

        addEnterProcessors(
            PlainEnterProcessor())
    }

    override fun getStatementAtCaret(editor: Editor?, psiFile: PsiFile?): PsiElement? {
        var atCaret = super.getStatementAtCaret(editor, psiFile)
        if (atCaret is PsiWhiteSpace) return null
        while (atCaret != null) {
            val parent = atCaret.parent
            val elementType = atCaret.node.elementType
            atCaret = when {
                elementType == RsElementTypes.LBRACE || elementType == RsElementTypes.RBRACE -> parent
                parent is RsBlock -> return atCaret
                else -> parent
            }
        }
        return null
    }

    override fun doNotStepInto(element: PsiElement?): Boolean {
        return true
    }

    private inner class PlainEnterProcessor : FixEnterProcessor() {
        override fun doEnter(atCaret: PsiElement, file: PsiFile, editor: Editor, modified: Boolean): Boolean {
            plainEnter(editor)
            return true
        }
    }
}
