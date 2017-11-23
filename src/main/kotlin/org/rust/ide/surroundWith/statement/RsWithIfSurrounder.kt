/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.surroundWith.statement

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.rust.lang.core.psi.RsBlock
import org.rust.lang.core.psi.RsIfExpr
import org.rust.lang.core.psi.RsPsiFactory

class RsWithIfSurrounder : RsStatementsSurrounderBase.BlockWithCondition<RsIfExpr>() {
    override fun getTemplateDescription(): String = "if { }"

    override fun createTemplate(project: Project): Pair<RsIfExpr, RsBlock> {
        val i = RsPsiFactory(project).createExpression("if a {}") as RsIfExpr
        return i to i.block!!
    }

    override fun conditionRange(expression: RsIfExpr): TextRange =
        expression.condition!!.textRange

}
