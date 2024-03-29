/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.typing.assist

import com.intellij.lang.SmartEnterProcessorWithFixers
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.RsExprStmt
import org.rust.lang.core.psi.RsLetDecl
import org.rust.lang.core.psi.ext.endOffset

class AfterSemicolonEnterProcessor : SmartEnterProcessorWithFixers.FixEnterProcessor() {
    override fun doEnter(atCaret: PsiElement, file: PsiFile, editor: Editor, modified: Boolean): Boolean {
        if (!modified) return false

        val isSuitableElement = RsSmartEnterProcessor.isSuitableElement(atCaret)
            || atCaret is RsExprStmt || atCaret is RsLetDecl
        if (!isSuitableElement) return false

        val elementEndOffset = atCaret.endOffset
        editor.caretModel.moveToOffset(elementEndOffset)
        return true
    }
}
