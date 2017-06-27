/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsLitExpr
import org.rust.lang.core.psi.RsLiteralKind
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.kind

class RsApproxConstantInspection : RsLocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : RsVisitor() {
        override fun visitLitExpr(o: RsLitExpr) {
            val literal = o.kind
            if (literal is RsLiteralKind.Float) {
                val value = literal.value ?: return
                val constant = KNOWN_CONSTS.find { it.matches(value) } ?: return
                holder.registerProblem(o, literal.suffix ?: "f64", constant)
            }
        }
    }

    private companion object {
        val KNOWN_CONSTS = listOf(
            PredefinedConstant("E", Math.E, 4),
            PredefinedConstant("FRAC_1_PI", 1.0 / Math.PI, 4),
            PredefinedConstant("FRAC_1_SQRT_2", 1.0 / Math.sqrt(2.0), 5),
            PredefinedConstant("FRAC_2_PI", 2.0 / Math.PI, 5),
            PredefinedConstant("FRAC_2_SQRT_PI", 2.0 / Math.sqrt(Math.PI), 5),
            PredefinedConstant("FRAC_PI_2", Math.PI / 2.0, 5),
            PredefinedConstant("FRAC_PI_3", Math.PI / 3.0, 5),
            PredefinedConstant("FRAC_PI_4", Math.PI / 4.0, 5),
            PredefinedConstant("FRAC_PI_6", Math.PI / 6.0, 5),
            PredefinedConstant("FRAC_PI_8", Math.PI / 8.0, 5),
            PredefinedConstant("LN_10", Math.log(10.0), 5),
            PredefinedConstant("LN_2", Math.log(2.0), 5),
            PredefinedConstant("LOG10_E", Math.log10(Math.E), 5),
            PredefinedConstant("LOG2_E", Math.log(Math.E) / Math.log(2.0), 5),
            PredefinedConstant("PI", Math.PI, 3),
            PredefinedConstant("SQRT_2", Math.sqrt(2.0), 5)
        )
    }
}

data class PredefinedConstant(val name: String, val value: Double, val minDigits: Int) {
    val accuracy = Math.pow(0.1, minDigits.toDouble())

    fun matches(value: Double) = Math.abs(value - this.value) < accuracy
}

private fun ProblemsHolder.registerProblem(element: PsiElement, type: String, constant: PredefinedConstant) {
    registerProblem(element, "Approximate value of `std::$type::consts::${constant.name}` found. Consider using it directly.")
}
