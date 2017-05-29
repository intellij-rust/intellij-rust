package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.rust.ide.inspections.fixes.RemoveMutableFix
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.descendantsOfType
import org.rust.lang.core.psi.ext.isMut
import org.rust.lang.core.psi.ext.parentOfType
import org.rust.lang.core.psi.ext.selfParameter

class RsVariableMutableInspection : RsLocalInspectionTool() {
    override fun getDisplayName() = "No mutable required"
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitPatBinding(o: RsPatBinding) {
                if (!o.isMut) return
                val block = o.parentOfType<RsBlock>() ?: o.parentOfType<RsFunction>() ?: return
                if (ReferencesSearch.search(o, LocalSearchScope(block))
                    .asSequence()
                    .any { checkOccurrenceNeedMutable(it.element.parent) }) return
                if (block.descendantsOfType<RsMacroExpr>().any { checkExprPosition(o, it) }) return
                holder.registerProblem(
                    o,
                    "Variable `${o.identifier.text}` does not need to be mutable",
                    *arrayOf(RemoveMutableFix(o))
                )
            }
        }

    fun checkExprPosition(o: RsPatBinding, expr: RsMacroExpr) = o.textOffset < expr.textOffset

    fun checkOccurrenceNeedMutable(occurrence: PsiElement): Boolean {
        val parent = occurrence.parent
        when (parent) {
            is RsUnaryExpr -> return parent.isMutable || parent.mul != null
            is RsBinaryExpr -> return parent.left == occurrence
            is RsMethodCallExpr -> {
                val ref = parent.reference.resolve() as? RsFunction ?: return true
                val self = ref.selfParameter ?: return true
                return self.isMut
            }
            is RsTupleExpr,
            is RsFieldExpr -> {
                val expr = parent.parent as? RsUnaryExpr ?: return true
                return expr.isMutable
            }
            is RsValueArgumentList -> return false
        }
        return true
    }

    private val RsUnaryExpr.isMutable: Boolean get() = mut != null
}
