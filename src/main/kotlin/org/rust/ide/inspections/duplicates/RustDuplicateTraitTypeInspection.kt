package org.rust.ide.inspections.duplicates

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import org.rust.lang.core.psi.RustTraitItemElement

class RustDuplicateTraitTypeInspection : RustDuplicateInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        createInspection(RustTraitItemElement::getTraitTypeMemberList) {
            holder.registerProblem(it.identifier, "Duplicate associated type <code>#ref</code>", ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
        }
}
