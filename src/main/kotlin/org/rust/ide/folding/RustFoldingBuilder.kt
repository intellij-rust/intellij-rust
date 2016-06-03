package org.rust.ide.folding

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RustFile
import org.rust.lang.core.psi.util.descendentsOfType
import java.util.*

class RustFoldingBuilder() : FoldingBuilderEx(), DumbAware {
    override fun getPlaceholderText(node: ASTNode): String = "{...}"

    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<out FoldingDescriptor> {
        if (root !is RustFile) return emptyArray()

        val descriptors: MutableList<FoldingDescriptor> = ArrayList()

        root.descendentsOfType<RustBlockExprElement>().forEach {
            val block = it.block
            if (block != null) {
                descriptors += FoldingDescriptor(it.node, block.textRange)
            }
        }
        root.descendentsOfType<RustImplItemElement>().forEach {
            val implBody = it.implBody
            if (implBody != null) {
                descriptors += FoldingDescriptor(it.node, implBody.textRange)
            }
        }
        root.descendentsOfType<RustStructItemElement>().forEach {
            val structDeclArgs = it.structDeclArgs
            if (structDeclArgs != null) {
                descriptors += FoldingDescriptor(it.node, structDeclArgs.textRange)
            }
        }
        root.descendentsOfType<RustStructExprElement>().forEach {
            descriptors += FoldingDescriptor(it.node, it.structExprBody.textRange)
        }
        root.descendentsOfType<RustEnumItemElement>().forEach {
            val enumBody = it.enumBody
            descriptors += FoldingDescriptor(it.node, enumBody.textRange)
        }
        root.descendentsOfType<RustTraitItemElement>().forEach {
            val traitBody = it.traitBody
            descriptors += FoldingDescriptor(it.node, traitBody.textRange)
        }
        root.descendentsOfType<RustEnumVariantElement>().forEach {
            val structDeclArgs = it.enumStructArgs
            if (structDeclArgs != null) {
                descriptors += FoldingDescriptor(it.node, structDeclArgs.textRange)
            }
        }
        root.descendentsOfType<RustFnItemElement>().forEach {
            val fnBody = it.block
            if (fnBody != null) {
                descriptors += FoldingDescriptor(it.node, fnBody.textRange)
            }
        }
        root.descendentsOfType<RustImplMethodMemberElement>().forEach {
            val methodBody = it.block
            if (methodBody != null) {
                descriptors += FoldingDescriptor(it.node, methodBody.textRange)
            }
        }
        root.descendentsOfType<RustTraitMethodMemberElement>().forEach {
            val methodBody = it.block
            if (methodBody != null) {
                descriptors += FoldingDescriptor(it.node, methodBody.textRange)
            }
        }
        root.descendentsOfType<RustModItemElement>().forEach {
            val rbrace = it.rbrace;
            if (rbrace != null) {
                descriptors += FoldingDescriptor(it.node, TextRange(it.lbrace.textOffset, rbrace.textOffset + 1))
            }
        }
        return descriptors.toTypedArray()
    }

    override fun isCollapsedByDefault(node: ASTNode): Boolean = false
}
