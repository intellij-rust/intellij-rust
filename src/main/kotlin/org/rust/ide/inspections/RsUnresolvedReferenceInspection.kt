package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import org.rust.lang.core.psi.*
import org.rust.lang.core.types.isPrimitive
import org.rust.lang.core.types.type

class RsUnresolvedReferenceInspection : RsLocalInspectionTool() {
    override fun getDisplayName() = "Unresolved reference"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitPath(o: RsPath) {
                val p = o.parent
                val isPrimitiveType = o is RsPath &&
                    p is RsBaseType &&
                    p.type.isPrimitive

                if (isPrimitiveType || o.reference.resolve() != null) return

                if (o.path != null) {
                    holder.registerProblem(o.navigationElement, "Unresolved reference")
                }
            }

            override fun visitMethodCallExpr(o: RsMethodCallExpr) {
                if (o.reference.resolve() == null &&
                    !(o.firstChild is RsPathExpr && (o.firstChild as RsPathExpr).path.reference.resolve() is RsPatBinding) &&
                    !(o.firstChild is RsMethodCallExpr && o.firstChild.reference!!.resolve() == null)) {
                    holder.registerProblem(o.identifier.navigationElement, "Unresolved reference")
                }
            }
        }
}

