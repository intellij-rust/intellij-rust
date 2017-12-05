/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.folding

import com.intellij.codeInsight.folding.CodeFoldingSettings
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.FoldingGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.RsLanguage
import org.rust.lang.core.leftLeaves
import org.rust.lang.core.leftSiblings
import org.rust.lang.core.parser.RustParserDefinition.Companion.BLOCK_COMMENT
import org.rust.lang.core.parser.RustParserDefinition.Companion.INNER_EOL_DOC_COMMENT
import org.rust.lang.core.parser.RustParserDefinition.Companion.OUTER_EOL_DOC_COMMENT
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.core.psi.ext.getNextNonCommentSibling
import org.rust.lang.core.psi.ext.getPrevNonCommentSibling
import org.rust.lang.core.rightSiblings
import java.lang.Integer.max
import java.util.*

class RsFoldingBuilder : FoldingBuilderEx(), DumbAware {
    override fun getPlaceholderText(node: ASTNode): String =
        when {
            node.elementType == LBRACE -> " { "
            node.elementType == RBRACE -> " }"
            node.elementType == USE_ITEM -> "/* uses */"
            node.psi is RsModDeclItem -> "/* mods */"
            node.psi is RsExternCrateItem -> "/* crates */"
            node.psi is PsiComment -> "/* ... */"
            else -> "{...}"
        }

    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<out FoldingDescriptor> {
        if (root !is RsFile) return emptyArray()

        val descriptors: MutableList<FoldingDescriptor> = ArrayList()
        val usingRanges: MutableList<TextRange> = ArrayList()
        val modsRanges: MutableList<TextRange> = ArrayList()
        val cratesRanges: MutableList<TextRange> = ArrayList()
        val rightMargin = CodeStyleSettingsManager.getSettings(root.project).getRightMargin(RsLanguage)
        val visitor = FoldingVisitor(descriptors, usingRanges, modsRanges, cratesRanges, rightMargin)
        PsiTreeUtil.processElements(root) { it.accept(visitor); true }

        return descriptors.toTypedArray()
    }

