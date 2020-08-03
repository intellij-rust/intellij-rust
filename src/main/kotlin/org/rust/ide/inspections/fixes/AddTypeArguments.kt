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
import org.rust.ide.inspections.getTypeArgumentsAndDeclaration
import org.rust.lang.core.parser.RustParserUtil
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.COMMA
import org.rust.lang.core.psi.RsElementTypes.LT
import org.rust.lang.core.psi.ext.*
import org.rust.openapiext.buildAndRunTemplate
import org.rust.openapiext.createSmartPointer

class AddTypeArguments(element: RsElement) : LocalQuickFixAndIntentionActionOnPsiElement(element) {
    override fun getText(): String = "Add missing type arguments"
    override fun getFamilyName() = text

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement
    ) {
        val element = startElement as? RsElement ?: return
        val (typeArguments, declaration) = getTypeArgumentsAndDeclaration(element) ?: return

        val requiredParameters = declaration.requiredGenericParameters
        if (requiredParameters.isEmpty()) return

        val argumentCount = typeArguments?.typeReferenceList?.size ?: 0
        if (argumentCount >= requiredParameters.size) return

        val factory = RsPsiFactory(project)
        val missingTypes = requiredParameters.drop(argumentCount).map { it.name ?: "_" }

        val replaced = if (typeArguments != null) {
            var anchor = with(typeArguments) {
                typeReferenceList.lastOrNull() ?: lifetimeList.lastOrNull() ?: lt
            }
            val nextSibling = anchor.getNextNonCommentSibling()
            val addCommaAfter = nextSibling?.isComma == true
            if (addCommaAfter && nextSibling != null) {
                anchor = nextSibling
            }

            for (type in missingTypes) {
                if (anchor.elementType != LT && !anchor.isComma) {
                    anchor = typeArguments.addAfter(factory.createComma(), anchor)
                }
                anchor = typeArguments.addAfter(factory.createType(type), anchor)
            }

            if (addCommaAfter) {
                typeArguments.addAfter(factory.createComma(), anchor)
            }

            typeArguments
        } else {
            val newArgumentList = factory.createTypeArgumentList(missingTypes)

            // this can only happen for type references (base types/trait refs)
            val path = getPath(element) ?: return
            path.addAfter(newArgumentList, path.identifier) as RsTypeArgumentList
        }

        editor?.buildAndRunTemplate(element, replaced.typeReferenceList.drop(argumentCount).map { it.createSmartPointer() })
    }
}

private val RsGenericDeclaration.requiredGenericParameters: List<RsTypeParameter>
    get() = typeParameters.filter { it.typeReference == null }

private fun getPath(element: PsiElement): RsPath? = when (element) {
    is RsBaseType -> element.path
    is RsTraitRef -> element.path
    else -> null
}

private val PsiElement.isComma: Boolean
    get() = elementType == COMMA
