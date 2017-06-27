/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.typing

import com.intellij.codeInsight.daemon.impl.CollectHighlightsUtil
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.RS_RAW_LITERALS
import org.rust.lang.core.psi.RsFile

class RsRawLiteralHashesInserter : TypedHandlerDelegate() {
    override fun beforeCharTyped(c: Char, project: Project, editor: Editor, file: PsiFile, fileType: FileType): Result {
        if (file !is RsFile) return Result.CONTINUE

        // We are only interested in handling typing '#'
        if (c != '#') return Result.CONTINUE

        val caretOffset = editor.caretModel.offset
        if (!isValidOffset(caretOffset - 1, editor.document.charsSequence)) return Result.CONTINUE

        // Get token type current cursor is, we are using caretOffset - 1 in order to properly
        // handle this case: r#""#<caret>, cases r<caret>#""# and <caret>r#""# will still work.
        val highlighter = (editor as EditorEx).highlighter
        val iterator = highlighter.createIterator(caretOffset - 1)
        val (openHashes, closeHashes) = getHashesOffsets(iterator) ?: return Result.CONTINUE

        val hasErrorAfterLiteral = run {
            val start = closeHashes.endOffset
            var end = editor.document.charsSequence.indexOf('\n', start)
            if (end == -1) {
                end = editor.document.charsSequence.length
            }
            CollectHighlightsUtil.getElementsInRange(file, start, end).any { it is PsiErrorElement }
        }
        if (hasErrorAfterLiteral) return Result.CONTINUE

        // Now detect on which side of the literal we are, and insert hash on the other one.
        // We are growing ranges in order to catch situations where caret is places directly
        // after last hash character.
        when (caretOffset) {
            in openHashes.grown(1) -> editor.document.insertString(closeHashes.startOffset, "#")
            in closeHashes.grown(1) -> editor.document.insertString(openHashes.endOffset, "#")
        }

        return Result.CONTINUE
    }
}

class RsRawLiteralHashesDeleter : RsEnableableBackspaceHandlerDelegate() {
    private var offsets: Pair<TextRange, TextRange>? = null

    override fun deleting(c: Char, file: PsiFile, editor: Editor): Boolean {
        val caretOffset = editor.caretModel.offset
        if (!isValidOffset(caretOffset, editor.document.charsSequence)) return false

        val highlighter = (editor as EditorEx).highlighter
        val iterator = highlighter.createIterator(caretOffset - 1)

        // [getHashesOffsets] is O(n) (n is literal length), so do not evaluate it when it's not needed.
        if (c != '#' || iterator.tokenType !in RS_RAW_LITERALS) return false

        // We have to compute offsets here, because we still have our '#' in document.
        offsets = getHashesOffsets(iterator)
        return offsets != null
    }

    override fun deleted(c: Char, file: PsiFile, editor: Editor): Boolean {
        // We want caret offset before deletion!
        val caretOffset = editor.caretModel.offset + 1
        val (openHashes, closeHashes) = checkNotNull(offsets)

        // Now detect on which side of the literal we are, and remove hash on the other one.
        // Remember that offsets apply to literal before deletion!
        // We are growing ranges in order to catch situations where caret is places directly
        // after last hash character.
        when (caretOffset) {
            in openHashes.grown(1) ->
                // -1 because left-closed ranges
                // -1 because open hash was deleted so offsets are now shifted right by 1
                editor.document.deleteChar(closeHashes.endOffset - 2)
            in closeHashes.grown(1) ->
                editor.document.deleteChar(openHashes.startOffset)
        }

        return false
    }
}

private fun getHashesOffsets(iterator: HighlighterIterator): Pair<TextRange, TextRange>? {
    // We are only interested in valid raw literals
    val literal = getLiteralDumb(iterator) ?: return null
    if (literal.node.elementType !in RS_RAW_LITERALS) return null
    val openDelim = literal.offsets.openDelim ?: return null
    val closeDelim = literal.offsets.closeDelim ?: return null

    // Get rid of ugly quotes from our precious ranges (remember - we are operating on left-closed ranges),
    // and normalize them to real literal offsets.
    return openDelim.shiftRight(iterator.start).grown(-1) to closeDelim.shiftRight(iterator.start + 1).grown(-1)
}
