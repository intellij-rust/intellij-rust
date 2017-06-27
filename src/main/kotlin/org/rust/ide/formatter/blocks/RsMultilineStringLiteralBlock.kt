/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter.blocks

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.formatter.common.AbstractBlock
import java.util.*

/**
 * Rust has multiline string literals, and whitespace inside them is actually significant.
 *
 * By default, the formatter will add and remove indentation **inside** string literals
 * without a moment of hesitation. To handle this situation, we create a separate FmtBlock
 * for each line of the string literal, and forbid messing with whitespace between them.
 */
class RsMultilineStringLiteralBlock(
    private val node: ASTNode,
    private val alignment: Alignment?,
    private val indent: Indent?,
    private val wrap: Wrap?
) : ASTBlock {

    override fun getNode(): ASTNode = node
    override fun getAlignment(): Alignment? = alignment
    override fun getIndent(): Indent? = indent
    override fun getWrap(): Wrap? = wrap
    override fun getTextRange(): TextRange = node.textRange
    override fun isIncomplete(): Boolean = false
    override fun isLeaf(): Boolean = false

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes =
        ChildAttributes(null, null)


    override fun getSubBlocks(): List<Block> {
        val result = ArrayList<Block>()
        var startOffset = 0
        val chars = node.chars

        for ((idx, c) in chars.withIndex()) {
            if (c == '\n') {
                result += RsLineBlock(node, TextRange(startOffset, idx))
                startOffset = idx
            }
        }

        if (startOffset < chars.length) {
            result += RsLineBlock(node, TextRange(startOffset, chars.length))
        }
        return result
    }

    override fun getSpacing(child1: Block?, child2: Block): Spacing = Spacing.getReadOnlySpacing()
}

private class RsLineBlock(
    node: ASTNode,
    rangeInParent: TextRange
) : AbstractBlock(node, null, null) {

    private val textRange = rangeInParent.shiftRight(node.startOffset)

    override fun getTextRange(): TextRange = textRange

    override fun isLeaf(): Boolean = true

    override fun buildChildren(): List<Block> = emptyList()

    override fun getSpacing(child1: Block?, child2: Block): Spacing? = null

    override fun toString(): String {
        val text = node.chars[textRange.shiftRight(-node.startOffset)].toString()
        return '"' + text.replace("\n", "\\n").replace("\"", "\\\"") + '"'
    }
}

private operator fun CharSequence.get(range: TextRange): CharSequence =
    subSequence(range.startOffset until range.endOffset)
