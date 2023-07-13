/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.typing

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.util.text.CharArrayUtil
import org.rust.lang.core.psi.RsDotExpr
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsPat


class RsTypedHandler : TypedHandlerDelegate() {
    override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (file !is RsFile) return Result.CONTINUE
        if (c != '.') return Result.CONTINUE

        val offset = editor.caretModel.offset
        PsiDocumentManager.getInstance(project).commitDocument(editor.document)
        if (indentDotIfNeeded(project, file, offset)) return Result.STOP

        return Result.CONTINUE
    }

    private fun indentDotIfNeeded(project: Project, file: RsFile, offset: Int): Boolean {
        val currElement = file.findElementAt(offset - 1) ?: return false
        val prevLeaf = PsiTreeUtil.prevLeaf(currElement)
        if (!(prevLeaf is PsiWhiteSpace && prevLeaf.text.contains("\n"))) return false
        if (currElement.parent !is RsDotExpr) return false
        val curElementLength = currElement.text.length
        if (offset < curElementLength) return false
        CodeStyleManager.getInstance(project).adjustLineIndent(file, offset - curElementLength)
        return true
    }

    override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (file !is RsFile) return Result.CONTINUE

        val offset = editor.caretModel.offset

        // `:` is typed right after `:`
        if (charTyped == ':' && StringUtil.endsWith(editor.document.immutableCharSequence, 0, offset, ":")) {
            AutoPopupController.getInstance(project).scheduleAutoPopup(editor, CompletionType.BASIC) { f ->
                val leaf = f.findElementAt(offset - 1)
                leaf.elementType == COLONCOLON
            }
            return Result.STOP
        }

        // `|` is typed after `(` or `,` or `=` (ignoring whitespace) - perform lambda expr completion
        if (charTyped == '|') {
            val i = CharArrayUtil.shiftBackward(editor.document.immutableCharSequence, 0, offset - 1, " \n") + 1
            val shouldShowPopup = StringUtil.endsWith(editor.document.immutableCharSequence, 0, i, "(")
                || StringUtil.endsWith(editor.document.immutableCharSequence, 0, i, ",")
                || StringUtil.endsWith(editor.document.immutableCharSequence, 0, i, "=")
            if (shouldShowPopup) {
                AutoPopupController.getInstance(project).scheduleAutoPopup(editor, CompletionType.BASIC) { f ->
                    val leaf = f.findElementAt(offset)
                    leaf?.parent !is RsPat
                }
                return Result.STOP
            }
        }

        // struct literal `Foo {/*caret*/}`
        if (charTyped == '{') {
            AutoPopupController.getInstance(project).autoPopupParameterInfo(editor, null)
            return Result.STOP
        }

        if (charTyped == 'i' || charTyped == 'u' || charTyped == 'f') {
            AutoPopupController.getInstance(project).scheduleAutoPopup(editor, CompletionType.BASIC) { f ->
                val leaf = f.findElementAt(offset)
                leaf?.elementType == INTEGER_LITERAL || leaf?.elementType == FLOAT_LITERAL
            }
        }

        return Result.CONTINUE
    }
}
