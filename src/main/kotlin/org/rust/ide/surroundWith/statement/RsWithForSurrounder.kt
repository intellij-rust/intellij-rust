/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.surroundWith.statement

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.rust.lang.core.psi.RsBlock
import org.rust.lang.core.psi.RsForExpr
import org.rust.lang.core.psi.RsPsiFactory

class RsWithForSurrounder : RsStatementsSurrounderBase.BlockWithCondition<RsForExpr>() {
    override fun getTemplateDescription(): String = "for { }"

    override fun createTemplate(project: Project): Pair<RsForExpr, RsBlock> {
        val f = RsPsiFactory(project).createExpression("for a in b {}") as RsForExpr
        return f to f.block!!
    }

    override fun conditionRange(expression: RsForExpr): TextRange =
        TextRange(
            expression.pat!!.textRange.startOffset,
            expression.expr!!.textRange.endOffset
        )
}
