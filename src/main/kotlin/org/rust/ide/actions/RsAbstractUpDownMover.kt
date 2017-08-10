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
import com.intellij.psi.tree.IElementType
import org.rust.ide.utils.findElementAtIgnoreWhitespaceAfter
import org.rust.ide.utils.findElementAtIgnoreWhitespaceBefore
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.elementType

val PsiElement.line: Int? get() = containingFile.viewProvider.document?.getLineNumber(textRange.startOffset)

abstract class RsAbstractUpDownMover : StatementUpDownMover() {

    abstract val containers: List<IElementType>
    abstract val jumpOver: List<IElementType>
    abstract fun collectedElement(element: PsiElement): Pair<PsiElement, List<Int>>?

    override fun checkAvailable(editor: Editor, file: PsiFile, info: MoveInfo, down: Boolean): Boolean {
        if (file !is RsFile) {
            return false
        }

        val selectionRange = getLineRangeFromSelection(editor)
        val start = file.findElementAtIgnoreWhitespaceBefore(
            editor.document.getLineStartOffset(selectionRange.startLine)
        ) ?: return false
        val (collectedElement, possibleStartLines) = collectedElement(start) ?: return false
        if (!possibleStartLines.contains(selectionRange.startLine)) {
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

        var element: PsiElement? = if (!down) {
            file.findElementAtIgnoreWhitespaceBefore(offset)
        } else {
            file.findElementAtIgnoreWhitespaceAfter(offset)
        } ?: return info.prohibitMove()

        while (element != null && element !is RsFile) {
            if (containers.any { element?.elementType == it }) {
                var parentOfFn = collectedElement.parent
                while (parentOfFn !is RsFile) {
                    if (element == parentOfFn) {
                        return info.prohibitMove()
                    }
                    parentOfFn = parentOfFn.parent
                }
            }

            if (jumpOver.any { element?.elementType == it }) {
                break
            }
            val parent = element.parent
            if (parent is RsFile) {
                break
            }
            element = parent
        }

        if (element != null) {
            info.toMove2 = LineRange(element)
        }
        if (down) {
            if (info.toMove2.startLine - 1 == range.endLine || element == collectedElement) {
                info.toMove2 = LineRange(line - 1, line)
            }
        } else {
            if (info.toMove2.startLine == range.startLine) {
                info.toMove2 = LineRange(line, line + 1)
            }
        }
        return true
    }
}
