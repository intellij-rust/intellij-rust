/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.doc.psi

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.impl.source.tree.PsiWhiteSpaceImpl
import com.intellij.psi.impl.source.tree.SharedImplUtil
import com.intellij.psi.tree.ILazyParseableElementType
import com.intellij.util.CharTable
import com.intellij.util.text.CharArrayUtil
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import org.rust.lang.RsLanguage
import org.rust.lang.doc.psi.impl.RsDocCommentImpl
import org.rust.lang.doc.psi.impl.RsDocGapImpl
import kotlin.math.max
import kotlin.math.min

class RsDocCommentElementType(debugName: String) : ILazyParseableElementType(debugName, RsLanguage) {
    override fun doParseContents(chameleon: ASTNode, psi: PsiElement): ASTNode {
        val charTable = SharedImplUtil.findCharTableByTree(chameleon)
        val textMap = DocTextMap.new(chameleon.text, RsDocKind.of(this))

        val root = RsDocCommentImpl(this, null)
        RsDocMarkdownAstBuilder(textMap, charTable).buildTree(
            root,
            MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(textMap.mappedText.toString())
        )

        return root.firstChildNode
    }

    override fun createNode(text: CharSequence): ASTNode? = RsDocCommentImpl(this, text)
}

private class RsDocMarkdownAstBuilder(
    private val textMap: DocTextMap,
    private val charTable: CharTable
) {
    private var prevNodeEnd = 0

    private fun CompositeElement.insertLeaves(startOffset: Int, endOffset: Int) {
        for (piece in textMap.split(startOffset, endOffset)) {
            when (piece) {
                is Piece.Text -> {
                    rawAddChildrenWithoutNotifications(LeafPsiElement(RsDocElementTypes.DOC_DATA, charTable.intern(piece.str)))
                }
                is Piece.Gap -> {
                    val gapStart = CharArrayUtil.shiftForward(piece.str, 0, "\n\t ")
                    if (gapStart != 0) {
                        rawAddChildrenWithoutNotifications(PsiWhiteSpaceImpl(charTable.intern(piece.str.substring(0, gapStart))))
                    }
                    if (gapStart != piece.str.length) {
                        val gapEnd = CharArrayUtil.shiftBackward(piece.str, gapStart, piece.str.lastIndex, "\n\t ") + 1
                        val gapText = charTable.intern(piece.str, gapStart, gapEnd)
                        check(gapText.isNotEmpty())
                        rawAddChildrenWithoutNotifications(RsDocGapImpl(RsDocElementTypes.DOC_GAP, gapText))
                        if (gapEnd != piece.str.length) {
                            rawAddChildrenWithoutNotifications(PsiWhiteSpaceImpl(charTable.intern(piece.str.substring(gapEnd))))
                        }
                    }
                }
            }
        }
    }

    private fun CompositeElement.insertLeaves(endOffset: Int) {
        val endOffsetMapped = textMap.mapOffsetFromMarkdownToRust(endOffset)
        if (endOffsetMapped != prevNodeEnd) {
            insertLeaves(prevNodeEnd, endOffsetMapped)
        }
        prevNodeEnd = endOffsetMapped
    }

    private fun visitNode(parent: CompositeElement, markdownNode: org.intellij.markdown.ast.ASTNode) {
        val type = RsDocElementTypes.map(markdownNode.type)
        if (type == null) {
            if (markdownNode !is org.intellij.markdown.ast.LeafASTNode) {
                visitChildren(parent, markdownNode)
            }
            return
        }

        parent.insertLeaves(markdownNode.startOffset)

        val node = type.createCompositeNode()
        parent.rawAddChildrenWithoutNotifications(node)

        visitChildren(node, markdownNode)
        node.insertLeaves(markdownNode.endOffset)
    }

    private fun visitChildren(node: CompositeElement, markdownNode: org.intellij.markdown.ast.ASTNode) {
        for (markdownChild in markdownNode.children) {
            visitNode(node, markdownChild)
        }
    }

    fun buildTree(root: CompositeElement, markdownRoot: org.intellij.markdown.ast.ASTNode) {
        for (markdownChild in markdownRoot.children) {
            visitNode(root, markdownChild)
        }

        if (prevNodeEnd < textMap.originalText.length) {
            root.insertLeaves(prevNodeEnd, textMap.originalText.length)
        }
    }
}


private class DocTextMap(
    val originalText: String,
    val mappedText: CharSequence,
    private val offsetMap: IntArray,
    private val pieces: List<Piece>
) {
    fun mapOffsetFromMarkdownToRust(offset: Int): Int = offsetMap[offset]

    fun split(startOffset: Int, endOffset: Int): List<Piece> {
        var offset = 0
        val result = mutableListOf<Piece>()
        for (p in pieces) {
            val pieceEndOffset = offset + p.str.length
            if (startOffset < pieceEndOffset && endOffset - offset > 0) {
                result += p.cut(startOffset - offset, endOffset - offset)
            }
            offset += p.str.length
        }

        return result
    }


    companion object {
        fun new(text: String, kind: RsDocKind): DocTextMap {
            val pieces = mutableListOf<Piece>()
            val mappedText = StringBuilder()
            val map = IntArray(text.length)
            var counter = 0
            val lines = kind.removeDecorationToLines(text).toList()
            for ((i, line) in lines.withIndex()) {
                val isLast = i == lines.lastIndex
                val prefix = line.prefix
                val suffix = line.suffix

                if (prefix.isNotEmpty()) {
                    pieces.mergeAddGap(prefix)
                    counter += prefix.length
                }

                val hasLineBreak = !isLast && suffix.isEmpty()
                val content = line.content
                val new = content + if (hasLineBreak) "\n" else ""
                if (content.isNotEmpty() || hasLineBreak) {
                    if (content.isNotEmpty()) {
                        pieces += Piece.Text(content)
                    }
                    for (j in new.indices) {
                        map[mappedText.length + j] = counter + j
                    }
                    map[mappedText.length + new.length] = counter + new.length
                    counter += new.length
                    mappedText.append(new)
                }

                if (hasLineBreak) {
                    pieces += Piece.Gap("\n")
                }

                if (suffix.isNotEmpty()) {
//                    pieces += Piece.Gap(suffix)
                    pieces.mergeAddGap(suffix)
                    counter += suffix.length
                }
            }

            return DocTextMap(text, mappedText, map, pieces)
        }

        private fun MutableList<Piece>.mergeAddGap(gap: String) {
            if (lastOrNull() is Piece.Gap) {
                this[lastIndex] = Piece.Gap(this[lastIndex].str.toString() + gap)
            } else {
                this += Piece.Gap(gap)
            }
        }
    }
}

private sealed class Piece(val str: CharSequence) {
    class Text(str: CharSequence): Piece(str)
    class Gap(str: CharSequence): Piece(str)
}

private fun Piece.cut(startOffset: Int, endOffset: Int): Piece {
    val newStr = str.subSequence(max(0, startOffset), min(endOffset, str.length))
    return when (this) {
        is Piece.Text -> Piece.Text(newStr)
        is Piece.Gap -> Piece.Gap(newStr)
    }
}
