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
import org.rust.ide.utils.template.buildAndRunTemplate
import org.rust.lang.core.psi.RsElementTypes.COMMA
import org.rust.lang.core.psi.RsElementTypes.LT
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsTypeArgumentList
import org.rust.lang.core.psi.RsTypeReference
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.core.psi.ext.getNextNonCommentSibling
import org.rust.lang.core.psi.ext.requiredGenericParameters
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
        val inserted = insertTypeArgumentsIfNeeded(element) ?: return
        editor?.buildAndRunTemplate(element, inserted.map { it.createSmartPointer() })
    }
}

/**
 * Inserts type arguments if they are needed and returns a list of inserted type arguments.
 */
fun insertTypeArgumentsIfNeeded(pathOrMethodCall: RsElement): List<RsTypeReference>? {
    val (typeArguments, declaration) = getTypeArgumentsAndDeclaration(pathOrMethodCall) ?: return null

    val requiredParameters = declaration.requiredGenericParameters
    if (requiredParameters.isEmpty()) return null

    val argumentCount = typeArguments?.typeReferenceList?.size ?: 0
    if (argumentCount >= requiredParameters.size) return null

    val factory = RsPsiFactory(pathOrMethodCall.project)
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
        if (pathOrMethodCall !is RsPath) return null
        pathOrMethodCall.addAfter(newArgumentList, pathOrMethodCall.identifier) as RsTypeArgumentList
    }
    return replaced.typeReferenceList.drop(argumentCount)
}

private val PsiElement.isComma: Boolean
    get() = elementType == COMMA
