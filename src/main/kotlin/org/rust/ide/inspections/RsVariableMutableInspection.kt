/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.psi.PsiElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.rust.ide.inspections.fixes.RemoveMutableFix
import org.rust.lang.core.macros.MacroExpansionMode
import org.rust.lang.core.macros.macroExpansionManager
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

class RsVariableMutableInspection : RsLintInspection() {
    override fun getLint(element: PsiElement): RsLint = RsLint.UnusedMutable

    override fun getDisplayName() = "Variable does not need to be mutable"
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitPatBinding(o: RsPatBinding) {
                if (!o.mutability.isMut) return
                val block = o.ancestorStrict<RsBlock>() ?: o.ancestorStrict<RsFunction>() ?: return
                if (ReferencesSearch.search(o, LocalSearchScope(block))
                        .asSequence()
                        .any { checkOccurrenceNeedsMutable(it.element.parent) }) return
                if (o.project.macroExpansionManager.macroExpansionMode !is MacroExpansionMode.New &&
                    block.descendantsOfType<RsMacroCall>().any { checkExprPosition(o, it) }) return
                holder.registerProblem(
                    o,
                    "Variable `${o.identifier.text}` does not need to be mutable",
                    RemoveMutableFix()
                )
            }
        }

    fun checkExprPosition(o: RsPatBinding, expr: RsMacroCall) = o.textOffset < expr.textOffset

    fun checkOccurrenceNeedsMutable(occurrence: PsiElement): Boolean {
        return when (val parent = occurrence.parent) {
            is RsUnaryExpr -> parent.isMutable
            is RsBinaryExpr -> parent.isAssignBinaryExpr && parent.left == occurrence
            is RsMethodCall -> {
                val ref = parent.reference.resolve() as? RsFunction ?: return true
                val self = ref.selfParameter ?: return true
                self.mutability.isMut
            }
            is RsTupleExpr -> {
                val expr = parent.parent as? RsUnaryExpr ?: return true
                expr.isMutable
            }
            is RsValueArgumentList -> false
            is RsDotExpr -> checkMethodNeedsMutable(parent, occurrence)
            else -> true
        }
    }

    private fun checkMethodNeedsMutable(parent: RsDotExpr, expression: PsiElement): Boolean {
        if (parent.expr != expression) return true
        val method = parent.methodCall?.reference?.resolve() as? RsFunction ?: return true
        return method.hasSelfParameters && method.selfParameter?.mutability?.isMut ?: true
    }

    private val RsUnaryExpr.isMutable: Boolean get() = mut != null
}
