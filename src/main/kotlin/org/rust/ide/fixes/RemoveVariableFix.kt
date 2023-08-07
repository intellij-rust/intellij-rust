/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.parentOfType
import org.rust.RsBundle
import org.rust.lang.core.psi.RsLetDecl
import org.rust.lang.core.psi.RsPatBinding
import org.rust.lang.core.psi.RsPatIdent
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.hasSideEffects
import org.rust.lang.core.psi.ext.topLevelPattern


/**
 * Fix that removes a variable.
 * A heuristic is used whether to also remove its expression or not.
 */
class RemoveVariableFix(
    binding: RsPatBinding,
    private val bindingName: String
) : RsQuickFixBase<RsPatBinding>(binding) {
    override fun getText() = RsBundle.message("intention.name.remove.variable", bindingName)
    override fun getFamilyName() = RsBundle.message("intention.family.name.remove.variable")

    override fun invoke(project: Project, editor: Editor?, element: RsPatBinding) {
        val patIdent = element.topLevelPattern as? RsPatIdent ?: return
        deleteVariable(patIdent)
    }
}

private fun deleteVariable(pat: RsPatIdent) {
    val decl = pat.parentOfType<RsLetDecl>() ?: return
    val expr = decl.expr

    if (expr != null && expr.hasSideEffects) {
        val factory = RsPsiFactory(expr.project)
        val newExpr = if (decl.semicolon != null) {
            factory.tryCreateExprStmtWithSemicolon(expr.text) ?: expr
        } else {
            expr
        }
        decl.replace(newExpr)
    } else {
        decl.delete()
    }
}
