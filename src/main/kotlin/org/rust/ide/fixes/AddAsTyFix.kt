/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.RsBundle
import org.rust.ide.presentation.renderInsertionSafe
import org.rust.ide.presentation.shortPresentableText
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.types.ty.Ty

/**
 * For the given `expr` adds cast to the given type `ty`
 */
class AddAsTyFix(
    expr: RsExpr,
    @SafeFieldForPreview
    private val ty: Ty,
) : RsQuickFixBase<RsExpr>(expr) {
    override fun getFamilyName(): String = RsBundle.message("intention.family.name.add.safe.cast")
    override fun getText(): String = RsBundle.message("intention.name.add.safe.cast.to", ty.shortPresentableText)

    override fun invoke(project: Project, editor: Editor?, element: RsExpr) {
        element.replace(RsPsiFactory(project).createCastExpr(element, ty.renderInsertionSafe(element)))
    }
}
