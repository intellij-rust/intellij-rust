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
import com.intellij.psi.impl.source.tree.TreeElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.ILazyParseableElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import org.rust.lang.RsLanguage
import org.rust.lang.doc.psi.impl.*
import org.rust.stdext.removeLast
import kotlin.math.max
import kotlin.math.min

class RsDocCommentElementType(debugName: String) : ILazyParseableElementType(debugName, RsLanguage) {
    override fun doParseContents(chameleon: ASTNode, psi: PsiElement): ASTNode {
        val pieces = mutableListOf<Piece>()
        val text = chameleon.text
        val orgn = text.split("\n")
        val md = StringBuilder()
        val map = IntArray(text.length)
        var counter = 0
        for ((i, s) in RsDocKind.of(this).removeDecoration(orgn.asSequence()).withIndex()) {
            val prefix = orgn[i].removeSuffix(s) // TODO not always a suffix
            pieces += Piece.Prefix(prefix)
            counter += prefix.length
            val new = s + "\n"
            pieces += Piece.Text(new)
            for (j in new.indices) {
                map[md.length + j] = counter + j
            }
            counter += new.length
            md.append(new)
        }
        val flavour = CommonMarkFlavourDescriptor()
        val rootNode = MarkdownParser(flavour).buildMarkdownTreeFromString(md.removeSuffix("\n").toString())
        val root = RsDocCompositePsiElement(Temp.DOC_ROOT)
        val bldr = object : org.intellij.markdown.ast.visitors.RecursiveVisitor() {
            private val stack = mutableListOf<CompositeElement>()
            private val parent: CompositeElement get() = stack.last()

            private var prevEnd = 0

            private fun insertLeaves(startOffset: Int, endOffset: Int) {
                for (piece in pieces.split(startOffset, endOffset)) {
                    val type = when (piece) {
                        is Piece.Text -> Temp.DOC_TEXT
                        is Piece.Prefix -> Temp.DOC_PREFIX
                    }
                    insertNode(LeafPsiElement(type, piece.str))
                }
            }

            override fun visitNode(node: org.intellij.markdown.ast.ASTNode) {
                md.toString()
                val type = Temp.map(node.type)
                if (type == null) {
                    if (node !is org.intellij.markdown.ast.LeafASTNode) {
                        visitChildren(parent, node)
                    }
                    return
                }
                val startOffsetT = map[node.startOffset]
                if (startOffsetT != prevEnd) {
                    insertLeaves(prevEnd, startOffsetT)
                }
                prevEnd = startOffsetT
                val lowered = if (type is RsDocTokenType2) {
                    type.astFactory(type)
                } else {
                    RsDocCompositePsiElement(type)
                }
                insertNode(lowered)

                visitChildren(lowered, node)
            }

            private fun insertNode(lowered: TreeElement) {
                parent.rawAddChildrenWithoutNotifications(lowered)
            }

            private fun visitChildren(lowered: CompositeElement, node: org.intellij.markdown.ast.ASTNode) {
                stack += lowered
                super.visitNode(node)

                val endOffsetT = map[node.endOffset]
                if (endOffsetT != prevEnd) {
                    insertLeaves(prevEnd, endOffsetT)
                }
                prevEnd = endOffsetT

                stack.removeLast()
            }

            fun visitRoot(root: CompositeElement, node: org.intellij.markdown.ast.ASTNode) {
                stack += root
                super.visitNode(node)
                if (prevEnd < text.length) {
                    insertLeaves(prevEnd, text.length)
                }
            }
        }
        bldr.visitRoot(root, rootNode)
        return root.firstChildNode
    }

    override fun createNode(text: CharSequence): ASTNode? = RsDocCommentImpl(this, text)
}

sealed class Piece(val str: CharSequence) {
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

private fun List<Piece>.split(startOffset: Int, endOffset: Int): List<Piece> {
    var offset = 0
    val result = mutableListOf<Piece>()
    for (p in this) {
        val pieceEndOffset = offset + p.str.length
        if (startOffset < pieceEndOffset && endOffset - offset > 0) {
            result += p.cut(startOffset - offset, endOffset - offset)
        }
        offset += p.str.length
    }

    return result
}

object Temp {
    val DOC_ROOT = RsDocTokenType("<DOC_ROOT>")
    val DOC_TEXT = RsDocTokenType("<DOC_TEXT>")
    val DOC_PREFIX = RsDocTokenType("<DOC_PREFIX>")

