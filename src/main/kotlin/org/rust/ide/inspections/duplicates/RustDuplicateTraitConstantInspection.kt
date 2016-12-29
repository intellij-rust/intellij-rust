package org.rust.ide.inspections.duplicates

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import org.rust.lang.core.psi.RustTraitItemElement

class RustDuplicateTraitConstantInspection : RustDuplicateInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        createInspection(RustTraitItemElement::getConstantList) {
            holder.registerProblem(it.identifier, "Duplicate trait constant <code>#ref</code>", ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
        }
}
