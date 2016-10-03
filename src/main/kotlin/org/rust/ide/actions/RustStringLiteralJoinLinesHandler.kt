package org.rust.ide.actions

import com.intellij.codeInsight.editorActions.JoinLinesHandlerDelegate
import com.intellij.codeInsight.editorActions.JoinLinesHandlerDelegate.CANNOT_JOIN
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiFile
import com.intellij.util.text.CharSequenceSubSequence
import org.rust.ide.typing.endsWithUnescapedBackslash
import org.rust.lang.core.psi.RustTokenElementTypes
import org.rust.lang.core.psi.impl.RustFile
import org.rust.lang.core.psi.util.elementType

class RustStringLiteralJoinLinesHandler : JoinLinesHandlerDelegate {
    override fun tryJoinLines(document: Document, file: PsiFile, offsetNear: Int, end: Int): Int {
        if (file !is RustFile) return CANNOT_JOIN

        val text = document.charsSequence
        val leftPsi = file.findElementAt(offsetNear) ?: return CANNOT_JOIN
        val rightPsi = file.findElementAt(end) ?: return CANNOT_JOIN

        if (leftPsi != rightPsi || leftPsi.elementType !in RustTokenElementTypes.STRING_LITERALS) return CANNOT_JOIN

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
