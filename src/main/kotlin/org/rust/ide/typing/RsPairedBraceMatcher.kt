package org.rust.ide.typing

import com.intellij.codeInsight.highlighting.PairedBraceMatcherAdapter
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.tree.TokenSet
import org.rust.lang.RsFileType
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.RsTokenElementTypes
import org.rust.lang.core.psi.RsTokenElementTypes.*

class RsPairedBraceMatcher : PairedBraceMatcherAdapter(RsBraceMatcher(), RsLanguage) {

    private object Holder {
        val TYPE_TOKENS = TokenSet.orSet(COMMENTS_OR_WHITE_SPACE_TOKEN_SET,
            TokenSet.create(
                RsTokenElementTypes.IDENTIFIER,
                RsTokenElementTypes.COMMA,
                RsTokenElementTypes.LIFETIME,
                RsTokenElementTypes.RBRACK, RsTokenElementTypes.LBRACK,
                RsTokenElementTypes.PLUS,
                RsTokenElementTypes.COLON))
    }

    override fun isLBraceToken(iterator: HighlighterIterator, fileText: CharSequence?, fileType: FileType?): Boolean {
        return isBrace(iterator, fileText, fileType, true)
    }

    override fun isRBraceToken(iterator: HighlighterIterator, fileText: CharSequence?, fileType: FileType?): Boolean {
        return isBrace(iterator, fileText, fileType, false)
    }

    private fun isBrace(iterator: HighlighterIterator, fileText: CharSequence?, fileType: FileType?, left: Boolean): Boolean {
        val pair = findPair(left, iterator, fileText, fileType) ?: return false

        if (fileType !== RsFileType) return false

        val opposite = if (left) GT else LT
        if ((if (left) pair.rightBraceType else pair.leftBraceType) !== opposite) return true

        val braceElementType = if (left) LT else GT
        var count = 0
        try {
            var paired = 1
            while (true) {
                count++
                if (left) {
                    iterator.advance()
                } else {
                    iterator.retreat()
                }
                if (iterator.atEnd()) break
                val tokenType = iterator.tokenType
                if (tokenType === opposite) {
                    paired--
                    if (paired == 0) return true
                    continue
                }

                if (tokenType === braceElementType) {
                    paired++
                    continue
                }

                if (!Holder.TYPE_TOKENS.contains(tokenType)) {
                    return false
                }
            }
            return false
        } finally {
            while (count-- > 0) {
                if (left) {
                    iterator.retreat()
                } else {
                    iterator.advance()
                }
            }
        }
    }
}
