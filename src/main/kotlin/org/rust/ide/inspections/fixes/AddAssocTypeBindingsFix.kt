/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.fixes

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.ide.utils.template.buildAndRunTemplate
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.startOffset
import org.rust.openapiext.createSmartPointer

class AddAssocTypeBindingsFix(
    element: PsiElement,
    private val missingTypes: List<String>
) : LocalQuickFixAndIntentionActionOnPsiElement(element) {
    override fun getText(): String = "Add missing associated types"
    override fun getFamilyName() = text

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement
    ) {
        val element = startElement as? RsElement ?: return
        val path = when (element) {
            is RsTraitRef -> element.path
            is RsPathType -> element.path
            else -> return
        }

        val factory = RsPsiFactory(project)
        val defaultType = "()"

        val arguments = path.typeArgumentList ?: path.addEmptyTypeArguments(factory)
        val lastArgument = with(arguments) {
            (assocTypeBindingList + typeReferenceList + lifetimeList).maxByOrNull { it.startOffset } ?: lt
        }
        val missingTypes = missingTypes.map { factory.createAssocTypeBinding(it, defaultType) }
        val addedArguments = arguments.addElements(missingTypes, lastArgument, factory)

        editor?.buildAndRunTemplate(element, addedArguments.mapNotNull { it.typeReference?.createSmartPointer() })
    }
}
