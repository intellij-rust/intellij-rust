/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import org.rust.RsBundle
import org.rust.ide.presentation.render
import org.rust.ide.presentation.renderInsertionSafe
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsTypeReference
import org.rust.lang.core.types.ty.Ty

@Suppress("UnstableApiUsage")
class ConvertTypeReferenceFix(
    reference: RsTypeReference,
    @NlsSafe private val identifier: String,
    @FileModifier.SafeFieldForPreview private val ty: Ty
) : RsQuickFixBase<RsTypeReference>(reference) {
    override fun getFamilyName(): String = RsBundle.message("intention.family.name.convert.type")
    override fun getText(): String = RsBundle.message("intention.name.change.type.to2", identifier, ty.render())

    override fun invoke(project: Project, editor: Editor?, element: RsTypeReference) {
        val factory = RsPsiFactory(project)
        val type = factory.tryCreateType(ty.renderInsertionSafe()) ?: return

        element.replace(type)
    }
}
