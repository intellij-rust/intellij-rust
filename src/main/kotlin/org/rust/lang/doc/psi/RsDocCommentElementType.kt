/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.doc.psi

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.impl.source.tree.CompositePsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.impl.source.tree.SharedImplUtil
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.ILazyParseableElementType
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
        val markdownRoot = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(textMap.mappedText.toString())

        val builder = object {
            private var prevNodeEnd = 0

            private fun CompositeElement.insertLeaves(startOffset: Int, endOffset: Int) {
                for (piece in textMap.split(startOffset, endOffset)) {
                    val text = charTable.intern(piece.str)
                    val leaf = when (piece) {
                        is Piece.Text -> LeafPsiElement(RsDocElementTypes.DOC_TEXT, text)
                        is Piece.Prefix -> RsDocGapImpl(RsDocElementTypes.DOC_GAP, text)
                    }
                    rawAddChildrenWithoutNotifications(leaf)
                }
            }

            private fun CompositeElement.insertLeaves(endOffset: Int) {
                val endOffsetMapped = textMap.mapOffsetFromMarkdownToRust(endOffset)
                if (endOffsetMapped != prevNodeEnd) {
                    insertLeaves(prevNodeEnd, endOffsetMapped)
                }
                prevNodeEnd = endOffsetMapped
            }

            fun visitNode(parent: CompositeElement, markdownNode: org.intellij.markdown.ast.ASTNode) {
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

            fun visitRoot(root: CompositeElement, markdownRoot: org.intellij.markdown.ast.ASTNode) {
                for (markdownChild in markdownRoot.children) {
                    visitNode(root, markdownChild)
                }

                if (prevNodeEnd < textMap.originalText.length) {
                    root.insertLeaves(prevNodeEnd, textMap.originalText.length)
                }
            }
        }

        val root = RsDummyCompositePsiElement(this)
        builder.visitRoot(root, markdownRoot)

        check(textMap.originalText.contentEquals(root.chars)) { "`${textMap.originalText}` != `${root.text}`" }

        return root.firstChildNode
    }

    override fun createNode(text: CharSequence): ASTNode? = RsDocCommentImpl(this, text)
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
            val md = StringBuilder()
            val map = IntArray(text.length)
            var counter = 0
            for (line in kind.removeDecorationToLines(text)) {
                val prefix = line.prefix
                val suffix = line.suffix

                if (prefix.isNotEmpty()) {
                    pieces += Piece.Prefix(prefix)
                    counter += prefix.length
                }

                val new = line.content + if (suffix.isEmpty()) "\n" else ""
                if (new.isNotEmpty()) {
                    pieces += Piece.Text(new)
                    for (j in new.indices) {
                        map[md.length + j] = counter + j
                    }
                    map[md.length + new.length] = counter + new.length
                    counter += new.length
                    md.append(new)
                }

                if (suffix.isNotEmpty()) {
                    pieces += Piece.Prefix(suffix)
                    counter += suffix.length
                }
            }

            return DocTextMap(text, md.removeSuffix("\n"), map, pieces)
        }
    }
}

private sealed class Piece(val str: CharSequence) {
    class Text(str: CharSequence): Piece(str)
    class Prefix(str: CharSequence): Piece(str)
}

private fun Piece.cut(startOffset: Int, endOffset: Int): Piece {
    val newStr = str.subSequence(max(0, startOffset), min(endOffset, str.length))
    return when (this) {
        is Piece.Text -> Piece.Text(newStr)
        is Piece.Prefix -> Piece.Prefix(newStr)
    }
}

private class RsDummyCompositePsiElement(type: IElementType) : CompositePsiElement(type)
