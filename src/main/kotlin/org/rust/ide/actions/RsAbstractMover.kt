/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.codeInsight.editorActions.moveUpDown.LineRange
import com.intellij.codeInsight.editorActions.moveUpDown.StatementUpDownMover
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.ide.utils.findElementAtIgnoreWhitespaceAfter
import org.rust.ide.utils.findElementAtIgnoreWhitespaceBefore
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.parentOfType

val PsiElement.line: Int? get() = containingFile.viewProvider.document?.getLineNumber(textRange.startOffset)

abstract class RsAbstractMover : StatementUpDownMover() {

    abstract val listOfContainers: List<Class<out PsiElement>>
    abstract val jumpOver: List<Class<out PsiElement>>

    abstract fun collectedElement(element: PsiElement): Pair<PsiElement, List<Int>>?

    override fun checkAvailable(editor: Editor, file: PsiFile, info: MoveInfo, down: Boolean): Boolean {
        if (file !is RsFile) {
            return false
        }

        val selectionRange = getLineRangeFromSelection(editor)
        val start = file.findElementAtIgnoreWhitespaceBefore(
            editor.document.getLineStartOffset(selectionRange.startLine)
        ) ?: return false
        val (collectedElement, possibleStartlines) = collectedElement(start) ?: return false
        if (!possibleStartlines.contains(selectionRange.startLine)) {
            return false
        }
        val range = LineRange(collectedElement)

        info.toMove = range
        val line = if (!down) {
            info.toMove.startLine - 1
        } else {
            info.toMove.endLine + 1
        }
        if (line < 0 || line >= editor.document.lineCount) {
            return info.prohibitMove()
        }
        val offset = editor.document.getLineStartOffset(line)
        var element = if (!down) {
            file.findElementAtIgnoreWhitespaceBefore(offset) ?: return false
        } else {
            file.findElementAtIgnoreWhitespaceAfter(offset) ?: return false
        }
        while (element != null && element !is RsFile) {
            if (listOfContainers.any { it.isAssignableFrom(element.javaClass) }) {
                var parentOfFn = collectedElement.parent
                while (parentOfFn !is RsFile) {
                    if (element == parentOfFn ) {
                        return info.prohibitMove()
                    }
                    parentOfFn = parentOfFn.parent
                }
            }

            if (jumpOver.any { it.isAssignableFrom(element.javaClass) }) {
                break
            }
            val context = element.context ?: break
            if (context is RsFile) {
                break
            }
            element = context
        }
        val beforeRange = LineRange(element)
        info.toMove2 = beforeRange
        if (beforeRange.startLine == range.startLine) {
            if (!down) {
                info.toMove2 = LineRange(line, line + 1)
            } else {
                info.toMove2 = LineRange(line - 1, line)
            }
        }
        return true
    }

}
