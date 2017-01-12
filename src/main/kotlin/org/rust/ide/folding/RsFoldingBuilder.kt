package org.rust.ide.folding

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RsFile
import java.util.*

class RsFoldingBuilder() : FoldingBuilderEx(), DumbAware {
    override fun getPlaceholderText(node: ASTNode): String = if (node.psi is PsiComment) "/* ... */" else "{...}"

    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<out FoldingDescriptor> {
        if (root !is RsFile) return emptyArray()

        val descriptors: MutableList<FoldingDescriptor> = ArrayList()
        val visitor = FoldingVisitor(descriptors)
        PsiTreeUtil.processElements(root) { it.accept(visitor); true }

        return descriptors.toTypedArray()
    }

    private class FoldingVisitor(private val descriptors: MutableList<FoldingDescriptor>) : RsVisitor() {

        override fun visitStructExprBody(o: RsStructExprBody) = fold(o)

        override fun visitEnumBody(o: RsEnumBody) = fold(o)

        override fun visitBlockFields(o: RsBlockFields) = fold(o)

        override fun visitBlock(o: RsBlock) = fold(o)

        override fun visitMatchBody(o: RsMatchBody) = fold(o)

        override fun visitUseGlobList(o: RsUseGlobList) = fold(o)

        override fun visitTraitItem(o: RsTraitItem) = foldBetween(o, o.lbrace, o.rbrace)

        override fun visitModItem(o: RsModItem) = foldBetween(o, o.lbrace, o.rbrace)

        override fun visitMacroArg(o: RsMacroArg) = foldBetween(o, o.lbrace, o.rbrace)

        override fun visitImplItem(o: RsImplItem) = foldBetween(o, o.lbrace, o.rbrace)

        override fun visitComment(comment: PsiComment) {
            if (comment.tokenType == RsTokenElementTypes.BLOCK_COMMENT) {
                fold(comment)
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
    }

    override fun isCollapsedByDefault(node: ASTNode): Boolean = false
}
