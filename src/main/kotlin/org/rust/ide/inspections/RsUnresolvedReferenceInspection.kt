package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import org.rust.lang.core.psi.RsEnumItem
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.types.ty.TyPrimitive

class RsUnresolvedReferenceInspection : RsLocalInspectionTool() {
    override fun getDisplayName() = "Unresolved reference"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitPath(o: RsPath) {
                val isPrimitiveType = o is RsPath && TyPrimitive.fromPath(o) != null

                if (isPrimitiveType || o.reference.resolve() != null) return

                val parent = o.path ?: return
                val parentRes = parent.reference.resolve()
                if (parentRes is RsMod || parentRes is RsEnumItem) {
                    holder.registerProblem(o.navigationElement, "Unresolved reference")
                }
            }
        }
}

