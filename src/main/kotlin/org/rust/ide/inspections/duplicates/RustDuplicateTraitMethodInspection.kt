package org.rust.ide.inspections.duplicates

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import org.rust.lang.core.psi.RustTraitBodyElement

class RustDuplicateTraitMethodInspection : RustDuplicateInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        createInspection(RustTraitBodyElement::getTraitMethodMemberList) {
            holder.registerProblem(it.identifier, "Duplicate trait method <code>#ref</code>", ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
        }
}
