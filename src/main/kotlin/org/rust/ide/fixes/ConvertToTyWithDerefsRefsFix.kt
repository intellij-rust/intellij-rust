/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.types.ty.Mutability
import org.rust.lang.core.types.ty.Ty
import org.rust.stdext.mapToMutableList

/**
 * The data class represents a sequence of dereferences and references. The dereferences are defined by number of
 * dereferences and references are defied by the sequence of mutabilities.
 */
data class DerefRefPath(val derefs: Int, val refs: List<Mutability>)

/**
 * The fix applies `path.derefs` dereferences to the expression and then references of the mutability given by
 * `path.refs`. Note that correctness of the generated code is not verified.
 */
class ConvertToTyWithDerefsRefsFix(
    expr: RsExpr,
    ty: Ty,
    @SafeFieldForPreview
    private val path: DerefRefPath
) : ConvertToTyFix(expr, ty, formatRefs(path)) {
    override fun invoke(project: Project, editor: Editor?, element: RsExpr) {
        val psiFactory = RsPsiFactory(project)
        element.replace(psiFactory.createRefExpr(psiFactory.createDerefExpr(element, path.derefs), path.refs))
    }
}

private fun formatRefs(path: DerefRefPath): String {
    val refs = path.refs.mapToMutableList { if (it.isMut) "&mut " else "&" }
    refs.add("*".repeat(path.derefs))
    return refs.joinToString("").trimEnd()
}
