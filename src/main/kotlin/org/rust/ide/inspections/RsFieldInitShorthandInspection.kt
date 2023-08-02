/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemHighlightType
import org.rust.RsBundle
import org.rust.ide.fixes.ChangeToFieldShorthandFix
import org.rust.lang.core.psi.RsPathExpr
import org.rust.lang.core.psi.RsStructLiteralField
import org.rust.lang.core.psi.RsVisitor

class RsFieldInitShorthandInspection : RsLocalInspectionTool() {
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor = object : RsWithMacrosInspectionVisitor() {
        override fun visitStructLiteralField(o: RsStructLiteralField) {
            val init = o.expr ?: return
            if (!(init is RsPathExpr && init.text == o.identifier?.text)) return
            holder.registerProblem(
                o,
                RsBundle.message("inspection.message.expression.can.be.simplified"),
                ProblemHighlightType.WEAK_WARNING,
                ChangeToFieldShorthandFix(o)
            )
        }
    }

    override val isSyntaxOnly: Boolean = true
}
