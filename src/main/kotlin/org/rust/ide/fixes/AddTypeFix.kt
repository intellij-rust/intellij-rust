/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.ide.presentation.renderInsertionSafe
import org.rust.ide.utils.template.buildAndRunTemplate
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.types.ty.Ty

/**
 * Adds type ascription after the given element.
 */
class AddTypeFix(anchor: PsiElement, ty: Ty) : RsQuickFixBase<PsiElement>(anchor) {
    private val typeText: String = ty.renderInsertionSafe()

    override fun getFamilyName(): String = RsBundle.message("intention.family.name.add.type")
    override fun getText(): String = RsBundle.message("intention.name.add.type", typeText)

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val factory = RsPsiFactory(project)
        val parent = element.parent

        val colon = factory.createColon()
        val anchor = parent.addAfter(colon, element)

        val type = factory.createType(typeText)
        val insertedType = parent.addAfter(type, anchor)

        editor?.buildAndRunTemplate(parent, listOf(insertedType))
    }
}
