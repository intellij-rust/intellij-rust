package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.mixin.isAssocFn
import org.rust.lang.core.psi.util.parentOfType

class RsSelfInStaticContextInspection : RsLocalInspectionTool() {
    override fun getDisplayName(): String = "self in static context"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitPath(path: RsPath) = inspectPath(holder, path)
        }

    private fun inspectPath(holder: ProblemsHolder, path: RsPath) {
        if (path.self == null) {
            return
        }

        val function = path.parentOfType<RsFunction>() ?: return

        if (function.isAssocFn) {
            holder.registerProblem(
                path,
                "The self keyword was used in a static method [E424]",
                ProblemHighlightType.GENERIC_ERROR
            )
        }
    }
}
