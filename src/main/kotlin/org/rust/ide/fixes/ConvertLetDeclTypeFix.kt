/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.RsBundle
import org.rust.ide.presentation.renderInsertionSafe
import org.rust.lang.core.psi.RsLetDecl
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.types.ty.Ty

/**
 * Change the declared type of a local variable.
 */
class ConvertLetDeclTypeFix(
    decl: RsLetDecl,
    @IntentionName private val fixText: String,
    @SafeFieldForPreview
    private val ty: Ty
) : RsQuickFixBase<RsLetDecl>(decl) {
    override fun getFamilyName(): String = RsBundle.message("intention.family.name.convert.type.local.variable")
    override fun getText(): String = fixText

    override fun invoke(project: Project, editor: Editor?, element: RsLetDecl) {
        val factory = RsPsiFactory(project)
        val type = factory.tryCreateType(ty.renderInsertionSafe()) ?: return

        element.typeReference?.replace(type)
    }
}
