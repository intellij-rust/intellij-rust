package org.rust.lang.highlight

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import org.rust.lang.core.lexer.RustTokenElementTypes.BYTE_STRING_LITERAL
import org.rust.lang.core.lexer.RustTokenElementTypes.STRING_LITERAL

// Do not autopair `'` in char literals because of lifeteimes, which use a single `'`: `'a`
class RustQuoteHandler : SimpleTokenSetQuoteHandler(STRING_LITERAL, BYTE_STRING_LITERAL) {
    override fun hasNonClosedLiteral(editor: Editor?, iterator: HighlighterIterator?, offset: Int): Boolean = true
}
