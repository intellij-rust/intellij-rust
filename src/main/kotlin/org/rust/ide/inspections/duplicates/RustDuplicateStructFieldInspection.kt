package org.rust.ide.inspections.duplicates

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.rust.ide.inspections.RustLocalInspectionTool
import org.rust.lang.core.psi.RustElementVisitor
import org.rust.lang.core.psi.RustFieldsOwner
import org.rust.lang.core.psi.fields

class RustDuplicateStructFieldInspection : RustLocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : RustElementVisitor() {
        override fun visitElement(element: PsiElement) {
            if (element is RustFieldsOwner) {
                for (dupe in element.fields.findDuplicates()) {
                    holder.registerProblem(dupe, "Duplicate field <code>#ref</code>", ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
                }
            }
        }
    }
}

