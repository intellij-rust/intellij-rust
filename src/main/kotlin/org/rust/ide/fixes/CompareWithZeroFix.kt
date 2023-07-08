/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.RsBundle
import org.rust.lang.core.psi.RsCastExpr
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.types.ty.TyNumeric
import org.rust.lang.core.types.type

class CompareWithZeroFix private constructor(expr: RsCastExpr) : RsQuickFixBase<RsCastExpr>(expr) {
    override fun getFamilyName(): String = RsBundle.message("intention.family.name.compare.with.zero")

    override fun getText(): String = familyName

    override fun invoke(project: Project, editor: Editor?, element: RsCastExpr) {
        element.replace(RsPsiFactory(project).createExpression("${element.expr.text} != 0"))
    }

    companion object {
        fun createIfCompatible(expression: RsCastExpr): CompareWithZeroFix? {
            return if (expression.expr.type is TyNumeric) CompareWithZeroFix(expression) else null
        }
    }
}
