/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.LocalQuickFix
import org.rust.ide.annotator.fixes.AddMutableFix
import org.rust.ide.inspections.fixes.DeriveCopyFix
import org.rust.ide.inspections.fixes.InitializeWithDefaultValueFix
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.borrowCheckResult
import org.rust.lang.core.types.isImmutable
import org.rust.lang.core.types.ty.TyReference
import org.rust.lang.core.types.type

class RsBorrowCheckerInspection : RsLocalInspectionTool() {

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsVisitor() {
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

            override fun visitFunction(func: RsFunction) {
                val borrowCheckResult = func.borrowCheckResult ?: return

                // TODO: Remove this check when type inference is implemented for `asm!` macro calls
                if (func.descendantsWithMacrosOfType<RsAsmMacroArgument>().isNotEmpty()) return

                borrowCheckResult.usesOfMovedValue.forEach {
                    registerUseOfMovedValueProblem(holder, it.use)
                }
                borrowCheckResult.usesOfUninitializedVariable.forEach {
                    registerUseOfUninitializedVariableProblem(holder, it.use)
                }
                borrowCheckResult.moveErrors.forEach {
                    val move = it.from.element.ancestorOrSelf<RsExpr>()
                    if (move != null) registerMoveProblem(holder, move)
                }
            }
        }

    private fun registerProblem(holder: RsProblemsHolder, expr: RsExpr, nameExpr: RsExpr) {
        if (expr.isPhysical && nameExpr.isPhysical) {
            val fix = AddMutableFix.createIfCompatible(nameExpr)
            holder.registerProblem(expr, "Cannot borrow immutable local variable `${nameExpr.text}` as mutable", *notNullElements(fix))
        }
    }

    private fun registerUseOfMovedValueProblem(holder: RsProblemsHolder, use: RsElement) {
        if (use.isPhysical) {
            val fix = DeriveCopyFix.createIfCompatible(use)
            holder.registerProblem(use, "Use of moved value", *notNullElements(fix))
        }
    }

    private fun registerMoveProblem(holder: RsProblemsHolder, element: RsElement) {
        if (element.isPhysical) {
            holder.registerProblem(element, "Cannot move")
        }
    }

    private fun registerUseOfUninitializedVariableProblem(holder: RsProblemsHolder, use: RsElement) {
        if (use.isPhysical) {
            val fix = InitializeWithDefaultValueFix.createIfCompatible(use)
            holder.registerProblem(use, "Use of possibly uninitialized variable", *notNullElements(fix))
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

// BACKCOMPAT: 2022.3. Replace with LocalQuickFix.notNullElements
private fun notNullElements(fix: LocalQuickFix?): Array<LocalQuickFix> {
    return if (fix == null) LocalQuickFix.EMPTY_ARRAY else arrayOf(fix)
}
