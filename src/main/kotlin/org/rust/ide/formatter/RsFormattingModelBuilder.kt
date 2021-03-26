/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.rust.ide.formatter.blocks.RsFmtBlock
import org.rust.ide.formatter.blocks.RsMacroArgFmtBlock
import org.rust.ide.formatter.blocks.RsMultilineStringLiteralBlock
import org.rust.lang.core.psi.RS_RAW_LITERALS
import org.rust.lang.core.psi.RS_STRING_LITERALS
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.tokenSetOf

class RsFormattingModelBuilder : FormattingModelBuilder {
    override fun getRangeAffectingIndent(file: PsiFile?, offset: Int, elementAtOffset: ASTNode?): TextRange? = null

    override fun createModel(formattingContext: FormattingContext): FormattingModel {
        val settings = formattingContext.codeStyleSettings
        val element = formattingContext.psiElement
        val ctx = RsFmtContext.create(settings)
        val block = createBlock(element.node, null, Indent.getNoneIndent(), null, ctx)
        /** / com.intellij.formatting.FormattingModelDumper.dumpFormattingModel(block, 2, System.err) // */
        return FormattingModelProvider.createFormattingModelForPsiFile(element.containingFile, block, settings)
    }

    companion object {
        private val MACRO_COMPOSITE_NODES = tokenSetOf(
            MACRO_BODY, MACRO_CASE, MACRO_PATTERN, MACRO_PATTERN_CONTENTS, MACRO_BINDING, MACRO_BINDING_GROUP,
            MACRO_EXPANSION, MACRO_EXPANSION_CONTENTS, MACRO_REFERENCE, MACRO_EXPANSION_REFERENCE_GROUP,
            MACRO_BINDING_GROUP_SEPARATOR, META_VAR_IDENTIFIER,
            MACRO_ARGUMENT, MACRO_ARGUMENT_TT
        )

        fun createBlock(
            node: ASTNode,
            alignment: Alignment?,
            indent: Indent?,
            wrap: Wrap?,
            ctx: RsFmtContext
        ): ASTBlock {
            val type = node.elementType

            if (type in MACRO_COMPOSITE_NODES) {
                return RsMacroArgFmtBlock(node, alignment, indent, wrap, ctx)
            }

            if ((type in RS_STRING_LITERALS || type in RS_RAW_LITERALS) && node.textContains('\n')) {
                return RsMultilineStringLiteralBlock(node, alignment, indent, wrap)
            }

            return RsFmtBlock(node, alignment, indent, wrap, ctx)
        }
    }
}
