package org.rust.lang.typing

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.TokenSet
import com.intellij.util.text.CharArrayUtil
import org.rust.lang.core.lexer.RustTokenElementTypes.INNER_DOC_COMMENT
import org.rust.lang.core.lexer.RustTokenElementTypes.OUTER_DOC_COMMENT
import org.rust.lang.core.lexer.RustTokenElementTypes.EOL_COMMENT
import org.rust.lang.core.psi.impl.RustFileImpl

class RustEnterInLineCommentHandler : EnterHandlerDelegateAdapter() {

    override fun preprocessEnter(file: PsiFile, editor: Editor, caretOffset: Ref<Int>,
                                 caretAdvance: Ref<Int>, dataContext: DataContext,
                                 originalHandler: EditorActionHandler?): Result {

        // return if this is not a Rust file
        if (file !is RustFileImpl) {
            return Result.Continue
        }

        val document = editor.document
        val text = document.charsSequence

        // skip following spaces and tabs
        val caret = CharArrayUtil.shiftForward(text, caretOffset.get(), " \t")

        // figure out if the caret is at the end of the line
        val isEOL = caret < text.length && text[caret] == '\n'

        // find the PsiElement at the caret
        var elementAtCaret = file.findElementAt(caret) ?: return Result.Continue
        if (isEOL) {
            // ... or the previous one if this is end-of-line whitespace
            elementAtCaret = elementAtCaret.prevSibling
        }

        // check if the element at the caret is a line comment
        val type = elementAtCaret.node?.elementType
        if (type !in LINE_COMMENT_TYPES) {
            return Result.Continue
        }

        // return if caret is at end of line for a non-documentation comment
        if (isEOL && type == EOL_COMMENT) {
            return Result.Continue
        }

        // extract the comment token (//, /// or //!) from the comment text
        val commentText = elementAtCaret.textRange.subSequence(text)
        val commentTokenLength = CharArrayUtil.shiftForward(commentText, 0, "/!")
        val commentToken = commentText.subSequence(0, commentTokenLength).toString()

        // prefix the next line with an identical comment token
        document.insertString(caret, commentToken + " ")
        caretAdvance.set(commentToken.length + 1)

        return Result.DefaultForceIndent
    }

    companion object {
        val LINE_COMMENT_TYPES = TokenSet.create(EOL_COMMENT, INNER_DOC_COMMENT, OUTER_DOC_COMMENT)
    }
}
