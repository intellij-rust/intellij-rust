/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsLitExpr
import org.rust.lang.core.psi.RsUnaryExpr
import org.rust.lang.core.types.type
import org.rust.lang.utils.evaluation.ConstEvaluationDiagnostic

class RsIntegerOverflowInspection : RsIntegerConstEvaluationInspection() {

    override fun problemType(expr: RsExpr): ProblemType? {
        return if (expr is RsLitExpr || expr is RsUnaryExpr && expr.expr is RsLitExpr) {
            ProblemType.Lint(RsLint.OverflowingLiterals, "Literal out of range for ${expr.type}")
        } else {
            super.problemType(expr)
        }
    }

    override fun customProblemType(owner: PsiElement, expr: RsExpr): ProblemType {
        return ProblemType.Lint(RsLint.ArithmeticOverflow, "This arithmetic operation will overflow")
    }

    override fun acceptDiagnostic(diagnostic: ConstEvaluationDiagnostic): Boolean {
        return diagnostic is ConstEvaluationDiagnostic.IntegerOverflow
    }
}
