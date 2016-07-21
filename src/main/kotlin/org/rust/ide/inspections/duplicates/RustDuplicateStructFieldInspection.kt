package org.rust.ide.inspections.duplicates

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.util.SmartList
import org.rust.ide.inspections.RustLocalInspectionTool
import org.rust.lang.core.psi.RustElementVisitor
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustStructItemElement
import org.rust.lang.core.psi.util.fields
import java.util.*

class RustDuplicateStructFieldInspection : RustLocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : RustElementVisitor() {
        override fun visitStructItem(o: RustStructItemElement) {
            for (dupe in o.fields.findDuplicates()) {
                holder.registerProblem(dupe, "Duplicate field <code>#ref</code>", ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
            }
        }
    }
}

private fun Collection<RustNamedElement>.findDuplicates(): Collection<RustNamedElement> {
    val names = HashSet<String>()
    val result = SmartList<RustNamedElement>()
    for (item in this) {
        val name = item.name ?: continue
        if (name in names) {
            result += item
        }

        names += name
    }

    return result
}
