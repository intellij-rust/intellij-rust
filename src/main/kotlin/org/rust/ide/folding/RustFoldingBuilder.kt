package org.rust.ide.folding

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RustFile
import java.util.*

class RustFoldingBuilder() : FoldingBuilderEx() {
    override fun getPlaceholderText(node: ASTNode): String? {
        return "{...}"
    }

    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<out FoldingDescriptor> {
        if (root !is RustFile) return emptyArray()

        val descriptors: MutableList<FoldingDescriptor> = ArrayList()

        PsiTreeUtil.findChildrenOfType(root, RustBlockExpr::class.java).forEach {
            val block = it.block
            if (block != null) {
                descriptors += FoldingDescriptor(it.node, block.textRange)
            }
        }
        PsiTreeUtil.findChildrenOfType(root, RustImplItem::class.java).forEach {
            val implBody = it.implBody
            if (implBody != null) {
                descriptors += FoldingDescriptor(it.node, implBody.textRange)
            }
        }
        PsiTreeUtil.findChildrenOfType(root, RustStructItem::class.java).forEach {
            val structDeclArgs = it.structDeclArgs
            if (structDeclArgs != null) {
                descriptors += FoldingDescriptor(it.node, structDeclArgs.textRange)
            }
        }
        PsiTreeUtil.findChildrenOfType(root, RustStructExpr::class.java).forEach {
            descriptors += FoldingDescriptor(it.node, it.structExprBody.textRange)
        }
        PsiTreeUtil.findChildrenOfType(root, RustEnumItem::class.java).forEach {
            val enumBody = it.enumBody
            descriptors += FoldingDescriptor(it.node, enumBody.textRange)
        }
        PsiTreeUtil.findChildrenOfType(root, RustEnumVariant::class.java).forEach {
            val structDeclArgs = it.enumStructArgs
            if (structDeclArgs != null) {
                descriptors += FoldingDescriptor(it.node, structDeclArgs.textRange)
            }
        }
        PsiTreeUtil.findChildrenOfType(root, RustFnItem::class.java).forEach {
            val fnBody = it.block
            if (fnBody != null) {
                descriptors += FoldingDescriptor(it.node, fnBody.textRange)
            }
        }
        PsiTreeUtil.findChildrenOfType(root, RustImplMethodMember::class.java).forEach {
            val methodBody = it.block
            if (methodBody != null) {
                descriptors += FoldingDescriptor(it.node, methodBody.textRange)
            }
        }
        PsiTreeUtil.findChildrenOfType(root, RustTraitMethodMember::class.java).forEach {
            val methodBody = it.block
            if (methodBody != null) {
                descriptors += FoldingDescriptor(it.node, methodBody.textRange)
            }
        }
        PsiTreeUtil.findChildrenOfType(root, RustModItem::class.java).forEach {
            val rbrace = it.rbrace;
            if (rbrace != null) {
                descriptors += FoldingDescriptor(it.node, TextRange(it.lbrace.textOffset, rbrace.textOffset))
            }
        }
        return descriptors.toTypedArray()
    }

    override fun isCollapsedByDefault(node: ASTNode): Boolean {
        return false
    }
}
