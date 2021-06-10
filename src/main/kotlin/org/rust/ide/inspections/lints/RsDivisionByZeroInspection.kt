/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.utils.evaluation.ConstEvaluationDiagnostic

class RsDivisionByZeroInspection : RsIntegerConstEvaluationInspection() {

    override fun customProblemType(owner: PsiElement, expr: RsExpr): ProblemType {
        return ProblemType.Lint(RsLint.UnconditionalPanic, "Attempt to divide by zero")
    }

    override fun acceptDiagnostic(diagnostic: ConstEvaluationDiagnostic): Boolean {
        return diagnostic is ConstEvaluationDiagnostic.DivisionByZero
    }
}
