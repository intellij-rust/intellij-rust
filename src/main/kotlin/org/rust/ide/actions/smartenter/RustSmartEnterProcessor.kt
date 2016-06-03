package org.rust.ide.actions.smartenter

import com.intellij.lang.SmartEnterProcessorWithFixers
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.RustBlockElement
import org.rust.lang.core.psi.RustStmtElement

/**
 * Smart enter implementation for the Rust language.
 */
class RustSmartEnterProcessor : SmartEnterProcessorWithFixers() {

    init {
        addFixers(
            MethodCallFixer(),
            SemicolonFixer())

        addEnterProcessors(
            PlainEnterProcessor())
    }

    override fun getStatementAtCaret(editor: Editor?, psiFile: PsiFile?): PsiElement? {
        val atCaret = super.getStatementAtCaret(editor, psiFile)
        if (atCaret is PsiWhiteSpace) return null

        return PsiTreeUtil.getParentOfType(atCaret, RustStmtElement::class.java, RustBlockElement::class.java)
    }

    private inner class PlainEnterProcessor : SmartEnterProcessorWithFixers.FixEnterProcessor() {
        override fun doEnter(atCaret: PsiElement, file: PsiFile, editor: Editor, modified: Boolean): Boolean {
            plainEnter(editor)
            return true
        }
    }
}
