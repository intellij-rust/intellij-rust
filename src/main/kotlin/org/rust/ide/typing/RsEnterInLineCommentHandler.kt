/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.typing

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType.WHITE_SPACE
import com.intellij.util.text.CharArrayUtil
import org.rust.lang.core.parser.RustParserDefinition.Companion.EOL_COMMENT
import org.rust.lang.core.parser.RustParserDefinition.Companion.INNER_EOL_DOC_COMMENT
import org.rust.lang.core.parser.RustParserDefinition.Companion.OUTER_EOL_DOC_COMMENT
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.doc.psi.RsDocKind

class RsEnterInLineCommentHandler : EnterHandlerDelegateAdapter() {
    override fun preprocessEnter(
        file: PsiFile,
        editor: Editor,
        caretOffsetRef: Ref<Int>,
        caretAdvanceRef: Ref<Int>,
        dataContext: DataContext,
        originalHandler: EditorActionHandler?
    ): Result {
        // return if this is not a Rust file
        if (file !is RsFile) {
            return Result.Continue
        }

        // get current document and commit any changes, so we'll get latest PSI
        val document = editor.document
        PsiDocumentManager.getInstance(file.project).commitDocument(document)

        val caretOffset = caretOffsetRef.get()
        val text = document.charsSequence

        // skip following spaces and tabs
        val offset = CharArrayUtil.shiftForward(text, caretOffset, " \t")

        // figure out if the caret is at the end of the line
        val isEOL = offset < text.length && text[offset] == '\n'

        // find the PsiElement at the caret
        var elementAtCaret = file.findElementAt(offset) ?: return Result.Continue
        if (isEOL && elementAtCaret.isEolWhitespace(offset)) {
            // ... or the previous one if this is end-of-line whitespace
            elementAtCaret = elementAtCaret.prevSibling ?: return Result.Continue
        }

        // check if the element at the caret is a line comment
        // and extract the comment token (//, /// or //!) from the comment text
        val prefix = when (elementAtCaret.elementType) {
            OUTER_EOL_DOC_COMMENT -> RsDocKind.OuterEol.prefix
            INNER_EOL_DOC_COMMENT -> RsDocKind.InnerEol.prefix
            EOL_COMMENT -> {
                // return if caret is at end of line for a non-documentation comment
                if (isEOL) {
                    return Result.Continue
                }

                "//"
            }
            else -> return Result.Continue
        }

        // If caret is currently inside some prefix, do nothing.
        if (offset < elementAtCaret.textOffset + prefix.length) {
            return Result.Continue
        }

        if (text.startsWith(prefix, offset)) {
            // If caret is currently at the beginning of some sequence which
            // starts the same as our prefix, we are at one of these situations:
            // a)  // comment
            //     <caret>// comment
            // b) // comment <caret>//comment
            // Here, we don't want to insert any prefixes, as there is already one
            // in code. We only have to insert space after prefix if it's missing
            // and update caret position.
            val afterPrefix = offset + prefix.length
            if (afterPrefix < document.textLength && text[afterPrefix] != ' ') {
                document.insertString(afterPrefix, " ")
            }
            caretOffsetRef.set(offset)
        } else {
            // Otherwise; add one space, if caret isn't at one
            // currently, and insert prefix just before it.
            val prefixToAdd = if (text[caretOffset] != ' ') prefix + ' ' else prefix
            document.insertString(caretOffset, prefixToAdd)
            caretAdvanceRef.set(prefixToAdd.length)
        }

        return Result.Default
    }

    // Returns true for
    //   ```
    //   fooo  <caret>
    //
    //
    //   ```
    //
    // Returns false for
    //   ```
    //   fooo
    //
    //   <caret>
    //   ```
    private fun PsiElement.isEolWhitespace(caretOffset: Int): Boolean {
        if (node?.elementType != WHITE_SPACE) return false
        val pos = node.text.indexOf('\n')
        return pos == -1 || caretOffset <= pos + textRange.startOffset
    }
}
