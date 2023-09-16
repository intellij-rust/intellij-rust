/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.surroundWith.statement

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.rust.RsBundle
import org.rust.lang.core.psi.RsBlock
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsWhileExpr

class RsWithWhileSurrounder : RsStatementsSurrounderBase.BlockWithCondition<RsWhileExpr>() {

    @Suppress("DialogTitleCapitalization")
    override fun getTemplateDescription(): String = RsBundle.message("action.while.text")

    override fun createTemplate(project: Project): Pair<RsWhileExpr, RsBlock> {
        val w = RsPsiFactory(project).createExpression("while a {}") as RsWhileExpr
        return w to w.block!!
    }

    override fun conditionRange(expression: RsWhileExpr): TextRange =
        expression.condition!!.textRange
}
