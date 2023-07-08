/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.RsBundle
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsTryExpr
import org.rust.lang.core.psi.ext.macroBody
import org.rust.lang.core.psi.ext.replaceWithExpr

class ChangeTryMacroToTryOperator(element: RsMacroCall) : RsQuickFixBase<RsMacroCall>(element) {
    override fun getText(): String = RsBundle.message("intention.name.change.try.to")
    override fun getFamilyName(): String = name

    override fun invoke(project: Project, editor: Editor?, element: RsMacroCall) {
        val factory = RsPsiFactory(project)
        val body = element.macroBody ?: return
        val expr = factory.tryCreateExpression(body) ?: return
        val tryExpr = factory.createExpression("()?") as RsTryExpr
        tryExpr.expr.replace(expr)
        element.replaceWithExpr(tryExpr)
    }
}
