/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.typing.assist

import com.intellij.lang.SmartEnterProcessorWithFixers
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.LBRACE
import org.rust.lang.core.psi.RsElementTypes.RBRACE
import org.rust.lang.core.psi.ext.ancestors

/**
 * Smart enter implementation for the Rust language.
 */
class RsSmartEnterProcessor : SmartEnterProcessorWithFixers() {

    init {
        addFixers(
            MethodCallFixer(),
            SemicolonFixer(),
            CommaFixer(),
            FunctionOrStructFixer()
        )

        addEnterProcessors(
            AfterSemicolonEnterProcessor(),
            AfterFunctionOrStructEnterProcessor(),
            PlainEnterProcessor()
        )
    }

    override fun getStatementAtCaret(editor: Editor, psiFile: PsiFile): PsiElement? {
        val atCaret = super.getStatementAtCaret(editor, psiFile) ?: return null
        if (atCaret is PsiWhiteSpace) return null
        loop@ for (each in atCaret.ancestors) {
            val elementType = each.node.elementType
            when {
                elementType == LBRACE || elementType == RBRACE -> continue@loop
                each is RsMatchArm || each.parent is RsBlock
                    || each.parent is RsFunction || each.parent is RsStructItem -> return each
            }
        }
        return null
    }

    override fun doNotStepInto(element: PsiElement): Boolean {
        return true
    }

    override fun processDefaultEnter(project: Project, editor: Editor, file: PsiFile) {
        plainEnter(editor)
    }

    private class PlainEnterProcessor : FixEnterProcessor() {
        override fun doEnter(atCaret: PsiElement, file: PsiFile, editor: Editor, modified: Boolean): Boolean {
            plainEnter(editor)
            return true
        }
    }
}
