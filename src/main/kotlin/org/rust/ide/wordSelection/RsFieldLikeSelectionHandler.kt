/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.wordSelection

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.core.psi.ext.endOffset
import org.rust.lang.core.psi.ext.rightSiblings
import org.rust.lang.core.psi.ext.startOffset

class RsFieldLikeSelectionHandler : ExtendWordSelectionHandlerBase() {
    override fun canSelect(e: PsiElement): Boolean = isFieldLikeDecl(e)

    override fun select(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): List<TextRange>? {
        val start = e.startOffset
        var end = e.endOffset

        // expand the end to include the adjacent comma after the field:
        e.rightSiblings
            .firstOrNull { it !is PsiComment && it !is PsiWhiteSpace }
            ?.takeIf { it.elementType == RsElementTypes.COMMA }
            ?.let { end = it.endOffset }

        return ExtendWordSelectionHandlerBase.expandToWholeLine(editorText, TextRange.create(start, end))
    }

    companion object {
        fun isFieldLikeDecl(e: PsiElement) =
            e is RsNamedFieldDecl || e is RsStructLiteralField || e is RsEnumVariant || e is RsMatchArm
    }
}
