/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.typing

import com.intellij.codeInsight.highlighting.PairedBraceMatcherAdapter
import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.TokenType.WHITE_SPACE
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import org.rust.lang.RsFileType
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.RS_COMMENTS
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.tokenSetOf
import java.util.*

class RsBraceMatcher : PairedBraceMatcherAdapter(RsBaseBraceMatcher(), RsLanguage) {

    override fun isLBraceToken(iterator: HighlighterIterator, fileText: CharSequence, fileType: FileType): Boolean =
        isBrace(iterator, fileText, fileType, true)

    override fun isRBraceToken(iterator: HighlighterIterator, fileText: CharSequence, fileType: FileType): Boolean =
        isBrace(iterator, fileText, fileType, false)

    private fun isBrace(iterator: HighlighterIterator, fileText: CharSequence, fileType: FileType, left: Boolean): Boolean {
        if (fileType != RsFileType) return false
        val pair = findPair(left, iterator, fileText, fileType) ?: return false
        val brace = pair.leftBraceType
        // Non angle bracket handled by `RsBaseBraceMatcher`
        if (!(brace == LT || brace == GT)) return true

        // Here is the tricky part. Unlike `{}`, `()` and `[]`,
        // `<>` does not always form a brace pair because of
        // comparison operators. Another complication is that
        // we don't have access to tree here, and can use only
        // the lexical structure. Luckily, chained comparisons
        // are forbidden in Rust.
        //
        // So let's run a standard stack-based brace matching
        // algorithm with a twist that we try to bail out early
        // if we see some token which is unlikely to be seen
        // between a pair of `<>`.
        var count = 0
        try {
            val braceStack: Deque<IElementType> = ArrayDeque<IElementType>()
            braceStack.addLast(brace)
            var prevIsAnd = false
            while (true) {
                count++
                if (left) iterator.advance() else iterator.retreat()
                if (iterator.atEnd()) return false
                val current = mirrorIfReverse(iterator.tokenType, !left)
                if (current in UNPAIRED_TYPE_TOKENS) {
                    // `&` `&` is glued to `&&` only in the parser,
                    // need to handle them specially
                    if (prevIsAnd && current == AND) return false
                    prevIsAnd = current == AND
                    continue
                }
                val co = coBrace(current) ?: return false

                if (current in OPEN_BRACES) {
                    braceStack.addLast(current)
                } else {
                    if (braceStack.pollLast() != co) return false
                    if (braceStack.isEmpty()) return true
                }
            }
        } finally {
            while (count-- > 0) {
                if (left) iterator.retreat() else iterator.advance()
            }
        }
    }

    companion object {
        private fun mirrorIfReverse(b: IElementType, reverse: Boolean): IElementType {
            if (!reverse) return b
            return coBrace(b) ?: b
        }

        private fun coBrace(b: IElementType): IElementType? = when (b) {
            LT -> GT
            GT -> LT

            LPAREN -> RPAREN
            RPAREN -> LPAREN

            LBRACE -> RBRACE
            RBRACE -> LBRACE

            LBRACK -> RBRACK
            RBRACK -> LBRACK

            else -> null
        }

        private val OPEN_BRACES = tokenSetOf(LT, LPAREN, LBRACE, LBRACK)

        val UNPAIRED_TYPE_TOKENS = TokenSet.orSet(
            RS_COMMENTS,
            tokenSetOf(
                WHITE_SPACE,
                IDENTIFIER, UNDERSCORE, SELF, SUPER,
                COMMA, SEMICOLON,
                QUOTE_IDENTIFIER,
                PLUS,
                COLON, EQ,
                COLONCOLON,
                INTEGER_LITERAL,
                AND, MUT, CONST, MUL
            )
        )
    }
}

private class RsBaseBraceMatcher : PairedBraceMatcher {

    override fun getPairs() = PAIRS

    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, next: IElementType?): Boolean =
        next in InsertPairBraceBefore

    override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int = openingBraceOffset

    companion object {
        private val PAIRS: Array<BracePair> = arrayOf(
            BracePair(LBRACE, RBRACE, true /* structural */),
            BracePair(LPAREN, RPAREN, false),
            BracePair(LBRACK, RBRACK, false),
            BracePair(LT, GT, false)
        )

        private val InsertPairBraceBefore = TokenSet.orSet(
            RS_COMMENTS,
            TokenSet.create(
                TokenType.WHITE_SPACE,
                SEMICOLON,
                COMMA,
                RPAREN,
                RBRACE,
                RBRACE, LBRACE
            )
        )
    }
}
