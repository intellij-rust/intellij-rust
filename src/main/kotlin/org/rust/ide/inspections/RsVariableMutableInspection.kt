package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import org.rust.ide.annotator.fixes.RemoveMutableFix
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.isMut
import org.rust.lang.core.psi.ext.parentOfType
import java.util.*

private fun findBlock(expr: PsiElement) = PsiTreeUtil.getNonStrictParentOfType(expr, RsBlock::class.java)

class RsVariableMutableInspection : RsLocalInspectionTool() {
    override fun getDisplayName() = "No mutable required"
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitPatBinding(o: RsPatBinding) {
                if (!o.isMut) return
                val occurrences = findOccurrences(o.identifier)
                var isMutableNeeded = false
                if (!occurrences.isEmpty()) {
                    for (occurrence in occurrences) {
                        val parent = occurrence.parentOfType<RsBinaryExpr>() ?: continue
                        if (parent.left == occurrence) {
                            isMutableNeeded = true
                            break
                        }
                    }
                }
                if (!isMutableNeeded) {
                    holder.registerProblem(o, "No mutable required for ${o.identifier.text}", RemoveMutableFix(o))
                }
            }
        }
}

/**
 * Finds occurrences in the sub scope of expr, so that all will be replaced if replace all is selected.
 */
fun findOccurrences(expr: PsiElement): List<RsExpr> {
    val visitor = object : PsiRecursiveElementVisitor() {
        val foundOccurrences = ArrayList<RsExpr>()

        override fun visitElement(element: PsiElement) {
            if (element is RsExpr && expr.text == element.text) {
                foundOccurrences.add(element)
            } else {
                super.visitElement(element)
            }
        }
    }

    val block = findBlock(expr) ?: return emptyList()
    block.acceptChildren(visitor)
    return visitor.foundOccurrences
}