    private class FoldingVisitor(
        private val descriptors: MutableList<FoldingDescriptor>,
        private val usesRanges: MutableList<TextRange>,
        private val modsRanges: MutableList<TextRange>,
        private val cratesRanges: MutableList<TextRange>,
        val rightMargin: Int
    ) : RsVisitor() {

        override fun visitStructLiteralBody(o: RsStructLiteralBody) = fold(o)

        override fun visitEnumBody(o: RsEnumBody) = fold(o)

        override fun visitBlockFields(o: RsBlockFields) = fold(o)

        override fun visitBlock(o: RsBlock) {
            if (tryFoldBlockWhitespaces(o)) return
            fold(o)
        }

        override fun visitMatchBody(o: RsMatchBody) = fold(o)

        override fun visitUseGroup(o: RsUseGroup) = fold(o)

        override fun visitMembers(o: RsMembers) = foldBetween(o, o.lbrace, o.rbrace)

        override fun visitModItem(o: RsModItem) = foldBetween(o, o.lbrace, o.rbrace)

        override fun visitMacroArgument(o: RsMacroArgument) = foldBetween(o, o.lbrace, o.rbrace)

        override fun visitComment(comment: PsiComment) {
            when (comment.tokenType) {
                BLOCK_COMMENT,
                INNER_EOL_DOC_COMMENT,
                OUTER_EOL_DOC_COMMENT -> fold(comment)
            }
        }

        override fun visitStructItem(o: RsStructItem) {
            val blockFields = o.blockFields
            if (blockFields != null) {
                fold(blockFields)
            }
        }

        private fun fold(element: PsiElement) {
            descriptors += FoldingDescriptor(element.node, element.textRange)
        }

        private fun foldBetween(element: PsiElement, left: PsiElement?, right: PsiElement?) {
            if (left != null && right != null) {
                val range = TextRange(left.textOffset, right.textOffset + 1)
                descriptors += FoldingDescriptor(element.node, range)
            }
        }

        private fun tryFoldBlockWhitespaces(block: RsBlock): Boolean {
            if (block.parent !is RsFunction) return false

            val doc = PsiDocumentManager.getInstance(block.project).getDocument(block.containingFile) ?: return false
            val maxLenght = rightMargin - block.getOffsetInLine(doc) - ONE_LINER_PLACEHOLDERS_EXTRA_LENGTH
            if (!block.isSingleLine(doc, maxLenght)) return false

            val lbrace = block.lbrace
            val rbrace = block.rbrace ?: return false

            val blockElement = lbrace.getNextNonCommentSibling()
            if (blockElement == null || blockElement != rbrace.getPrevNonCommentSibling()) return false
            if (blockElement.textContains('\n')) return false
            if (!(doc.areOnAdjacentLines(lbrace, blockElement) && doc.areOnAdjacentLines(blockElement, rbrace))) return false

            val leadingSpace = lbrace.nextSibling as? PsiWhiteSpace ?: return false
            val trailingSpace = rbrace.prevSibling as? PsiWhiteSpace ?: return false

            val leftEl = block.prevSibling as? PsiWhiteSpace ?: lbrace
            val range1 = TextRange(leftEl.textOffset, leadingSpace.textRange.endOffset)
            val range2 = TextRange(trailingSpace.textOffset, rbrace.textRange.endOffset)
            val group = FoldingGroup.newGroup("one-liner")
            descriptors += FoldingDescriptor(lbrace.node, range1, group)
            descriptors += FoldingDescriptor(rbrace.node, range2, group)

            return true
        }

        override fun visitUseItem(o: RsUseItem) {
            foldRepeatingItems(o, usesRanges)
        }

        override fun visitModDeclItem(o: RsModDeclItem) {
            foldRepeatingItems(o, modsRanges)
        }

        override fun visitExternCrateItem(o: RsExternCrateItem) {
            foldRepeatingItems(o, cratesRanges)
        }

        private inline fun <reified T> foldRepeatingItems(startNode: T, ranges: MutableList<TextRange>) {
            if (isInRangesAlready(ranges, startNode as PsiElement)) return

            var lastNode: PsiElement? = null
            var tmpNode: PsiElement? = startNode

            while (tmpNode is T || tmpNode is PsiWhiteSpace) {
                tmpNode = tmpNode.getNextNonCommentSibling()
                if (tmpNode is T)
                    lastNode = tmpNode
            }

            if (lastNode == startNode) return

            if (lastNode != null) {
                val range = TextRange(startNode.textRange.startOffset, lastNode.textRange.endOffset)
                descriptors += FoldingDescriptor(startNode.node, range)
                ranges.add(range)
            }
        }

        private fun isInRangesAlready(ranges: MutableList<TextRange>, element: PsiElement?): Boolean {
            if (element == null) return false
            return !ranges.filter { x -> x.contains(element.textOffset) }.isEmpty()
        }
    }

    override fun isCollapsedByDefault(node: ASTNode): Boolean =
        (RsCodeFoldingSettings.instance.collapsibleOneLineMethods && node.elementType in COLLAPSED_BY_DEFAULT)
            || (CodeFoldingSettings.getInstance().COLLAPSE_DOC_COMMENTS && node.elementType in DOC_COMMENTS)

    private companion object {
        val COLLAPSED_BY_DEFAULT = TokenSet.create(LBRACE, RBRACE)
        val DOC_COMMENTS = TokenSet.create(INNER_EOL_DOC_COMMENT, OUTER_EOL_DOC_COMMENT)
        val ONE_LINER_PLACEHOLDERS_EXTRA_LENGTH = 4
    }
}

private fun Document.areOnAdjacentLines(first: PsiElement, second: PsiElement): Boolean =
    getLineNumber(first.textRange.endOffset) + 1 == getLineNumber(second.textRange.startOffset)

private fun RsBlock.isSingleLine(doc: Document, maxLength: Int): Boolean {
    // remove all leading and trailing spaces before counting lines
    val startContents = lbrace.rightSiblings.dropWhile { it is PsiWhiteSpace }.firstOrNull() ?: return false
    if (startContents.node.elementType == RBRACE) return false
    val endContents = rbrace?.leftSiblings?.dropWhile { it is PsiWhiteSpace }?.firstOrNull() ?: return false
    if (endContents.textRange.endOffset - startContents.textOffset > maxLength) return false

    return doc.getLineNumber(startContents.textOffset) == doc.getLineNumber(endContents.textRange.endOffset)
}

private fun PsiElement.getOffsetInLine(doc: Document): Int {
    val blockLine = doc.getLineNumber(textRange.startOffset)
    return leftLeaves
        .takeWhile { doc.getLineNumber(it.textRange.endOffset) == blockLine }
        .sumBy { el -> el.text.lastIndexOf('\n').let { el.text.length - max(it + 1, 0) } }
}
