/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsLitExpr
import org.rust.lang.core.psi.RsUnaryExpr
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.UnaryOperator
import org.rust.lang.core.psi.ext.operatorType
import org.rust.lang.core.types.consts.asLong
import org.rust.lang.core.types.ty.TyInteger
import org.rust.lang.core.types.type
import org.rust.lang.utils.evaluation.ConstExpr
import org.rust.lang.utils.evaluation.toConstExpr
import org.rust.lang.utils.evaluation.validValuesRange

class RsIntegerOverflowInspection : RsLintInspection() {
    override fun getLint(element: PsiElement): RsLint = RsLint.OverflowingLiterals

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : RsVisitor() {
            override fun visitLitExpr(o: RsLitExpr) {
                val type = o.type
                if (type !is TyInteger) return

                val parent = o.parent
                val expr: RsExpr = if (parent is RsUnaryExpr && parent.operatorType == UnaryOperator.MINUS) {
                    parent
                } else {
                    o
                }

                val value = evaluate(expr.toConstExpr()) ?: return
                if (overflows(value, type)) {
                    holder.registerProblem(expr, "literal out of range for $type")
                }
            }
        }

    private fun evaluate(expr: ConstExpr<*>?): Long? = when {
        expr is ConstExpr.Constant -> expr.const.asLong()
        expr is ConstExpr.Value.Integer -> expr.value
        expr is ConstExpr.Unary && expr.operator == UnaryOperator.MINUS -> evaluate(expr.expr)?.let { -it }
        else -> null
    }

    private fun overflows(value: Long, type: TyInteger): Boolean = value !in type.validValuesRange
}
