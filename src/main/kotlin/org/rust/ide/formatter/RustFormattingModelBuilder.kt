package org.rust.ide.formatter

import com.intellij.formatting.FormattingModel
import com.intellij.formatting.FormattingModelBuilder
import com.intellij.formatting.FormattingModelProvider
import com.intellij.formatting.SpacingBuilder
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import org.rust.lang.RustLanguage

class RustFormattingModelBuilder : FormattingModelBuilder {
    override fun getRangeAffectingIndent(file: PsiFile?, offset: Int, elementAtOffset: ASTNode?): TextRange? = null

    override fun createModel(element: PsiElement, settings: CodeStyleSettings): FormattingModel {
        val block = RustBlock(element.node, settings, createSpacingBuilder(settings))
        return FormattingModelProvider.createFormattingModelForPsiFile(element.containingFile, block, settings)
    }

    protected fun createSpacingBuilder(settings: CodeStyleSettings): SpacingBuilder {
        val rustSettings = settings.getCustomSettings(RustCodeStyleSettings::class.java)
        val commonSettings = settings.getCommonSettings(RustLanguage)
        return SpacingBuilder(commonSettings)
    }
}