    val DOC_STRONG = RsDocTokenType("<DOC_STRONG>")
    val DOC_EMPH = RsDocTokenType("<DOC_EMPH>")
    val DOC_HEADING = RsDocTokenType("<DOC_HEADING>")
    val DOC_INLINE_LINK = RsDocTokenType2("<DOC_INLINE_LINK>", ::RsDocInlineLinkImpl)
    val DOC_SHORT_REFERENCE_LINK = RsDocTokenType2("<DOC_SHORT_REFERENCE_LINK>", ::RsDocLinkReferenceShortImpl)
    val DOC_FULL_REFERENCE_LINK = RsDocTokenType2("<DOC_FULL_REFERENCE_LINK>", ::RsDocLinkReferenceFullImpl)
    val DOC_AUTOLINK = RsDocTokenType("<DOC_AUTOLINK>")
    val DOC_LINK_REF_DEF = RsDocTokenType2("<DOC_LINK_REF_DEF>", ::RsDocLinkReferenceDefImpl)
    val DOC_LINK_LABEL = RsDocTokenType2("<DOC_LINK_LABEL>", ::RsDocLinkLabelImpl)
    val DOC_LINK_TITLE = RsDocTokenType("<DOC_LINK_TITLE>")
    val DOC_LINK_TEXT = RsDocTokenType2("<DOC_LINK_TEXT>", ::RsDocLinkTextImpl)
    val DOC_LINK_DESTINATION = RsDocTokenType2("<DOC_LINK_DESTINATION>", ::RsDocLinkDestinationImpl)
    val DOC_IMAGE = RsDocTokenType("<DOC_DOC_IMAGE>")
    // `foo`
    val DOC_CODE_SPAN = RsDocTokenType("<DOC_CODE_SPAN>")
    // ```
    // foo
    // ```
    val DOC_CODE_FENCE = RsDocTokenType2("<DOC_CODE_FENCE>", ::RsDocCodeFenceImpl)
    //     foo
    //     bar
    // (^ 4 spaces)
    val DOC_CODE_BLOCK = RsDocTokenType("<DOC_CODE_BLOCK>")
    val DOC_BLOCK_QUOTE = RsDocTokenType("<DOC_BLOCK_QUOTE>")
    val DOC_HTML_BLOCK = RsDocTokenType("<DOC_HTML_BLOCK>")
    val DOC_ORDERED_LIST = RsDocTokenType("<DOC_ORDERED_LIST>")
    val DOC_UNORDERED_LIST = RsDocTokenType("<DOC_UNORDERED_LIST>")
    val DOC_LIST_ITEM = RsDocTokenType("<DOC_LIST_ITEM>")

    private val HEADERS = setOf(
        MarkdownElementTypes.ATX_1,
        MarkdownElementTypes.ATX_2,
        MarkdownElementTypes.ATX_3,
        MarkdownElementTypes.ATX_4,
        MarkdownElementTypes.ATX_5,
        MarkdownElementTypes.ATX_6,
        MarkdownElementTypes.SETEXT_1,
        MarkdownElementTypes.SETEXT_2
    )

    fun map(type: org.intellij.markdown.IElementType): RsDocTokenType? {
        return when (type) {
            in HEADERS -> DOC_HEADING
            MarkdownElementTypes.STRONG -> DOC_STRONG
            MarkdownElementTypes.EMPH -> DOC_EMPH
            MarkdownElementTypes.CODE_FENCE -> DOC_CODE_FENCE
            MarkdownElementTypes.CODE_SPAN -> DOC_CODE_SPAN
            MarkdownElementTypes.LINK_DEFINITION -> DOC_LINK_REF_DEF
            MarkdownElementTypes.SHORT_REFERENCE_LINK -> DOC_SHORT_REFERENCE_LINK
            MarkdownElementTypes.FULL_REFERENCE_LINK -> DOC_FULL_REFERENCE_LINK
            MarkdownElementTypes.AUTOLINK -> DOC_AUTOLINK
            MarkdownElementTypes.INLINE_LINK -> DOC_INLINE_LINK
            MarkdownElementTypes.LINK_TITLE -> DOC_LINK_TITLE
            MarkdownElementTypes.LINK_LABEL -> DOC_LINK_LABEL
            MarkdownElementTypes.LINK_TEXT -> DOC_LINK_TEXT
            MarkdownElementTypes.LINK_DESTINATION -> DOC_LINK_DESTINATION
            MarkdownElementTypes.IMAGE -> DOC_IMAGE
            MarkdownElementTypes.CODE_BLOCK -> DOC_CODE_BLOCK
            MarkdownElementTypes.HTML_BLOCK -> DOC_HTML_BLOCK
            MarkdownElementTypes.BLOCK_QUOTE -> DOC_BLOCK_QUOTE
            MarkdownElementTypes.ORDERED_LIST -> DOC_ORDERED_LIST
            MarkdownElementTypes.UNORDERED_LIST -> DOC_UNORDERED_LIST
            MarkdownElementTypes.LIST_ITEM -> DOC_LIST_ITEM
            else -> null
        }
    }
}

class RsDocCompositePsiElement(type: IElementType) : CompositePsiElement(type)
