/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.RsBundle
import org.rust.ide.utils.template.buildAndRunTemplate
import org.rust.lang.core.psi.RsPathType
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsTraitRef
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.startOffset

class AddAssocTypeBindingsFix(
    element: RsElement,
    @SafeFieldForPreview
    private val missingTypes: List<String>
) : RsQuickFixBase<RsElement>(element) {
    override fun getText(): String = RsBundle.message("intention.name.add.missing.associated.types")
    override fun getFamilyName() = text

    override fun invoke(project: Project, editor: Editor?, element: RsElement) {
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

        editor?.buildAndRunTemplate(element, addedArguments.mapNotNull { it.typeReference })
    }
}
