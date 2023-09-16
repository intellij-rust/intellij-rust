/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.tt

import it.unimi.dsi.fastutil.ints.IntArrayList
import org.rust.lang.core.macros.proc.ProMacroExpanderVersion
import org.rust.lang.core.psi.MacroBraces
import org.rust.stdext.dequeOf
import java.util.*

/**
 * See https://github.com/rust-analyzer/rust-analyzer/blob/3e4ac8a2c9136052/crates/proc_macro_api/src/msg/flat.rs
 */
class FlatTree(
    val subtree: IntArrayList,
    val literal: IntArrayList,
    val punct: IntArrayList,
    val ident: IntArrayList,
    val tokenTree: IntArrayList,
    val text: List<String>,
) {
    fun toTokenTree(version: ProMacroExpanderVersion): TokenTree.Subtree {
        val encodeCloseSpan = version >= ProMacroExpanderVersion.ENCODE_CLOSE_SPAN_VERSION
        val offset = if (encodeCloseSpan) 1 else 0
        val stepSize = if (encodeCloseSpan) 5 else 4

        val res: MutableList<TokenTree.Subtree?> = ArrayList(subtree.size)
        repeat(subtree.size) { res.add(null) }

        for (i in (0 until subtree.size).step(stepSize).reversed()) {
            val delimiterId = subtree.getInt(i)
            val kind = subtree.getInt(i + offset + 1)
            val lo = subtree.getInt(i + offset + 2)
            val len = subtree.getInt(i + offset + 3)

            val rawTokenTrees = tokenTree
            val tokenTrees = ArrayList<TokenTree>(len - lo)
            for (j in lo until len) {
                val idxTag = rawTokenTrees.getInt(j)
                val tag = idxTag and 0b11
                val idx = idxTag shr 2
                tokenTrees += when (tag) {
                    0b00 -> res[idx]!! // we iterate subtrees in reverse to guarantee that this subtree exists
                    0b01 -> {
                        val index = idx * 2
                        val tokenId = literal.getInt(index)
                        val text = literal.getInt(index + 1)
                        TokenTree.Leaf.Literal(this.text[text], tokenId)
                    }
                    0b10 -> {
                        val index = idx * 3
                        val tokenId = punct.getInt(index)
                        val chr = punct.getInt(index + 1).toChar()
                        val spacing = when (val spacing = punct.getInt(index + 2)) {
                            0 -> Spacing.Alone
                            1 -> Spacing.Joint
                            else -> error("Unknown spacing $spacing")
                        }
                        TokenTree.Leaf.Punct(chr.toString(), spacing, tokenId)
                    }
                    0b11 -> {
                        val index = idx * 2
                        val tokenId = ident.getInt(index)
                        val text = ident.getInt(index + 1)
                        TokenTree.Leaf.Ident(this.text[text], tokenId)
                    }
                    else -> error("bad tag $tag")
                }
            }

            val delimiterKind = when (kind) {
                0 -> null
                1 -> MacroBraces.PARENS
                2 -> MacroBraces.BRACES
                3 -> MacroBraces.BRACKS
                else -> error("Unknown kind $kind")
            }

            res[i / stepSize] = TokenTree.Subtree(
                delimiterKind?.let { Delimiter(delimiterId, delimiterKind) },
                tokenTrees,
            )
        }

        return res[0]!!
    }

    companion object {
        fun fromSubtree(root: TokenTree.Subtree, version: ProMacroExpanderVersion): FlatTree =
            FlatTreeBuilder(version >= ProMacroExpanderVersion.ENCODE_CLOSE_SPAN_VERSION).apply { write(root) }.toFlatTree()
    }
}

private class FlatTreeBuilder(private val encodeCloseSpan: Boolean) {
    private val work: Deque<Pair<Int, TokenTree.Subtree>> = dequeOf()
    private val stringTable: HashMap<String, Int> = hashMapOf()

    private val subtree: IntArrayList = IntArrayList()
    private val literal: IntArrayList = IntArrayList()
    private val punct: IntArrayList = IntArrayList()
    private val ident: IntArrayList = IntArrayList()
    private val tokenTree: IntArrayList = IntArrayList()
    private val text: MutableList<String> = mutableListOf()

    fun toFlatTree(): FlatTree = FlatTree(subtree, literal, punct, ident, tokenTree, text)

    fun write(root: TokenTree.Subtree) {
        enqueue(root)
        while (true) {
            val (idx, subtree) = work.pollFirst() ?: break
            subtree(idx, subtree)
        }
    }

    private fun subtree(subtreeId: Int, subtree: TokenTree.Subtree) {
        var firstTt = tokenTree.size
        val nTt = subtree.tokenTrees.size
        tokenTree.ensureCapacity(firstTt + nTt)
        for (i in tokenTree.size until firstTt + nTt) {
            tokenTree.add(-1)
        }

        val offset = if (encodeCloseSpan) 1 else 0
        val stepSize = if (encodeCloseSpan) 5 else 4

        this.subtree[subtreeId * stepSize + offset + 2] = firstTt
        this.subtree[subtreeId * stepSize + offset + 3] = firstTt + nTt

        for (child in subtree.tokenTrees) {
            val idxTag = when (child) {
                is TokenTree.Subtree -> {
                    val idx = this.enqueue(child)
                    idx.shl(2).or(0b00)
                }
                is TokenTree.Leaf.Literal -> {
                    val idx = this.literal.size / 2
                    val text = this.intern(child.text)
                    this.literal.add(child.id)
                    this.literal.add(text)
                    idx.shl(2).or(0b01)
                }
                is TokenTree.Leaf.Punct -> {
                    val idx = this.punct.size / 3
                    this.punct.add(child.id)
                    this.punct.add(child.char[0].code)
                    this.punct.add(when (child.spacing) {
                        Spacing.Alone -> 0
                        Spacing.Joint -> 1
                    })
                    idx.shl(2).or(0b10)
                }
                is TokenTree.Leaf.Ident -> {
                    val idx = this.ident.size / 2
                    val text = this.intern(child.text)
                    this.ident.add(child.id)
                    this.ident.add(text)
                    idx.shl(2).or(0b11)
                }
            }
            this.tokenTree[firstTt] = idxTag
            firstTt += 1
        }
    }

    private fun enqueue(subtree: TokenTree.Subtree): Int {
        val stepSize = if (encodeCloseSpan) 5 else 4
        val idx = this.subtree.size / stepSize
        val delimiterId = subtree.delimiter?.id ?: -1
        val delimiterKind = subtree.delimiter?.kind
        this.subtree.apply {
            add(delimiterId)
            if (encodeCloseSpan) {
                add(-1) // closeId
            }
            add(when (delimiterKind) {
                null -> 0
                MacroBraces.PARENS -> 1
                MacroBraces.BRACES -> 2
                MacroBraces.BRACKS -> 3
            })
            add(-1)
            add(-1)
        }
        this.work.addLast(Pair(idx, subtree))
        return idx
    }

    private fun intern(text: String): Int {
        return stringTable.getOrPut(text) {
            val idx = this.text.size
            this.text.add(text)
            idx
        }
    }
}
