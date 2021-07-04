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
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.core.psi.ext.getNextNonCommentSibling
import org.rust.openapiext.buildAndRunTemplate
import org.rust.openapiext.createSmartPointer

class AddAssocTypeBindingsFix(element: PsiElement) : LocalQuickFixAndIntentionActionOnPsiElement(element) {
    override fun getText(): String = "Add missing associated types"
    override fun getFamilyName() = text

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement
    ) {
        val element = startElement as? RsTraitRef ?: return
        val arguments = element.path.typeArgumentList
        val trait = element.path.reference?.resolve() as? RsTraitItem ?: return

        val requiredAssocTypes = trait.associatedTypesTransitively
            .filter { it.typeReference == null }
            .associateBy { it.identifier.text }
        val existingAssocTypes = arguments?.assocTypeBindingList
            .orEmpty()
            .associateBy { it.identifier.text }
        val missingAssocTypes = requiredAssocTypes.filter { it.key !in existingAssocTypes }
        if (missingAssocTypes.isEmpty()) return

        val factory = RsPsiFactory(project)
        val newAssocTypes = missingAssocTypes.toList().sortedBy { it.first }
        val defaultType = "()"

        val inserted = if (arguments != null) {
            var anchor = with(arguments) {
                assocTypeBindingList.lastOrNull() ?: typeReferenceList.lastOrNull() ?: lifetimeList.lastOrNull() ?: lt
            }
            val nextSibling = anchor.getNextNonCommentSibling()
            val addCommaAfter = nextSibling?.isComma == true
            if (addCommaAfter && nextSibling != null) {
                anchor = nextSibling
            }

            for (type in newAssocTypes) {
                if (anchor.elementType != RsElementTypes.LT && !anchor.isComma) {
                    anchor = arguments.addAfter(factory.createComma(), anchor)
                }
                anchor = arguments.addAfter(factory.createAssocTypeBinding(type.first, defaultType), anchor)
            }

            if (addCommaAfter) {
                arguments.addAfter(factory.createComma(), anchor)
            }

            arguments
        } else {
            val newArgumentList = factory.createTypeArgumentList(newAssocTypes.map { "${it.first}=$defaultType" })
            element.path.addAfter(newArgumentList, element.path.identifier) as RsTypeArgumentList
        }

        editor?.buildAndRunTemplate(element, inserted.assocTypeBindingList
            .takeLast(missingAssocTypes.size)
            .mapNotNull { it.typeReference?.createSmartPointer() }
        )
    }
}

private val PsiElement.isComma: Boolean
    get() = elementType == RsElementTypes.COMMA
