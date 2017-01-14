package org.rust.ide.formatter

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import org.rust.ide.formatter.blocks.RsFmtBlock
import org.rust.ide.formatter.blocks.RsMacroArgFmtBlock
import org.rust.lang.core.psi.RsElementTypes.MACRO_ARG

class RsFormattingModelBuilder : FormattingModelBuilder {
    override fun getRangeAffectingIndent(file: PsiFile?, offset: Int, elementAtOffset: ASTNode?): TextRange? = null

    override fun createModel(element: PsiElement, settings: CodeStyleSettings): FormattingModel {
        val ctx = RsFmtContext.create(settings)
        val block = createBlock(element.node, null, Indent.getNoneIndent(), null, ctx)
        /** / com.intellij.formatting.FormattingModelDumper.dumpFormattingModel(block, 2, System.err) // */
        return FormattingModelProvider.createFormattingModelForPsiFile(element.containingFile, block, settings)
    }

    companion object {
        fun createBlock(
            node: ASTNode,
            alignment: Alignment?,
            indent: Indent?,
            wrap: Wrap?,
            ctx: RsFmtContext
        ): ASTBlock = when (node.elementType) {
            MACRO_ARG -> RsMacroArgFmtBlock(node, alignment, indent, wrap, ctx)
            else -> RsFmtBlock(node, alignment, indent, wrap, ctx)
        }
    }
}
