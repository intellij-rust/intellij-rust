/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.wordSelection

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.LineTokenizer
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.rust.lang.core.psi.RsElementTypes
import org.rust.lang.core.psi.RsMembers
import org.rust.lang.core.psi.RsStmt
import org.rust.lang.core.psi.ext.endOffset
import org.rust.lang.core.psi.ext.startOffset
import java.util.*

class RsGroupSelectionHandler : ExtendWordSelectionHandlerBase() {
    override fun canSelect(e: PsiElement): Boolean =
        e is RsStmt || RsFieldLikeSelectionHandler.isFieldLikeDecl(e) || e.parent is RsMembers || e is PsiComment


    override fun select(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): List<TextRange>? {
        // see com.intellij.codeInsight.editorActions.wordSelection.StatementGroupSelectioner for the reference implementation

        val result = ArrayList<TextRange>()

        var startElement = e
        var endElement = e

        while (startElement.prevSibling != null) {
            val sibling = startElement.prevSibling

            if (sibling.node.elementType == RsElementTypes.LBRACE) break

            if (sibling is PsiWhiteSpace) {
                val strings = LineTokenizer.tokenize(sibling.text.toCharArray(), false)
                if (strings.size > 2) {
                    break
                }
            }
            startElement = sibling
        }

        while (startElement is PsiWhiteSpace) startElement = startElement.nextSibling

        while (endElement.nextSibling != null) {
            val sibling = endElement.nextSibling

            if (sibling.node.elementType == RsElementTypes.RBRACE) break

            if (sibling is PsiWhiteSpace) {
                val strings = LineTokenizer.tokenize(sibling.text.toCharArray(), false)
                if (strings.size > 2) {
                    break
                }
            }
            endElement = sibling
        }

        while (endElement is PsiWhiteSpace) endElement = endElement.prevSibling

        result.addAll(ExtendWordSelectionHandlerBase.expandToWholeLine(
            editorText, TextRange(startElement.startOffset, endElement.endOffset)))

        return result
    }
}
