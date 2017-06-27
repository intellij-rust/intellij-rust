/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.typing.assist

import com.intellij.lang.SmartEnterProcessorWithFixers
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsCallExpr

/**
 * Fixer that closes missing function call parenthesis.
 */
class MethodCallFixer : SmartEnterProcessorWithFixers.Fixer<RsSmartEnterProcessor>() {
    override fun apply(editor: Editor, processor: RsSmartEnterProcessor, element: PsiElement) {
        if (element is RsCallExpr) {
            val argList = element.valueArgumentList
            if (argList.lastChild != null && argList.lastChild.text != ")") {
                editor.document.insertString(element.getTextRange().endOffset, ")")
            }
        }
    }
}
