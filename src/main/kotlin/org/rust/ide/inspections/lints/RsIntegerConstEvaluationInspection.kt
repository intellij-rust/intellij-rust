/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.intellij.psi.PsiElement
import com.intellij.psi.util.isAncestor
import org.rust.ide.inspections.RsProblemsHolder
import org.rust.ide.inspections.lints.RsIntegerConstEvaluationInspection.ProblemType.Error
import org.rust.ide.inspections.lints.RsIntegerConstEvaluationInspection.ProblemType.Lint
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.ancestors
import org.rust.lang.core.psi.ext.isConst
import org.rust.lang.core.types.ty.TyInteger
import org.rust.lang.core.types.type
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.addToHolder
import org.rust.lang.utils.evaluation.ConstEvaluationDiagnostic
import org.rust.lang.utils.evaluation.evaluate

abstract class RsIntegerConstEvaluationInspection : RsLintInspection() {

    override fun getLint(element: PsiElement): RsLint? {
        if (element !is RsExpr) return null
        return (problemType(element) as? Lint)?.lint
    }

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor? {
        return object : RsVisitor() {
            override fun visitExpr(o: RsExpr) {
                if (o.type !is TyInteger) return
                val parent = o.parent
                if (!isIntegerExprOwner(parent)) return

                val evaluationResult = o.evaluate(collectDiagnostics = true)

                for (diagnostic in evaluationResult.diagnostics) {
                    if (!o.isAncestor(diagnostic.expr)) continue
                    registerProblem(holder, diagnostic)
                }
            }
        }
    }

    private fun isIntegerExprOwner(element: PsiElement): Boolean {
        if (element is RsExpr && element.type is TyInteger) return false
        if (element is RsBlock && element.parent is RsBlockExpr) return false
        return true
    }

    private fun registerProblem(holder: RsProblemsHolder, diagnostic: ConstEvaluationDiagnostic) {
        if (acceptDiagnostic(diagnostic)) {
            val problemType = problemType(diagnostic.expr) ?: return
            when (problemType) {
                is Lint -> holder.registerLintProblem(diagnostic.expr, problemType.message)
                is Error -> problemType.diagnostic.addToHolder(holder)
            }
        }
    }

    protected open fun problemType(expr: RsExpr): ProblemType? {
        val owner = expr.ancestors.firstOrNull(::isIntegerExprOwner) ?: return null

        return when (owner) {
            is RsVariantDiscriminant,
            is RsTypeArgumentList,
            is RsArrayType,
            is RsArrayExpr -> Error(RsDiagnostic.ConstantEvaluationFailed(expr, "Evaluation of constant value failed"))
            is RsConstant -> {
                if (owner.isConst) {
                    Lint(RsLint.ConstErr, "Any use of this value will cause an error")
                } else {
                    Error(RsDiagnostic.ConstantEvaluationFailed(expr, "Could not evaluate static initializer"))
                }
            }
            else -> customProblemType(owner, expr)
        }
    }

    protected abstract fun customProblemType(owner: PsiElement, expr: RsExpr): ProblemType
    protected abstract fun acceptDiagnostic(diagnostic: ConstEvaluationDiagnostic): Boolean

    protected sealed class ProblemType {
        data class Lint(val lint: RsLint, val message: String) : ProblemType()
        class Error(val diagnostic: RsDiagnostic) : ProblemType()
    }
}
