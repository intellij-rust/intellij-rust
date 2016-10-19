package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import org.rust.lang.core.psi.*
import org.rust.lang.core.types.util.isPrimitive
import org.rust.lang.core.types.visitors.impl.RustTypificationEngine

class RustUnresolvedReferenceInspection : RustLocalInspectionTool() {
    override fun getDisplayName() = "Unresolved reference"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RustElementVisitor() {
            override fun visitPath(o: RustPathElement) {
                val p = o.parent
                val isPrimitiveType = o is RustPathElement &&
                    p is RustPathTypeElement &&
                    RustTypificationEngine.typifyType(p).isPrimitive

                if (isPrimitiveType || o.reference.resolve() != null) return

                val parent = o.path ?: return
                val parentRes = parent.reference.resolve()
                if (parentRes is RustMod || parentRes is RustEnumItemElement) {
                    holder.registerProblem(o.navigationElement, "Unresolved reference")
                }
            }
    }
}

