package org.rust.lang.formatter

import com.intellij.formatting.FormattingModel
import com.intellij.formatting.FormattingModelBuilder
import com.intellij.formatting.FormattingModelProvider
import com.intellij.formatting.Indent
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettings

class RustFormattingModelBuilder : FormattingModelBuilder {
    override fun getRangeAffectingIndent(file: PsiFile?, offset: Int, elementAtOffset: ASTNode?): TextRange? = null

    override fun createModel(element: PsiElement, settings: CodeStyleSettings): FormattingModel {
        val block = RustFormattingBlock(element.node, Indent.getNoneIndent())
        return FormattingModelProvider.createFormattingModelForPsiFile(element.containingFile, block, settings)
    }

}

