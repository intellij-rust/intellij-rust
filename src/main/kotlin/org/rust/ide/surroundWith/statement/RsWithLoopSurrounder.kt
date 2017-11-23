/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.surroundWith.statement

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RsBlock
import org.rust.lang.core.psi.RsLoopExpr
import org.rust.lang.core.psi.RsPsiFactory

class RsWithLoopSurrounder : RsStatementsSurrounderBase.SimpleBlock<RsLoopExpr>() {
    override fun getTemplateDescription(): String = "loop { }"

    override fun createTemplate(project: Project): Pair<RsLoopExpr, RsBlock> {
        val l = RsPsiFactory(project).createExpression("loop {}") as RsLoopExpr
        return l to l.block!!
    }

}
