/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.codeInsight.editorActions.JoinLinesHandlerDelegate
import com.intellij.codeInsight.editorActions.JoinLinesHandlerDelegate.CANNOT_JOIN
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.text.CharSequenceSubSequence
import org.rust.ide.formatter.impl.CommaList
import org.rust.ide.typing.endsWithUnescapedBackslash
import org.rust.lang.core.parser.RustParserDefinition.Companion.INNER_EOL_DOC_COMMENT
import org.rust.lang.core.parser.RustParserDefinition.Companion.OUTER_EOL_DOC_COMMENT
import org.rust.lang.core.psi.RS_STRING_LITERALS
import org.rust.lang.core.psi.RsElementTypes.COMMA
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.elementType

class RsJoinLinesHandler : JoinLinesHandlerDelegate {
    /**
     * Fixup lines **after** they have been join.
     * See [RsJoinRawLinesHandler]
     */
    override fun tryJoinLines(document: Document, file: PsiFile, offsetNear: Int, end: Int): Int {
        if (file !is RsFile) return CANNOT_JOIN

        val leftPsi = file.findElementAt(offsetNear) ?: return CANNOT_JOIN
        val rightPsi = file.findElementAt(end) ?: return CANNOT_JOIN

        val tryJoinCommaList = joinCommaList(document, leftPsi, rightPsi)
        if (tryJoinCommaList != CANNOT_JOIN) return tryJoinCommaList

        if (leftPsi != rightPsi) return CANNOT_JOIN
        val elementType = leftPsi.elementType

        return when (elementType) {
            in RS_STRING_LITERALS ->
                joinStringLiteral(document, offsetNear, end)

            INNER_EOL_DOC_COMMENT, OUTER_EOL_DOC_COMMENT ->
                joinLineDocComment(document, offsetNear, end)

            else -> CANNOT_JOIN
        }
    }


    private fun joinCommaList(document: Document, leftPsi: PsiElement, rightPsi: PsiElement): Int {
        if (leftPsi.elementType != COMMA) return CANNOT_JOIN
        val parentType = leftPsi.parent?.elementType ?: return CANNOT_JOIN
        val rightType = rightPsi.elementType
        val list = CommaList.forElement(parentType) ?: return CANNOT_JOIN
        if (rightType == list.closingBrace) {
            val replaceWith = if (list.needsSpaceBeforeClosingBrace) " " else ""
            document.replaceString(leftPsi.textOffset, rightPsi.textOffset, replaceWith)
            return leftPsi.textOffset
        }
        return CANNOT_JOIN
    }

    // Normally this is handled by `CodeDocumentationAwareCommenter`, but Rust have different styles
    // of documentation comments, so we handle this manually.
    private fun joinLineDocComment(document: Document, offsetNear: Int, end: Int): Int {
        val prefix = document.charsSequence.subSequence(end, end + 3).toString()
        if (prefix != "///" && prefix != "//!") return CANNOT_JOIN
        document.deleteString(offsetNear + 1, end + prefix.length)
        return offsetNear + 1
    }

    private fun joinStringLiteral(document: Document, offsetNear: Int, end: Int): Int {
        val text = document.charsSequence

        var start = offsetNear

        // Strip newline escape
        if (CharSequenceSubSequence(text, 0, start + 1).endsWithUnescapedBackslash()) {
            start--
            while (start >= 0 && (text[start] == ' ' || text[start] == '\t')) {
                start--
            }
        }

        document.deleteString(start + 1, end)
        document.insertString(start + 1, " ")

        return start + 1
    }
}
