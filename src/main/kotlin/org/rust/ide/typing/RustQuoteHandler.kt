package org.rust.ide.typing

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import org.rust.lang.core.psi.RustTokenElementTypes.BYTE_STRING_LITERAL
import org.rust.lang.core.psi.RustTokenElementTypes.STRING_LITERAL

// Do not autopair `'` in char literals because of lifetimes, which use a single `'`: `'a`
class RustQuoteHandler : SimpleTokenSetQuoteHandler(STRING_LITERAL, BYTE_STRING_LITERAL) {
    override fun hasNonClosedLiteral(editor: Editor?, iterator: HighlighterIterator?, offset: Int): Boolean = true
}
