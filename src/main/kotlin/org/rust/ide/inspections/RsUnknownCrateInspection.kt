package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import org.rust.lang.core.psi.RsExternCrateItem
import org.rust.lang.core.psi.RsVisitor

class RsUnknownCrateInspection : RsLocalInspectionTool() {
    override fun getDisplayName() = "Unknown crate"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitExternCrateItem(o: RsExternCrateItem) {
                if (o.reference.resolve() == null) {
                    holder.registerProblem(o.navigationElement, "Unknown crate '" + o.identifier.text + "'")
                }
            }
        }
}
