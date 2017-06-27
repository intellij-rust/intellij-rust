/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.surroundWith.statement

import com.intellij.codeInsight.CodeInsightUtilBase
import com.intellij.lang.surroundWith.Surrounder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.ide.surroundWith.addStatements
import org.rust.lang.core.psi.RsBlock
import org.rust.lang.core.psi.RsExpr

sealed class RsStatementsSurrounderBase<out T : RsExpr> : Surrounder {
    protected abstract fun createTemplate(project: Project): Pair<T, RsBlock>

    abstract class SimpleBlock<out T : RsExpr> : RsStatementsSurrounderBase<T>() {
        final override fun surroundElements(project: Project, editor: Editor, elements: Array<out PsiElement>): TextRange? {
            val template = surroundWithTemplate(project, elements)
            return TextRange.from(template.firstChild.textRange.endOffset, 0)
        }
    }

    abstract class BlockWithCondition<T : RsExpr> : RsStatementsSurrounderBase<T>() {
        protected abstract fun conditionRange(expression: T): TextRange

        final override fun surroundElements(project: Project, editor: Editor, elements: Array<out PsiElement>): TextRange? {
            val template = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(
                surroundWithTemplate(project, elements)
            )

            val range = conditionRange(template)
            editor.document.deleteString(range.startOffset, range.endOffset)

            return TextRange.from(range.startOffset, 0)
        }
    }

    final override fun isApplicable(elements: Array<out PsiElement>): Boolean =
        elements.isNotEmpty()

    protected fun surroundWithTemplate(project: Project, elements: Array<out PsiElement>): T {
        require(elements.isNotEmpty())
        val container = requireNotNull(elements[0].parent)

        var (template, block) = createTemplate(project)
        block.addStatements(elements)
        template = template.javaClass.cast(
            container.addBefore(template, elements[0])
        )

        container.deleteChildRange(elements.first(), elements.last())
        return template
    }
}
