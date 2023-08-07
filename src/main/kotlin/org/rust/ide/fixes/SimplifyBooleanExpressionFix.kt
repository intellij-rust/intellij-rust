/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.RsBundle
import org.rust.ide.utils.BooleanExprSimplifier
import org.rust.ide.utils.isPure
import org.rust.lang.core.psi.RsExpr

class SimplifyBooleanExpressionFix(expr: RsExpr) : RsQuickFixBase<RsExpr>(expr) {
    override fun getText(): String = RsBundle.message("intention.name.simplify.boolean.expression")
    override fun getFamilyName() = text

    override fun invoke(project: Project, editor: Editor?, element: RsExpr) {
        if (element.isPure() == true && BooleanExprSimplifier.canBeSimplified(element)) {
            val simplified = BooleanExprSimplifier(project).simplify(element) ?: return
            element.replace(simplified)
        }
    }
}
