/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.typing

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.util.text.CharSequenceSubSequence
import org.rust.lang.core.psi.RsComplexLiteral
import org.rust.lang.core.psi.RsLiteralKind

fun isValidOffset(offset: Int, text: CharSequence): Boolean =
    0 <= offset && offset <= text.length

/**
 * Beware that this returns `false` for EOF!
 */
fun isValidInnerOffset(offset: Int, text: CharSequence): Boolean =
    0 <= offset && offset < text.length

/**
 * Get previous and next token types relative to [iterator] position.
 */
fun getSiblingTokens(iterator: HighlighterIterator): Pair<IElementType?, IElementType?> {
    iterator.retreat()
    val prev = if (iterator.atEnd()) null else iterator.tokenType
    iterator.advance()

    iterator.advance()
    val next = if (iterator.atEnd()) null else iterator.tokenType
    iterator.retreat()

    return prev to next
}

/**
 * Creates virtual [RsLiteralKind] PSI element assuming that it is represented as
 * single, contiguous token in highlighter, in other words - it doesn't contain
 * any escape sequences etc. (hence 'dumb').
 */
fun getLiteralDumb(iterator: HighlighterIterator): RsComplexLiteral? {
    val start = iterator.start
    val end = iterator.end

    val document = iterator.document
    val text = document.charsSequence
    val literalText = CharSequenceSubSequence(text, start, end)

    val elementType = iterator.tokenType ?: return null
    return RsLiteralKind.fromAstNode(LeafPsiElement(elementType, literalText)) as? RsComplexLiteral
}

fun Document.deleteChar(offset: Int) {
    deleteString(offset, offset + 1)
}

fun CharSequence.endsWithUnescapedBackslash(): Boolean =
    takeLastWhile { it == '\\' }.length % 2 == 1
