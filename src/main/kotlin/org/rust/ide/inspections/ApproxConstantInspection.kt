package org.rust.ide.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.rust.lang.core.psi.RustLitExpr
import org.rust.lang.core.psi.RustVisitor

class ApproxConstantInspection : LocalInspectionTool() {

    override fun getGroupDisplayName() = "Rust"
    override fun getDisplayName() = "Approximate Constants"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : RustVisitor() {
            override fun visitLitExpr(o: RustLitExpr) {
                analyzeLiteral(o.floatLiteral ?: return, holder)
            }
        }
    }

    private fun analyzeLiteral(psiElement: PsiElement, holder: ProblemsHolder) {
        val text = psiElement.text
            .filter { it != '_' }
            .removeSuffix("f32")
            .removeSuffix("f64")

        // Parse the float literal and skip inspection on failure
        val value = try {
            text.toDouble()
        } catch (e: NumberFormatException) {
            return
        }

        val constant = KNOWN_CONSTS.find { it.matches(value) } ?: return

        val type = if (psiElement.text.endsWith("f32")) "f32" else "f64"

        holder.registerProblem(psiElement, type, constant)
    }

    companion object {
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

    fun matches(value: Double): Boolean {
        return Math.abs(value - this.value) < accuracy
    }
}

private fun ProblemsHolder.registerProblem(element: PsiElement, type: String, constant: PredefinedConstant) {
    registerProblem(element, "Approximate value of `std::$type::consts::${constant.name}` found. Consider using it directly.")
}
