package org.rust.ide.folding

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RustFile
import org.rust.lang.core.psi.visitors.RustRecursiveElementVisitor
import java.util.*

class RustFoldingBuilder() : FoldingBuilderEx(), DumbAware {
    override fun getPlaceholderText(node: ASTNode): String = if (node.psi is PsiComment) "/* ... */" else "{...}"

    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<out FoldingDescriptor> {
        if (root !is RustFile) return emptyArray()

        val descriptors: MutableList<FoldingDescriptor> = ArrayList()

        root.accept(object : RustRecursiveElementVisitor() {
            override fun visitBlockExpr(o: RustBlockExprElement) {
                super.visitBlockExpr(o)

                val block = o.block
                if (block != null) {
                    descriptors += FoldingDescriptor(o.node, block.textRange)
                }
            }

            override fun visitImplItem(o: RustImplItemElement) {
                super.visitImplItem(o)
                foldBetween(o, o.lbrace, o.rbrace)
            }

            override fun visitStructItem(o: RustStructItemElement) {
                super.visitStructItem(o)

                val blockFields = o.blockFields
                if (blockFields != null) {
                    descriptors += FoldingDescriptor(o.node, blockFields.textRange)
                }
            }

            override fun visitStructExpr(o: RustStructExprElement) {
                super.visitStructExpr(o)

                descriptors += FoldingDescriptor(o.node, o.structExprBody.textRange)
            }

            override fun visitEnumItem(o: RustEnumItemElement) {
                super.visitEnumItem(o)

                val enumBody = o.enumBody
                descriptors += FoldingDescriptor(o.node, enumBody.textRange)
            }

            override fun visitTraitItem(o: RustTraitItemElement) {
                super.visitTraitItem(o)
                foldBetween(o, o.lbrace, o.rbrace)
            }

            override fun visitEnumVariant(o: RustEnumVariantElement) {
                super.visitEnumVariant(o)

                val blockFields = o.blockFields
                if (blockFields != null) {
                    descriptors += FoldingDescriptor(o.node, blockFields.textRange)
                }
            }

            override fun visitFn(o: RustFnElement) {
                super.visitFn(o)

                val fnBody = o.block
                if (fnBody != null) {
                    descriptors += FoldingDescriptor(o.node, fnBody.textRange)
                }
            }

            override fun visitModItem(o: RustModItemElement) {
                super.visitModItem(o)
                foldBetween(o, o.lbrace, o.rbrace)
            }

            override fun visitMatchExpr(o: RustMatchExprElement) {
                super.visitMatchExpr(o)

                val body = o.matchBody
                if (body != null) {
                    descriptors += FoldingDescriptor(o.node, body.textRange)
                }
            }

            override fun visitMacroArg(o: RustMacroArgElement) {
                super.visitMacroArg(o)
                foldBetween(o, o.lbrace, o.rbrace)
            }

            override fun visitUseGlobList(o: RustUseGlobListElement) {
                super.visitUseGlobList(o)

                descriptors += FoldingDescriptor(o.node, o.textRange)
            }

            override fun visitComment(comment: PsiComment) {
                super.visitComment(comment)

                if (comment.tokenType == RustTokenElementTypes.BLOCK_COMMENT) {
                    descriptors += FoldingDescriptor(comment.node, comment.textRange)
                }
            }

            private fun foldBetween(element: PsiElement, left: PsiElement?, right: PsiElement?) {
                if (left != null && right != null) {
                    descriptors += FoldingDescriptor(element.node, TextRange(left.textOffset, right.textOffset + 1))
                }
            }
        })

        return descriptors.toTypedArray()
    }

    override fun isCollapsedByDefault(node: ASTNode): Boolean = false
}
