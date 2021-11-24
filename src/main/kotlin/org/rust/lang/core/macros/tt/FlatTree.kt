/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.tt

import gnu.trove.TIntArrayList
import org.rust.lang.core.psi.MacroBraces
import org.rust.stdext.dequeOf
import java.util.*

/**
 * See https://github.com/rust-analyzer/rust-analyzer/blob/3e4ac8a2c9136052/crates/proc_macro_api/src/msg/flat.rs
 */
class FlatTree(
    val subtree: TIntArrayList,
    val literal: TIntArrayList,
    val punct: TIntArrayList,
    val ident: TIntArrayList,
    val tokenTree: TIntArrayList,
    val text: List<String>,
) {
    fun toTokenTree(): TokenTree.Subtree {
        val res: MutableList<TokenTree.Subtree?> = ArrayList(subtree.size())
        repeat(subtree.size()) { res.add(null) }

        for (i in (0 until subtree.size()).step(4).reversed()) {
            val delimiterId = subtree[i]
            val kind = subtree[i + 1]
            val lo = subtree[i + 2]
            val len = subtree[i + 3]

            val rawTokenTrees = tokenTree
            val tokenTrees = ArrayList<TokenTree>(len - lo)
            for (j in lo until len) {
                val idxTag = rawTokenTrees[j]
                val tag = idxTag and 0b11
                val idx = idxTag shr 2
                tokenTrees += when (tag) {
                    0b00 -> res[idx]!! // we iterate subtrees in reverse to guarantee that this subtree exists
                    0b01 -> {
                        val index = idx * 2
                        val tokenId = literal[index]
                        val text = literal[index + 1]
                        TokenTree.Leaf.Literal(this.text[text], tokenId)
                    }
                    0b10 -> {
                        val index = idx * 3
                        val tokenId = punct[index]
                        val chr = punct[index + 1].toChar()
                        val spacing = when (val spacing = punct[index + 2]) {
                            0 -> Spacing.Alone
                            1 -> Spacing.Joint
                            else -> error("Unknown spacing $spacing")
                        }
                        TokenTree.Leaf.Punct(chr.toString(), spacing, tokenId)
                    }
                    0b11 -> {
                        val index = idx * 2
                        val tokenId = ident[index]
                        val text = ident[index + 1]
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

            res[i / 4] = TokenTree.Subtree(
                delimiterKind?.let { Delimiter(delimiterId, delimiterKind) },
                tokenTrees,
            )
        }

        return res[0]!!
    }

    companion object {
        fun fromSubtree(root: TokenTree.Subtree): FlatTree =
            FlatTreeBuilder().apply { write(root) }.toFlatTree()
    }
}

private class FlatTreeBuilder {
    private val work: Deque<Pair<Int, TokenTree.Subtree>> = dequeOf()
    private val stringTable: HashMap<String, Int> = hashMapOf()

    private val subtree: TIntArrayList = TIntArrayList()
    private val literal: TIntArrayList = TIntArrayList()
    private val punct: TIntArrayList = TIntArrayList()
    private val ident: TIntArrayList = TIntArrayList()
    private val tokenTree: TIntArrayList = TIntArrayList()
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
        var firstTt = tokenTree.size()
        val nTt = subtree.tokenTrees.size
        tokenTree.fill(firstTt, firstTt + nTt, -1)

        this.subtree[subtreeId * 4 + 2] = firstTt
        this.subtree[subtreeId * 4 + 3] = firstTt + nTt

        for (child in subtree.tokenTrees) {
            val idxTag = when (child) {
                is TokenTree.Subtree -> {
                    val idx = this.enqueue(child)
                    idx.shl(2).or(0b00)
                }
                is TokenTree.Leaf.Literal -> {
                    val idx = this.literal.size() / 2
                    val text = this.intern(child.text)
                    this.literal.add(child.id)
                    this.literal.add(text)
                    idx.shl(2).or(0b01)
                }
                is TokenTree.Leaf.Punct -> {
                    val idx = this.punct.size() / 3
                    this.punct.add(child.id)
                    this.punct.add(child.char[0].toInt())
                    this.punct.add(when (child.spacing) {
                        Spacing.Alone -> 0
                        Spacing.Joint -> 1
                    })
                    idx.shl(2).or(0b10)
                }
                is TokenTree.Leaf.Ident -> {
                    val idx = this.ident.size() / 2
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
        val idx = this.subtree.size() / 4
        val delimiterId = subtree.delimiter?.id ?: -1
        val delimiterKind = subtree.delimiter?.kind
        this.subtree.apply {
            add(delimiterId)
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
