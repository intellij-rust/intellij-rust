/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.tt

import com.intellij.lang.PsiBuilder
import com.intellij.psi.TokenType.WHITE_SPACE
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import org.rust.lang.core.macros.tt.TokenTree.Leaf
import org.rust.lang.core.macros.tt.TokenTree.Subtree
import org.rust.lang.core.parser.RustParserUtil
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.*

fun PsiBuilder.parseSubtree(textOffset: Int = 0, idOffset: Int = 0): MappedSubtree {
    return TokenTreeParser(this, textOffset, idOffset).parse()
}

private class TokenTreeParser(
    private val lexer: PsiBuilder,
    private val textOffset: Int,
    private val idOffset: Int,
) {
    private val tokenMap = mutableListOf<TokenMetadata>()

    fun parse(): MappedSubtree {
        val result = mutableListOf<TokenTree>()

        while (true) {
            val tokenType = lexer.tokenType ?: break
            val offset = lexer.currentOffset

            parse(offset, tokenType, result)
        }

        if (result.size == 1 && result.single() is Subtree) {
            return MappedSubtree((result.single() as Subtree), TokenMap(tokenMap))
        }

        return MappedSubtree(Subtree(null, result), TokenMap(tokenMap))
    }

    private fun parse(offset: Int, tokenType: IElementType, result: MutableList<TokenTree>) {
        val delimKind = MacroBraces.fromOpenToken(tokenType)
        if (delimKind != null) {
            parseSubtree(offset, delimKind, result)
        } else {
            parseLeaf(offset, tokenType, result)
        }
    }

    private fun parseSubtree(offset: Int, delimKind: MacroBraces, result: MutableList<TokenTree>) {
        val delimLeaf = Delimiter(allocDelimId(offset, nextWhitespaceOrCommentText()), delimKind)
        val subtreeResult = mutableListOf<TokenTree>()

        lexer.advanceLexer()

        while (true) {
            val tokenType = lexer.tokenType

            if (tokenType == null) {
                result += Leaf.Punct(delimKind.openText, Spacing.Alone, allocId(offset, ""))
                result += subtreeResult
                return
            }

            if (tokenType == delimKind.closeToken) break

            parse(lexer.currentOffset, tokenType, subtreeResult)
        }

        closeDelim(delimLeaf.id, lexer.currentOffset, nextWhitespaceOrCommentText())

        result += Subtree(delimLeaf, subtreeResult)
        lexer.advanceLexer()
    }

    private fun parseLeaf(offset: Int, tokenType: IElementType, result: MutableList<TokenTree>) {
        var shouldAdvanceLexer = true
        val tokenText = lexer.tokenText!!
        when (tokenType) {
            INTEGER_LITERAL -> {
                val lastMarker = lexer.latestDoneMarker
                val tokenText2 = if (RustParserUtil.parseFloatLiteral(lexer, 0)) {
                    shouldAdvanceLexer = false
                    val floatMarker = lexer.latestDoneMarker
                    if (floatMarker != null && floatMarker != lastMarker) {
                        lexer.originalText.substring(floatMarker.startOffset, floatMarker.endOffset)
                    } else {
                        tokenText
                    }
                } else {
                    tokenText
                }
                result += Leaf.Literal(tokenText2, allocId(offset, nextWhitespaceOrCommentText(shouldAdvanceLexer)))
            }
            in RS_LITERALS -> result += Leaf.Literal(tokenText, allocId(offset, nextWhitespaceOrCommentText()))
            in PROC_MACRO_IDENTIFIER_TOKENS -> result += Leaf.Ident(tokenText, allocId(offset, nextWhitespaceOrCommentText()))
            QUOTE_IDENTIFIER -> {
                result += Leaf.Punct(tokenText[0].toString(), Spacing.Joint, allocId(offset, ""))
                result += Leaf.Ident(tokenText.substring(1), allocId(offset + 1, nextWhitespaceOrCommentText()))
            }
            else -> {
                for (i in tokenText.indices) {
                    val isLastChar = i == tokenText.lastIndex
                    val char = tokenText[i].toString()
                    val (spacing, rightTrivia) = if (!isLastChar) {
                        Spacing.Joint to ""
                    } else {
                        when (lexer.rawLookup(1)) {
                            null -> Spacing.Alone to "" // The last token is always alone
                            in NEXT_TOKEN_ALONE_SET -> Spacing.Alone to nextWhitespaceOrCommentText()
                            else -> Spacing.Joint to ""
                        }
                    }
                    result += Leaf.Punct(char, spacing, allocId(offset + i, rightTrivia))
                }
            }
        }
        if (shouldAdvanceLexer) {
            lexer.advanceLexer()
        }
    }

    private fun allocId(startOffset: Int, rightTrivia: CharSequence): Int {
        val id = idOffset + tokenMap.size
        tokenMap += TokenMetadata.Token(textOffset + startOffset, rightTrivia)
        return id
    }

    private fun allocDelimId(openOffset: Int, rightTrivia: CharSequence): Int {
        val id = idOffset + tokenMap.size
        tokenMap += TokenMetadata.Delimiter(TokenMetadata.Token(textOffset + openOffset, rightTrivia), null)
        return id
    }

    private fun closeDelim(tokenId: Int, closeOffset: Int, rightTrivia: CharSequence) {
        tokenMap[tokenId - idOffset] = (tokenMap[tokenId - idOffset] as TokenMetadata.Delimiter)
            .copy(close = TokenMetadata.Token(textOffset + closeOffset, rightTrivia))
    }

    private fun nextWhitespaceOrCommentText(startFromNextToken: Boolean = true): CharSequence {
        val start = if (startFromNextToken) 1 else 0
        var counter = start
        while (lexer.rawLookup(counter) in WHITESPACE_OR_COMMENTS) {
            counter++
        }
        if (counter == start) return ""
        val startOffset = lexer.rawTokenTypeStart(start)
        val endOffset = lexer.rawTokenTypeStart(counter)
        return lexer.originalText.subSequence(startOffset, endOffset)
    }
}

private val PROC_MACRO_IDENTIFIER_TOKENS = TokenSet.orSet(
    RS_IDENTIFIER_TOKENS,
    tokenSetOf(UNDERSCORE)
)

private val NEXT_TOKEN_ALONE_SET = TokenSet.orSet(
    tokenSetOf(WHITE_SPACE, LBRACK, LBRACE, LPAREN, QUOTE_IDENTIFIER),
    RS_COMMENTS,
    RS_LITERALS,
    PROC_MACRO_IDENTIFIER_TOKENS,
)

private val WHITESPACE_OR_COMMENTS = TokenSet.orSet(
    tokenSetOf(WHITE_SPACE),
    RS_COMMENTS
)
