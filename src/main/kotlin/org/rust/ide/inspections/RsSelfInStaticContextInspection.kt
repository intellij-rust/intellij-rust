package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import org.rust.ide.annotator.fixes.AddSelfFix
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.mixin.asRustPath
import org.rust.lang.core.psi.impl.mixin.isAssocFn
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.symbols.RustPath

class RsSelfInStaticContextInspection : RsLocalInspectionTool() {
    override fun getDisplayName(): String = "self in static context"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitPath(path: RsPath) = inspectPath(holder, path)
        }

    private fun inspectPath(holder: ProblemsHolder, path: RsPath) {
        path.self ?: return

        val function = path.parentOfType<RsFunction>() ?: return
        if (path.asRustPath !is RustPath.ModRelative && function.isAssocFn) {
            holder.registerProblem(
                path,
                "The self keyword was used in a static method [E0424]",
                ProblemHighlightType.GENERIC_ERROR,
                AddSelfFix(function)
            )
        }
    }
}
