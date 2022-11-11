/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.ide.presentation.renderInsertionSafe
import org.rust.ide.utils.template.buildAndRunTemplate
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.types.ty.Ty
import org.rust.openapiext.createSmartPointer

/**
 * Adds type ascription after the given element.
 */
class AddTypeFix(anchor: PsiElement, ty: Ty) : LocalQuickFixAndIntentionActionOnPsiElement(anchor) {
    private val typeText: String = ty.renderInsertionSafe()

    override fun getFamilyName(): String = "Add type"
    override fun getText(): String = "Add type $typeText"

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement
    ) {
        val factory = RsPsiFactory(project)
        val parent = startElement.parent

        val colon = factory.createColon()
        val anchor = parent.addAfter(colon, startElement)

        val type = factory.createType(typeText)
        val insertedType = parent.addAfter(type, anchor)

        editor?.buildAndRunTemplate(parent, listOf(insertedType.createSmartPointer()))
    }
}
