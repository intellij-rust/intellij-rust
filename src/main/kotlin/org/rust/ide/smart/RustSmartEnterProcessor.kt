package org.rust.ide.smart

import com.intellij.lang.SmartEnterProcessorWithFixers
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

class RustSmartEnterProcessor : SmartEnterProcessorWithFixers() {
    init {
        addFixers(StatementFixer)
        addEnterProcessors(PlainEnterProcessor)
    }

    private object StatementFixer : Fixer<RustSmartEnterProcessor>() {
        override fun apply(editor: Editor, processor: RustSmartEnterProcessor, element: PsiElement) {
            val doc = editor.document
            val offset = element.textOffset
            val lineNum = doc.getLineNumber(offset)
            val lineStart = doc.getLineStartOffset(lineNum)
            val lineEnd = doc.getLineEndOffset(lineNum)
            val range = lineStart..lineEnd
            val openParenthesisCount = range.count { doc.getText(TextRange(it, it + 1)) == "(" }
            val closeParenthesisCount = range.count { doc.getText(TextRange(it, it + 1)) == ")" }
            val num = openParenthesisCount - closeParenthesisCount
            if (num > 0) {
                doc.insertString(lineEnd, "${(1..num).fold("", { s, i -> "$s)" })};")
                processor.commit(editor)
            }
        }
    }

    private object PlainEnterProcessor : FixEnterProcessor() {
        override fun doEnter(element: PsiElement?, file: PsiFile?, editor: Editor, modified: Boolean): Boolean {
            plainEnter(editor)
            return true
        }
    }
}
