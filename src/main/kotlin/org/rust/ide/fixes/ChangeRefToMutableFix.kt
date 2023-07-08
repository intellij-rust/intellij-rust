/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.RsBundle
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsUnaryExpr
import org.rust.lang.core.psi.ext.UnaryOperator
import org.rust.lang.core.psi.ext.operatorType


/**
 * Fix that converts the given immutable reference to a mutable reference.
 * @param expr An element, that represents an immutable reference.
 */
class ChangeRefToMutableFix(expr: RsUnaryExpr) : RsQuickFixBase<RsUnaryExpr>(expr) {
    override fun getText() = RsBundle.message("intention.name.change.reference.to.mutable")
    override fun getFamilyName() = text

    override fun invoke(project: Project, editor: Editor?, element: RsUnaryExpr) {
        if (element.operatorType != UnaryOperator.REF) return
        val innerExpr = element.expr ?: return

        val mutableExpr = RsPsiFactory(project).tryCreateExpression("&mut ${innerExpr.text}") ?: return
        element.replace(mutableExpr)
    }
}
