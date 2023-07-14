/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.LocalQuickFix.notNullElements
import org.rust.RsBundle
import org.rust.ide.experiments.RsExperiments.MIR_BORROW_CHECK
import org.rust.ide.experiments.RsExperiments.MIR_MOVE_ANALYSIS
import org.rust.ide.fixes.AddMutableFix
import org.rust.ide.fixes.DeriveCopyFix
import org.rust.ide.fixes.InitializeWithDefaultValueFix
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.borrowCheckResult
import org.rust.lang.core.types.isImmutable
import org.rust.lang.core.types.mirBorrowCheckResult
import org.rust.lang.core.types.ty.TyReference
import org.rust.lang.core.types.type
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.addToHolder
import org.rust.openapiext.isFeatureEnabled

class RsBorrowCheckerInspection : RsLocalInspectionTool() {

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsWithMacrosInspectionVisitor() {
            override fun visitMethodCall(o: RsMethodCall) {
                val fn = o.reference.resolve() as? RsFunction ?: return
                val receiver = o.receiver
                if (checkMethodRequiresMutable(receiver, fn)) {
                    registerProblem(holder, receiver, receiver)
                }
            }

            override fun visitUnaryExpr(unaryExpr: RsUnaryExpr) {
                val expr = unaryExpr.expr?.takeIf { it.isImmutable } ?: return

                if (unaryExpr.operatorType == UnaryOperator.REF_MUT) {
                    registerProblem(holder, expr, expr)
                }
            }

            @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
            override fun visitFunction2(func: RsFunction) {
                val usedMir = visitFunctionUsingMir(func)

                val borrowCheckResult = func.borrowCheckResult ?: return

                // TODO: Remove this check when type inference is implemented for `asm!` macro calls
                if (func.descendantsWithMacrosOfType<RsAsmMacroArgument>().isNotEmpty()) return

                if (!usedMir.analyzedMoves) {
                    borrowCheckResult.usesOfMovedValue.forEach {
                        registerUseOfMovedValueProblem(holder, it.use)
                    }
                }

                if (!usedMir.analyzedUninit) {
                    borrowCheckResult.usesOfUninitializedVariable.forEach {
                        registerUseOfUninitializedVariableProblem(holder, it.use)
                    }
                }

                borrowCheckResult.moveErrors.forEach {
                    val move = it.from.element.ancestorOrSelf<RsExpr>()
                    if (move != null) registerMoveProblem(holder, move)
                }
            }

            private fun visitFunctionUsingMir(function: RsFunction): MirAnalysisStatus {
                val useMirMoveAnalysis = isFeatureEnabled(MIR_MOVE_ANALYSIS)
                val useMirBorrowChecker = isFeatureEnabled(MIR_BORROW_CHECK)
                if (!useMirBorrowChecker && !useMirMoveAnalysis) return MirAnalysisStatus.NONE
                val result = function.mirBorrowCheckResult ?: return MirAnalysisStatus.NONE
                if (useMirMoveAnalysis) {
                    for (element in result.usesOfMovedValue) {
                        if (!element.isPhysical) continue
                        val fix = DeriveCopyFix.createIfCompatible(element)
                        RsDiagnostic.UseOfMovedValueError(element, fix).addToHolder(holder)
                    }
                }
                if (useMirBorrowChecker) {
                    for (element in result.usesOfUninitializedVariable) {
                        if (!element.isPhysical) continue
                        val fix = InitializeWithDefaultValueFix.createIfCompatible(element)
                        RsDiagnostic.UseOfUninitializedVariableError(element, fix).addToHolder(holder)
                    }
                    for (element in result.moveOutWhileBorrowedValues) {
                        if (!element.isPhysical) continue
                        RsDiagnostic.MoveOutWhileBorrowedError(element).addToHolder(holder)
                    }
                }
                return MirAnalysisStatus(useMirMoveAnalysis, useMirBorrowChecker)
            }
        }

    private data class MirAnalysisStatus(
        val analyzedMoves: Boolean = false,
        val analyzedUninit: Boolean = false,
    ) {
        companion object {
            val NONE = MirAnalysisStatus()
        }
    }

    private fun registerProblem(holder: RsProblemsHolder, expr: RsExpr, nameExpr: RsExpr) {
        if (expr.isPhysical && nameExpr.isPhysical) {
            val fix = AddMutableFix.createIfCompatible(nameExpr)
            holder.registerProblem(expr, RsBundle.message("inspection.message.cannot.borrow.immutable.local.variable.as.mutable", nameExpr.text), *notNullElements(fix))
        }
    }

    private fun registerUseOfMovedValueProblem(holder: RsProblemsHolder, use: RsElement) {
        if (use.isPhysical) {
            val fix = DeriveCopyFix.createIfCompatible(use)
            holder.registerProblem(use, RsBundle.message("inspection.message.use.moved.value"), *notNullElements(fix))
        }
    }

    private fun registerMoveProblem(holder: RsProblemsHolder, element: RsElement) {
        if (element.isPhysical) {
            holder.registerProblem(element, RsBundle.message("inspection.message.cannot.move"))
        }
    }

    private fun registerUseOfUninitializedVariableProblem(holder: RsProblemsHolder, use: RsElement) {
        if (use.isPhysical) {
            val fix = InitializeWithDefaultValueFix.createIfCompatible(use)
            holder.registerProblem(use, RsBundle.message("inspection.message.use.possibly.uninitialized.variable"), *notNullElements(fix))
        }
    }

    private fun checkMethodRequiresMutable(receiver: RsExpr, fn: RsFunction): Boolean {
        val selfParameter = fn.selfParameter ?: return false
        if (receiver.isImmutable && selfParameter.mutability.isMut && selfParameter.isRef) {
            val type = receiver.type
            return type !is TyReference || !type.mutability.isMut
        }
        return false
    }
}
