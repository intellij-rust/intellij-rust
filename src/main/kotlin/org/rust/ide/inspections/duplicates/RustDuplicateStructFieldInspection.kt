package org.rust.ide.inspections.duplicates

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import org.rust.ide.inspections.RustLocalInspectionTool
import org.rust.ide.inspections.duplicates.findDuplicates
import org.rust.lang.core.psi.RustElementVisitor
import org.rust.lang.core.psi.RustStructItemElement
import org.rust.lang.core.psi.util.fields

class RustDuplicateStructFieldInspection : RustLocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : RustElementVisitor() {
        override fun visitStructItem(o: RustStructItemElement) {
            for (dupe in o.fields.findDuplicates()) {
                holder.registerProblem(dupe, "Duplicate field <code>#ref</code>", ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
            }
        }
    }
}

