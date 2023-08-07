/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.ide.presentation.render
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.types.ty.Ty

/**
 * For the given `expr` converts it to the type `ty` with `ty::from(expr)`
 */
class ConvertToTyUsingFromTraitFix(
    expr: RsExpr,
    @SafeFieldForPreview
    private val ty: Ty,
) : ConvertToTyUsingTraitFix(expr, ty, "From") {
    override fun invoke(project: Project, editor: Editor?, element: RsExpr) {
        val newElement = RsPsiFactory(project).createAssocFunctionCall(
            ty.render(includeTypeArguments = false),
            "from",
            listOf(element)
        )
        element.replace(newElement)
    }
}
