/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.EqualityOp
import org.rust.lang.core.psi.ext.macroName
import org.rust.lang.core.psi.ext.operatorType

class RsAssertEqualInspection : RsLocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : RsVisitor() {

        override fun visitMacroCall(o: RsMacroCall) {
            val macroName = o.macroName?.text ?: return
            val assertMacroArg = o.assertMacroArgument ?: return
            if (macroName != "assert") return

            val expr = assertMacroArg.expr as? RsBinaryExpr ?: return
            when (expr.operatorType) {
                EqualityOp.EQ -> {
                    holder.registerProblem(
                        o,
                        "assert!(a == b) can be assert_eq!(a, b)",
                        SpecializedAssertQuickFix("assert_eq")
                    )
                }
                EqualityOp.EXCLEQ -> {
                    holder.registerProblem(
                        o,
                        "assert!(a != b) can be assert_ne!(a, b)",
                        SpecializedAssertQuickFix("assert_ne")
                    )
                }
                else -> return
            }
        }
    }


    private class SpecializedAssertQuickFix(private val assertName: String) : LocalQuickFix {
        override fun getName() = "Convert to $assertName!"

        override fun getFamilyName(): String = name

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val macro = descriptor.psiElement as RsMacroCall
            val assertArg = macro.assertMacroArgument!!
            val argPair = comparedAssertArgs(assertArg) ?: return

            macro.replace(buildAssert(project, "$assertName!()", argPair))
        }

        private fun comparedAssertArgs(arg: RsAssertMacroArgument): Pair<RsExpr, RsExpr>? {
            val expr = arg.expr as? RsBinaryExpr ?: return null
            val right = expr.right ?: return null
            return Pair(expr.left, right)
        }

        private fun addArguments(args: List<PsiElement>, anchor: PsiElement, into: PsiElement) {
            var currentAnchor = anchor
            args.forEach {
                currentAnchor = into.addAfter(it, currentAnchor)
            }
        }

        private fun buildAssert(project: Project, str: String, argPair: Pair<PsiElement, PsiElement>): PsiElement {
            val factory = RsPsiFactory(project)
            val newAssert = factory.createExpression(str) as RsCallExpr
            val args: MutableList<PsiElement> = mutableListOf(argPair.first)
            args.add(factory.createComma())
            args.add(argPair.second)
            addArguments(args, newAssert.valueArgumentList.lparen, newAssert.valueArgumentList)
            return newAssert
        }

    }

}
