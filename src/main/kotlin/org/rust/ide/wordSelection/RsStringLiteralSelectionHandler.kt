/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.wordSelection

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase
import com.intellij.codeInsight.editorActions.SelectWordUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.lang.core.lexer.RsEscapesLexer
import org.rust.lang.core.psi.RS_ALL_STRING_LITERALS
import org.rust.lang.core.psi.RsLiteralKind
import org.rust.lang.core.psi.ext.elementType

class RsStringLiteralSelectionHandler : ExtendWordSelectionHandlerBase() {
    override fun canSelect(e: PsiElement): Boolean =
        e.elementType in RS_ALL_STRING_LITERALS

    override fun select(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): List<TextRange>? {
        val kind = RsLiteralKind.fromAstNode(e.node) as? RsLiteralKind.String ?: return null
        val valueRange = kind.offsets.value?.shiftRight(kind.node.startOffset) ?: return null
        val result = super.select(e, editorText, cursorOffset, editor) ?: mutableListOf()

        val elementType = e.elementType
        if (elementType in RsEscapesLexer.ESCAPABLE_LITERALS_TOKEN_SET) {
            SelectWordUtil.addWordHonoringEscapeSequences(
                editorText,
                valueRange,
                cursorOffset,
                RsEscapesLexer.of(elementType),
                result
            )
        }

        result += valueRange
        return result
    }
}
