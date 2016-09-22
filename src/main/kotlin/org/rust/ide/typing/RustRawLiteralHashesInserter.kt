package org.rust.ide.typing

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.impl.RustFile
import org.rust.lang.core.psi.impl.RustRawStringLiteralImpl

class RustRawLiteralHashesInserter : TypedHandlerDelegate() {
    override fun beforeCharTyped(c: Char, project: Project, editor: Editor, file: PsiFile, fileType: FileType): Result {
        if (file !is RustFile) return Result.CONTINUE

        // We are only interested in handling typing '#'
        if (c != '#') return Result.CONTINUE

        val caretModel = editor.caretModel
        val caretOffset = caretModel.offset

        if (!isValidOffset(caretOffset, editor.document.charsSequence)) return Result.CONTINUE

        // Get token type current cursor is, we are using caretOffset - 1 in order to properly
        // handle this case: r#""#<caret>, cases r<caret>#""# and <caret>r#""# will still work.
        val highlighter = (editor as EditorEx).highlighter
        val iterator = highlighter.createIterator(caretOffset - 1)

        // We are only interested in valid raw literals
        val literal = getLiteralDumb(iterator) as? RustRawStringLiteralImpl ?: return Result.CONTINUE
        val openDelim = literal.offsets.openDelim ?: return Result.CONTINUE
        val closeDelim = literal.offsets.closeDelim ?: return Result.CONTINUE

        // Get rid of ugly quotes from out precious ranges (remember - we are operating on left-closed ranges)
        val openHashes = openDelim
        val closeHashes = closeDelim.shiftRight(1)

        // No detect on which side of the literal we are, and insert hash on the other one
        when (caretOffset - iterator.start) {
            in openHashes -> editor.document.insertString(iterator.end, "#")
            in closeHashes -> editor.document.insertString(iterator.start + 1, "#")
        }

        return Result.CONTINUE
    }
}
