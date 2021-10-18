/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.tt

import com.intellij.util.SmartList
import org.rust.lang.core.macros.MappedTextRange
import org.rust.lang.core.macros.RangeMap
import org.rust.lang.core.macros.mergeAdd
import org.rust.lang.core.psi.MacroBraces
import org.rust.stdext.withNext

data class MappedSubtree(val subtree: TokenTree.Subtree, val tokenMap: TokenMap)

class TokenMap(val map: List<TokenMetadata>) {
    fun get(id: TokenId): TokenMetadata? = map.getOrNull(id)
    fun merge(other: TokenMap): TokenMap = TokenMap(map + other.map)
}

sealed class TokenMetadata {
    data class Token(val startOffset: Int, val rightTrivia: CharSequence): TokenMetadata()
    data class Delimiter(val open: Token, val close: Token?): TokenMetadata()
}

fun MappedSubtree.toMappedText(): Pair<CharSequence, RangeMap> {
    return SubtreeTextBuilder(subtree, tokenMap).toText()
}

private class SubtreeTextBuilder(
    private val subtree: TokenTree.Subtree,
    private val tokenMap: TokenMap
) {
    private val sb = StringBuilder()
    private val ranges = SmartList<MappedTextRange>()

    fun toText(): Pair<CharSequence, RangeMap> {
        subtree.appendSubtree()
        return sb to RangeMap.from(ranges)
    }

    private fun TokenTree.Subtree.appendSubtree() {
        delimiter?.let { appendDelimiterPart(it, DelimiterBracePart.OPEN) }
        for ((tokenTree, nextTokenTree) in tokenTrees.asSequence().withNext()) {
            when (tokenTree) {
                is TokenTree.Leaf -> tokenTree.appendLeaf(nextTokenTree)
                is TokenTree.Subtree -> tokenTree.appendSubtree()
            }
        }
        delimiter?.let { appendDelimiterPart(it, DelimiterBracePart.CLOSE) }
    }

    private fun TokenTree.Leaf.appendLeaf(nextTokenTree: TokenTree?) {
        val nextId = when (nextTokenTree) {
            is TokenTree.Leaf -> nextTokenTree.id
            is TokenTree.Subtree, null -> null
        }
        when (this) {
            is TokenTree.Leaf.Literal -> append(text, id, nextId, Spacing.Alone)
            is TokenTree.Leaf.Ident -> append(text, id, nextId, Spacing.Alone)
            is TokenTree.Leaf.Punct -> append(char, id, nextId, spacing)
        }
    }

    private fun append(text: CharSequence, id: TokenId, nextId: TokenId?, spacing: Spacing) {
        val meta = (tokenMap.get(id) as? TokenMetadata.Token)
        if (meta != null) {
            ranges.mergeAdd(MappedTextRange(meta.startOffset, sb.length, text.length + meta.rightTrivia.length))
        }
        sb.append(text)
        if (meta != null) {
            sb.append(meta.rightTrivia)

            if (meta.rightTrivia.isEmpty() && spacing == Spacing.Alone && (id < 0 || nextId != id + 1)) {
                sb.append(" ")
            }
        } else if (spacing == Spacing.Alone) {
            sb.append(" ")
        }
    }

    private fun appendDelimiterPart(delimiter: Delimiter, part: DelimiterBracePart) {
        val meta = (tokenMap.get(delimiter.id) as? TokenMetadata.Delimiter)?.let { part.metaGetter(it) }
        if (meta != null) {
            ranges.mergeAdd(MappedTextRange(meta.startOffset, sb.length, 1 + meta.rightTrivia.length))
        }
        sb.append(part.textGetter(delimiter.kind))
        if (meta != null) {
            sb.append(meta.rightTrivia)
        }
    }

    private enum class DelimiterBracePart(
        val metaGetter: (TokenMetadata.Delimiter) -> TokenMetadata.Token?,
        val textGetter: (MacroBraces) -> String
    ) {
        OPEN(TokenMetadata.Delimiter::open, MacroBraces::openText),
        CLOSE(TokenMetadata.Delimiter::close, MacroBraces::closeText)
    }
}
