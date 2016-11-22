package org.rust.ide.inspections.duplicates

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import org.rust.lang.core.psi.RustFieldDeclElement
import org.rust.lang.core.psi.RustFieldsOwner
import org.rust.lang.core.psi.namedFields

class RustDuplicateStructFieldInspection : RustDuplicateInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        createInspection(RustFieldsOwner::getFields) {
            holder.registerProblem(it, "Duplicate field <code>#ref</code>", ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
        }
}

//TODO: Why do we need this extra layer of indirection? Why can't we rely on the property's getter via reference?
private fun RustFieldsOwner.getFields(): List<RustFieldDeclElement> = namedFields
