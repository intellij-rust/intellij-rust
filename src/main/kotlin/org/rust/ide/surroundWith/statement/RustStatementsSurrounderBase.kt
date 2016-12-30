package org.rust.ide.surroundWith.statement

import com.intellij.codeInsight.CodeInsightUtilBase
import com.intellij.lang.surroundWith.Surrounder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.ide.surroundWith.addStatements
import org.rust.lang.core.psi.RustBlockElement

sealed class RustStatementsSurrounderBase : Surrounder {
    protected abstract fun createTemplate(project: Project): Pair<PsiElement, RustBlockElement>

    abstract class SimpleBlock : RustStatementsSurrounderBase() {
        final override fun surroundElements(project: Project, editor: Editor, elements: Array<out PsiElement>): TextRange? {
            val template = surroundWithTemplate(project, elements)
            return TextRange.from(template.firstChild.textRange.endOffset, 0)
        }
    }

    abstract class BlockWithCondition : RustStatementsSurrounderBase() {
        protected abstract fun getExprForRemove(expression: PsiElement): PsiElement?

        final override fun surroundElements(project: Project, editor: Editor, elements: Array<out PsiElement>): TextRange? {
            val template = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(
                surroundWithTemplate(project, elements)
            )

            val removeIt = getExprForRemove(template)
            val conditionTextRange = checkNotNull(removeIt).textRange
            editor.document.deleteString(conditionTextRange.startOffset, conditionTextRange.endOffset)

            return TextRange.from(conditionTextRange.startOffset, 0)
        }
    }

    final override fun isApplicable(elements: Array<out PsiElement>): Boolean =
        elements.isNotEmpty()

    protected fun surroundWithTemplate(project: Project, elements: Array<out PsiElement>): PsiElement {
        require(elements.isNotEmpty())
        val container = requireNotNull(elements[0].parent)

        var (template, block) = createTemplate(project)
        block.addStatements(elements)
        template = container.addBefore(template, elements[0])

        container.deleteChildRange(elements.first(), elements.last())
        return template
    }
}
