package org.rust.ide.formatter

import com.intellij.formatting.FormattingModel
import com.intellij.formatting.FormattingModelBuilder
import com.intellij.formatting.FormattingModelProvider
import com.intellij.formatting.Indent
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import org.rust.ide.formatter.blocks.AbstractRustFmtBlock

class RustFormattingModelBuilder : FormattingModelBuilder {
    override fun getRangeAffectingIndent(file: PsiFile?, offset: Int, elementAtOffset: ASTNode?): TextRange? = null

    override fun createModel(element: PsiElement, settings: CodeStyleSettings): FormattingModel {
        val ctx = RustFmtContext.create(settings)
        val block = AbstractRustFmtBlock.createBlock(element.node, null, Indent.getNoneIndent(), null, ctx)
        /** / com.intellij.formatting.FormattingModelDumper.dumpFormattingModel(block, 2, System.err) // */
        return FormattingModelProvider.createFormattingModelForPsiFile(element.containingFile, block, settings)
    }
}
