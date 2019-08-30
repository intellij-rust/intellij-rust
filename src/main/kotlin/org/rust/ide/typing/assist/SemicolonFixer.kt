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
import org.rust.lang.core.psi.ext.endOffset

/**
 * Fixer that adds missing semicolons at the end of statements.
 */
class SemicolonFixer : SmartEnterProcessorWithFixers.Fixer<RsSmartEnterProcessor>() {
    override fun apply(editor: Editor, processor: RsSmartEnterProcessor, element: PsiElement) {
        if (element.parent !is RsBlock || (element as? RsStmt)?.semicolon != null) return
        editor.document.insertString(element.endOffset, ";")
    }
}
