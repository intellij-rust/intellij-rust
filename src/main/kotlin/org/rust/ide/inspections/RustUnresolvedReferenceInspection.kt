package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.rust.lang.core.psi.isPrimitive
import org.rust.lang.core.psi.*

class RustUnresolvedReferenceInspection : RustLocalInspectionTool() {
    override fun getDisplayName(): String = "Unresolved reference"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : RustElementVisitor() {
            override fun visitPath(o: RustPathElement) {
                if (o.isPrimitive) return
                val resolved = o.reference.resolve()
                if (resolved != null) return
                val parent = o.path
                val parentRes = parent?.reference?.resolve()
                if (parent == null || parentRes is RustMod || parentRes is RustEnumItemElement) {
                    holder.registerProblem(o.navigationElement, "Unresolved reference")
                }
            }
        }
    }
}

