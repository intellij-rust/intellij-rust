package org.rust.ide.surround

import com.intellij.lang.surroundWith.Surrounder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustElementFactory
import org.rust.lang.core.psi.RustExpr

class DelimiterSurrounder(private val left: String,
                          private val right: String,
                          private val description: String) : Surrounder {

    override fun getTemplateDescription() = description

    override fun isApplicable(elements: Array<out PsiElement>) = elements.all { it is RustExpr }

    override fun surroundElements(project: Project, editor: Editor, elements: Array<out PsiElement>): TextRange? {
        var endOffset = 0
        for (element in elements) {
            val expr = left + element.text + right
            val exprItem = RustElementFactory.createExpression(element.project, expr) ?: return null
            val replace = element.replace(exprItem)
            endOffset = replace.textRange.endOffset
        }

        return if (endOffset != 0) TextRange.create(endOffset, endOffset) else null
    }
}
