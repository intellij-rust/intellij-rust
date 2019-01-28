/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import org.rust.ide.utils.skipParenExprDown
import org.rust.lang.core.dfa.*
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.endOffsetInParent
import org.rust.lang.core.types.dataFlowAnalysisResult
import org.rust.lang.core.types.ty.TyBool
import org.rust.lang.core.types.ty.TyInteger
import org.rust.lang.core.types.type

class RsConstantConditionInspection : RsLocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitFunction(o: RsFunction) {
                if (o.block == null) return
                val (runnerResult, result) = o.dataFlowAnalysisResult
                when (runnerResult) {
                    DfaRunnerResult.OK -> createDescription(holder, result)
                    else -> {
                        val error = result.exception?.message?.let { ": $it" } ?: ""
                        holder.registerProblem(
                            o,
                            TextRange.create(o.fn.startOffsetInParent, o.identifier.endOffsetInParent),
                            "Couldn't analyze function $runnerResult$error"
                        )
                    }
                }
            }

            override fun visitIfExpr(o: RsIfExpr) {
                val condition = o.condition?.skipParenExprDown() as? RsLitExpr ?: return
                val boolLit = condition.boolLiteral ?: return
                holder.registerProblem(condition, "Condition is always `${boolLit.text}`")
            }

            override fun visitWhileExpr(o: RsWhileExpr) {
                val condition = o.condition?.skipParenExprDown() as? RsLitExpr ?: return
                if (condition.textMatches("false")) {
                    holder.registerProblem(condition, "Condition is always `false`")
                }
            }
        }
}

private fun createDescription(holder: ProblemsHolder, result: DfaResult) = with(result) {
    trueSet.forEach { registerConstantBoolean(holder, it, true) }
    falseSet.forEach { registerConstantBoolean(holder, it, false) }
    overflowExpressions.forEach { registerOverflow(holder, it) }
    registerError(holder, exception)
    //dor debug
    addStates(holder, resultState)
}

private fun addStates(holder: ProblemsHolder, state: DfaMemoryState?) {
    if (state == null) return
    state.entries.forEach {
        when (it.key.type) {
            is TyBool, is TyInteger -> holder.registerProblem(it.key, "Value is `${it.value}`", ProblemHighlightType.WEAK_WARNING)
        }
    }
}

private fun registerConstantBoolean(holder: ProblemsHolder, expr: RsExpr, value: Boolean) {
    holder.registerProblem(expr, "Condition `${expr.text}` is always `$value`")
}

private fun registerOverflow(holder: ProblemsHolder, expr: RsExpr) = when (expr) {
    is RsLitExpr -> holder.registerProblem(expr, "Literal out of range for ${expr.type}")
    else -> holder.registerProblem(expr, "Expression `${expr.text}` is overflow")
}

private fun registerError(holder: ProblemsHolder, error: DfaException?) = when (error) {
    is DfaDivisionByZeroException -> holder.registerProblem(error.expr, "Division by zero", ProblemHighlightType.ERROR)
    else -> {
    }
}
