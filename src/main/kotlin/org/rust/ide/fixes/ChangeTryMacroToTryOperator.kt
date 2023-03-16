/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsTryExpr
import org.rust.lang.core.psi.ext.macroBody
import org.rust.lang.core.psi.ext.replaceWithExpr

class ChangeTryMacroToTryOperator : LocalQuickFix {
    override fun getName() = "Change try! to ?"

    override fun getFamilyName() = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val macro = descriptor.psiElement as? RsMacroCall ?: return
        val factory = RsPsiFactory(project)
        val body = macro.macroBody ?: return
        val expr = factory.tryCreateExpression(body) ?: return
        val tryExpr = factory.createExpression("()?") as RsTryExpr
        tryExpr.expr.replace(expr)
        macro.replaceWithExpr(tryExpr)
    }
}
