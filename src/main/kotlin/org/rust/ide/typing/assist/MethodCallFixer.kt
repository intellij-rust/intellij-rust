package org.rust.ide.typing.assist

import com.intellij.lang.SmartEnterProcessorWithFixers
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsCallExpr

/**
 * Fixer that closes missing function call parenthesis.
 */
class MethodCallFixer : SmartEnterProcessorWithFixers.Fixer<RustSmartEnterProcessor>() {
    override fun apply(editor: Editor, processor: RustSmartEnterProcessor, element: PsiElement) {
        if (element is RsCallExpr) {
            val argList = element.valueArgumentList
            if (argList.lastChild != null && argList.lastChild.text != ")") {
                editor.document.insertString(element.getTextRange().endOffset, ")")
            }
        }
    }
}
