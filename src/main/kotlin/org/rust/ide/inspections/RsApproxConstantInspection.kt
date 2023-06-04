/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.cargo.util.AutoInjectedCrates.CORE
import org.rust.cargo.util.AutoInjectedCrates.STD
import org.rust.ide.utils.import.stdlibAttributes
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsFile.Attributes
import org.rust.lang.core.types.ty.TyFloat
import org.rust.lang.core.types.type
import kotlin.math.*

class RsApproxConstantInspection : RsLocalInspectionTool() {
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor = object : RsWithMacrosInspectionVisitor() {
        override fun visitLitExpr(o: RsLitExpr) {
            val literal = o.kind
            if (literal is RsLiteralKind.Float) {
                val value = literal.value ?: return
                val constant = KNOWN_CONSTS.find { it.matches(value) } ?: return
                val lib = when (o.stdlibAttributes) {
                    Attributes.NONE -> STD
                    Attributes.NO_STD -> CORE
                    Attributes.NO_CORE -> return
                }
                val type = when (val type = o.type) {
                    is TyFloat -> type.name
                    else -> "f64"
                }
                val path = "$lib::$type::consts::${constant.name}"
                val fix = ReplaceWithPredefinedQuickFix(o, path)
                holder.registerProblem(o, "Approximate value of `$path` found. Consider using it directly.", fix)
            }
        }
    }

    private class ReplaceWithPredefinedQuickFix(
        element: PsiElement,
        private val path: String
    ) : LocalQuickFixOnPsiElement(element) {

        override fun getFamilyName() = "Replace with predefined constant"
        override fun getText() = "Replace with `$path`"

        override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
            val pathExpr = RsPsiFactory(project).createExpression(path)
            startElement.replace(pathExpr)
        }

    }

    private companion object {
        @JvmField
        val KNOWN_CONSTS: List<PredefinedConstant> = listOf(
            PredefinedConstant("E", Math.E, 4),
            PredefinedConstant("FRAC_1_PI", 1.0 / Math.PI, 4),
            PredefinedConstant("FRAC_1_SQRT_2", 1.0 / sqrt(2.0), 5),
            PredefinedConstant("FRAC_2_PI", 2.0 / Math.PI, 5),
            PredefinedConstant("FRAC_2_SQRT_PI", 2.0 / sqrt(Math.PI), 5),
            PredefinedConstant("FRAC_PI_2", Math.PI / 2.0, 5),
            PredefinedConstant("FRAC_PI_3", Math.PI / 3.0, 5),
            PredefinedConstant("FRAC_PI_4", Math.PI / 4.0, 5),
            PredefinedConstant("FRAC_PI_6", Math.PI / 6.0, 5),
            PredefinedConstant("FRAC_PI_8", Math.PI / 8.0, 5),
            PredefinedConstant("LN_10", ln(10.0), 5),
            PredefinedConstant("LN_2", ln(2.0), 5),
            PredefinedConstant("LOG10_E", log10(Math.E), 5),
            PredefinedConstant("LOG2_E", ln(Math.E) / ln(2.0), 5),
            PredefinedConstant("PI", Math.PI, 3),
            PredefinedConstant("SQRT_2", sqrt(2.0), 5)
        )
    }
}

data class PredefinedConstant(val name: String, val value: Double, val minDigits: Int) {
    private val accuracy: Double = 0.1.pow(minDigits.toDouble())

    fun matches(value: Double): Boolean = abs(value - this.value) < accuracy
}
