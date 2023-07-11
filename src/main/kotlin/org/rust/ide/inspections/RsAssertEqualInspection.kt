/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.RsBundle
import org.rust.ide.fixes.RsQuickFixBase
import org.rust.lang.core.macros.expansionContext
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.EqualityOp
import org.rust.lang.core.psi.ext.bracesKind
import org.rust.lang.core.psi.ext.macroName
import org.rust.lang.core.psi.ext.operatorType
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.types.type
import org.rust.openapiext.Testmark

class RsAssertEqualInspection : RsLocalInspectionTool() {
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor = object : RsWithMacrosInspectionVisitor() {

        override fun visitMacroCall2(o: RsMacroCall) {
            if (o.macroName != "assert") return
            val assertMacroArg = o.assertMacroArgument ?: return

            val expr = assertMacroArg.expr as? RsBinaryExpr ?: return

            val (macroName, operator) = when (expr.operatorType) {
                EqualityOp.EQ -> "assert_eq" to "=="
                EqualityOp.EXCLEQ -> "assert_ne" to "!="
                else -> return
            }

            if (!isExprSuitable(expr)) return

            holder.registerProblem(
                o,
                RsBundle.message("inspection.message.assert.b.can.be.b", operator, macroName),
                SpecializedAssertQuickFix(o, macroName)
            )
        }

        private fun isExprSuitable(expr: RsBinaryExpr): Boolean {
            val leftType = expr.left.type
            val rightType = expr.right?.type ?: return false

            val lookup = ImplLookup.relativeTo(expr)
            // The `assert_eq!` macro, as opposed to `assert!`, requires both arguments to implement `core::fmt::Debug`.
            if (!lookup.isDebug(leftType).isTrue || !lookup.isDebug(rightType).isTrue) {
                Testmarks.DebugTraitIsNotImplemented.hit()
                return false
            }
            // Don't try to convert `assert!` macro into `assert_eq!/assert_ne!`
            // if expressions can't be compared at all
            if (!lookup.isPartialEq(leftType, rightType).isTrue) {
                Testmarks.PartialEqTraitIsNotImplemented.hit()
                return false
            }
            return true
        }
    }

    private class SpecializedAssertQuickFix(
        element: RsMacroCall,
        private val assertName: String
    ) : RsQuickFixBase<RsMacroCall>(element) {
        override fun getText() = RsBundle.message("intention.name.convert.to.macro", assertName)
        override fun getFamilyName(): String = text

        override fun invoke(project: Project, editor: Editor?, element: RsMacroCall) {
            val assertArg = element.assertMacroArgument!!

            val (left, right) = comparedAssertArgs(assertArg) ?: return
            val formatArgs = assertArg.formatMacroArgList
            val appendix = if (formatArgs.isNotEmpty()) {
                formatArgs.joinToString(separator = ", ", prefix = ",") { it.text }
            } else {
                ""
            }
            val newAssert = RsPsiFactory(project).createMacroCall(
                element.expansionContext,
                element.bracesKind ?: return,
                assertName,
                "${left.text}, ${right.text}$appendix"
            )
            element.replace(newAssert)
        }

        private fun comparedAssertArgs(arg: RsAssertMacroArgument): Pair<RsExpr, RsExpr>? {
            val expr = arg.expr as? RsBinaryExpr ?: return null
            val right = expr.right ?: return null
            return Pair(expr.left, right)
        }
    }

    object Testmarks {
        object PartialEqTraitIsNotImplemented : Testmark()
        object DebugTraitIsNotImplemented : Testmark()
    }
}
