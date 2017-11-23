/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.surroundWith.statement

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.rust.lang.core.psi.RsBlock
import org.rust.lang.core.psi.RsWhileExpr
import org.rust.lang.core.psi.RsPsiFactory

class RsWithWhileSurrounder : RsStatementsSurrounderBase.BlockWithCondition<RsWhileExpr>() {
    override fun getTemplateDescription(): String = "while { }"

    override fun createTemplate(project: Project): Pair<RsWhileExpr, RsBlock> {
        val w = RsPsiFactory(project).createExpression("while a {}") as RsWhileExpr
        return w to w.block!!
    }

    override fun conditionRange(expression: RsWhileExpr): TextRange =
        expression.condition!!.textRange
}
