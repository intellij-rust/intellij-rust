package org.rust.ide.typing.assist

import com.intellij.lang.SmartEnterProcessorWithFixers
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustCallExprElement

/**
 * Fixer that closes missing function call parenthesis.
 */
class MethodCallFixer : SmartEnterProcessorWithFixers.Fixer<RustSmartEnterProcessor>() {
    override fun apply(editor: Editor, processor: RustSmartEnterProcessor, element: PsiElement) {
        if (element is RustCallExprElement) {
            val argList = element.argList
            if (argList.lastChild != null && argList.lastChild.text != ")") {
                editor.document.insertString(element.getTextRange().endOffset, ")")
            }
        }
    }
}
