package org.rust.lang.core.lexer

import com.intellij.lexer.FlexAdapter
import com.intellij.psi.TokenType.WHITE_SPACE
import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.RustDocElementTypes.DOC_CODE_SPAN
import org.rust.lang.core.psi.RustDocElementTypes.DOC_TEXT

open class RustDocLexer() : RustMergingLexerAdapterBase(FlexAdapter(_RustDocLexer())) {
    override fun mergingAdvance(type: IElementType): IElementType {
        if (type == DOC_TEXT) {
            while (delegate.tokenType == DOC_TEXT
                || (delegate.tokenType == WHITE_SPACE && !delegate.tokenSequence.toString().isEOL())) {
                delegate.advance()
            }
        } else if (type == WHITE_SPACE || type == DOC_CODE_SPAN) {
            while (delegate.tokenType == type) {
                delegate.advance()
            }
        } else {
            delegate.advance()
        }
        return type
    }
}
