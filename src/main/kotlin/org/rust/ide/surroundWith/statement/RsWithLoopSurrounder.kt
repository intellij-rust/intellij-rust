/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.surroundWith.statement

import com.intellij.openapi.project.Project
import org.rust.RsBundle
import org.rust.lang.core.psi.RsBlock
import org.rust.lang.core.psi.RsLoopExpr
import org.rust.lang.core.psi.RsPsiFactory

class RsWithLoopSurrounder : RsStatementsSurrounderBase.SimpleBlock<RsLoopExpr>() {

    @Suppress("DialogTitleCapitalization")
    override fun getTemplateDescription(): String = RsBundle.message("action.loop.text")

    override fun createTemplate(project: Project): Pair<RsLoopExpr, RsBlock> {
        val l = RsPsiFactory(project).createExpression("loop {}") as RsLoopExpr
        return l to l.block!!
    }

}
