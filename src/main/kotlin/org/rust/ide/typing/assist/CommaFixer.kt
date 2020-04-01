/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.typing.assist

import com.intellij.lang.SmartEnterProcessorWithFixers
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsBlockExpr
import org.rust.lang.core.psi.RsMatchArm

/**
 * Fixer that adds missing comma at the end of statements.
 */
class CommaFixer : SmartEnterProcessorWithFixers.Fixer<RsSmartEnterProcessor>() {
    override fun apply(editor: Editor, processor: RsSmartEnterProcessor, element: PsiElement) {
        if (element is RsMatchArm && element.expr !is RsBlockExpr && element.comma == null) {
            editor.document.insertString(element.textRange.endOffset, ",")
        }
    }
}
